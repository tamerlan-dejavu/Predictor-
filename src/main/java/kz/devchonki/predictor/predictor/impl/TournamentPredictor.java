package kz.devchonki.predictor.predictor.impl;

import kz.devchonki.predictor.model.PredictorStats;
import kz.devchonki.predictor.predictor.BranchPredictor;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Tournament (hybrid) branch predictor — loosely modelled after the
 * Alpha 21264 design.
 *
 * <h3>Architecture</h3>
 * <pre>
 *   ┌──────────┐  localPred  ┐
 *   │  Local   │             │
 *   │ (Bimodal)│             ▼
 *   └──────────┘        ┌─────────┐
 *                       │ Chooser │──► final prediction
 *   ┌──────────┐        └─────────┘
 *   │  Global  │  globalPred ▲
 *   │ (GShare) │             │
 *   └──────────┘─────────────┘
 * </pre>
 *
 * <h3>Chooser</h3>
 * <p>A 2-bit saturating counter table indexed by a tournament-local GHR:
 * <ul>
 *   <li>0 – strongly local</li>
 *   <li>1 – weakly   local  (initial state)</li>
 *   <li>2 – weakly   global</li>
 *   <li>3 – strongly global</li>
 * </ul>
 * <p>The chooser is updated only when local and global <em>disagree</em>:
 * whichever was correct gets a credit (saturating increment/decrement).
 *
 * <h3>Predict / update protocol</h3>
 * <p>Both sub-predictors are called in every {@link #predict} and
 * {@link #update} cycle so their internal state (PHT, GHR) stays in sync
 * with the branch stream. The Tournament caches the sub-predictor outputs
 * from {@code predict()} and re-uses them in {@code update()} — no
 * double-counting of sub-predictor calls.
 */
@Component
public class TournamentPredictor implements BranchPredictor {

    // ── default configuration (Alpha 21264-inspired) ──────────────────────────
    private static final int DEFAULT_LOCAL_TABLE_SIZE  = 1024;
    private static final int DEFAULT_GLOBAL_TABLE_SIZE = 4096;
    private static final int DEFAULT_HISTORY_BITS      = 12;
    private static final int DEFAULT_CHOOSER_SIZE      = 4096;

    // ── sub-predictors and chooser ────────────────────────────────────────────
    private final BimodalPredictor local;
    private final GSharePredictor  global;
    private final int[]            chooser;
    private final int              chooserMask;   // chooserSize - 1

    // ── tournament-level GHR (indexes chooser, independent of GShare's GHR) ──
    private int ghr = 0;

    // ── statistics ────────────────────────────────────────────────────────────
    private long total         = 0;
    private long mispredictions = 0;

    // ── decision snapshot from predict() — reused in update() for correct scoring ──
    private boolean lastLocalPred;    // local sub-predictor answer at predict-time
    private boolean lastGlobalPred;   // global sub-predictor answer at predict-time
    private boolean lastUsedGlobal;   // which sub-predictor the chooser selected

    // ── constructors ─────────────────────────────────────────────────────────

    /** Default Spring bean with Alpha 21264–like parameters. */
    public TournamentPredictor() {
        this(DEFAULT_LOCAL_TABLE_SIZE, DEFAULT_GLOBAL_TABLE_SIZE,
                DEFAULT_HISTORY_BITS, DEFAULT_CHOOSER_SIZE);
    }

    /**
     * @param localTableSize  Bimodal PHT entries (power of 2)
     * @param globalTableSize GShare PHT entries (power of 2)
     * @param historyBits     GShare GHR width, [1, 20]
     * @param chooserSize     chooser table entries (power of 2)
     * @throws IllegalArgumentException if any size is not a valid power of 2
     */
    public TournamentPredictor(int localTableSize, int globalTableSize,
                               int historyBits, int chooserSize) {
        requirePowerOfTwo(localTableSize,  "localTableSize");
        requirePowerOfTwo(globalTableSize, "globalTableSize");
        requirePowerOfTwo(chooserSize,     "chooserSize");

        this.local       = new BimodalPredictor(localTableSize);
        this.global      = new GSharePredictor(globalTableSize, historyBits);
        this.chooser     = new int[chooserSize];
        this.chooserMask = chooserSize - 1;

        Arrays.fill(chooser, 1); // weakly local (neutral starting point)
    }

    // ── BranchPredictor ──────────────────────────────────────────────────────

    /**
     * Consults both sub-predictors via side-effect-free {@code peek()}, lets the
     * chooser select the final answer, and snapshots the decision for {@link #update}.
     *
     * <p><b>Bug fix — sub-predictor totalPredictions inflation:</b>
     * Previously called {@code local.predict()} and {@code global.predict()}, which
     * incremented their {@code totalPredictions} on every branch. This distorted
     * sub-predictor statistics (they appeared to have evaluated every branch twice
     * if also used standalone). {@code peek()} returns the same PHT/GHR-derived
     * answer without touching any counter.
     */
    @Override
    public boolean predict(long pc) {
        total++;
        int     chooserIdx = ghr & chooserMask;
        lastUsedGlobal = chooser[chooserIdx] >= 2;

        lastLocalPred  = local.peek(pc);
        lastGlobalPred = global.peek(pc);

        return lastUsedGlobal ? lastGlobalPred : lastLocalPred;
    }

    /**
     * <ol>
     *   <li>Scores the tournament prediction using the decision snapshotted in
     *       {@link #predict} — <em>before</em> any state is mutated.</li>
     *   <li>Forwards the true outcome to both sub-predictors (updates their PHTs/GHR).</li>
     *   <li>Updates the chooser only when sub-predictors disagreed.</li>
     *   <li>Shifts the tournament GHR.</li>
     * </ol>
     *
     * <p><b>Bug fix — chooser-updated scoring:</b>
     * Previously the misprediction was scored AFTER updating the chooser, so if
     * the chooser flipped its binary decision during the update the wrong prediction
     * was compared against {@code taken}.  Now {@code lastUsedGlobal} (cached in
     * {@code predict()}) is used, which reflects exactly what was returned to the
     * caller.
     */
    @Override
    public void update(long pc, boolean taken) {
        int chooserIdx = ghr & chooserMask;

        // ── 1. score with the decision that was actually returned ─────────────
        boolean finalPred = lastUsedGlobal ? lastGlobalPred : lastLocalPred;
        if (finalPred != taken) {
            mispredictions++;
        }

        // ── 2. update sub-predictors (PHT + internal GHR) ────────────────────
        local.update(pc, taken);
        global.update(pc, taken);

        // ── 3. update chooser (only when sub-predictors disagreed) ───────────
        if (lastLocalPred != lastGlobalPred) {
            if (lastGlobalPred == taken) {
                chooser[chooserIdx] = Math.min(3, chooser[chooserIdx] + 1); // toward global
            } else {
                chooser[chooserIdx] = Math.max(0, chooser[chooserIdx] - 1); // toward local
            }
        }

        // ── 4. update tournament GHR ─────────────────────────────────────────
        ghr = ((ghr << 1) | (taken ? 1 : 0)) & chooserMask;
    }

    @Override
    public void reset() {
        local.reset();
        global.reset();
        Arrays.fill(chooser, 1); // weakly local
        ghr            = 0;
        total          = 0;
        mispredictions = 0;
    }

    @Override
    public PredictorStats getStats() {
        if (total == 0) {
            return PredictorStats.empty();
        }
        double rate = (double) mispredictions / total * 100.0;
        double mpki = (double) mispredictions / (total / 1000.0);
        return new PredictorStats(total, mispredictions, rate, mpki);
    }

    @Override
    public String getName() {
        return "Tournament(L=" + local.getName()
                + ",G=" + global.getName() + ")";
    }

    // ── package-visible for testing ──────────────────────────────────────────

    int getChooserValue(int idx) {
        return chooser[idx & chooserMask];
    }

    int getGhr() {
        return ghr;
    }

    // ── internals ────────────────────────────────────────────────────────────

    private static void requirePowerOfTwo(int value, String name) {
        if (Integer.bitCount(value) != 1) {
            throw new IllegalArgumentException(
                    name + " must be a power of 2, got: " + value);
        }
    }
}
