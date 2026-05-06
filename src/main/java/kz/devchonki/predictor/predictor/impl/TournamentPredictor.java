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

    // ── last sub-predictor outputs (cached from predict(), used by update()) ──
    private boolean lastLocalPred;
    private boolean lastGlobalPred;

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
     * Calls both sub-predictors, lets the chooser select the final answer,
     * and caches both sub-predictions for use in {@link #update}.
     */
    @Override
    public boolean predict(long pc) {
        total++;
        int     chooserIdx = ghr & chooserMask;
        boolean useGlobal  = chooser[chooserIdx] >= 2;

        lastLocalPred  = local.predict(pc);
        lastGlobalPred = global.predict(pc);

        return useGlobal ? lastGlobalPred : lastLocalPred;
    }

    /**
     * <ol>
     *   <li>Forwards the true outcome to both sub-predictors (updates their PHTs/GHR).</li>
     *   <li>Updates the chooser only when sub-predictors disagreed.</li>
     *   <li>Scores the final (chooser-selected) prediction against the true outcome.</li>
     *   <li>Shifts the tournament GHR.</li>
     * </ol>
     */
    @Override
    public void update(long pc, boolean taken) {
        int chooserIdx = ghr & chooserMask;

        // ── 1. update sub-predictors ─────────────────────────────────────────
        local.update(pc, taken);
        global.update(pc, taken);

        // ── 2. update chooser (only on disagreement) ─────────────────────────
        if (lastLocalPred != lastGlobalPred) {
            if (lastGlobalPred == taken) {
                chooser[chooserIdx] = Math.min(3, chooser[chooserIdx] + 1); // toward global
            } else {
                chooser[chooserIdx] = Math.max(0, chooser[chooserIdx] - 1); // toward local
            }
        }

        // ── 3. score the tournament prediction ───────────────────────────────
        boolean finalPred = (chooser[chooserIdx] >= 2) ? lastGlobalPred : lastLocalPred;
        if (finalPred != taken) {
            mispredictions++;
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
