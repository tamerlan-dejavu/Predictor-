package kz.devchonki.predictor.predictor.impl;

import kz.devchonki.predictor.model.PredictorStats;
import kz.devchonki.predictor.predictor.BranchPredictor;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * GShare branch predictor — correlating predictor that XORs the branch PC
 * with a Global History Register (GHR) to index into a 2-bit saturating
 * counter PHT.
 *
 * <h3>Algorithm</h3>
 * <pre>
 *   index = ((pc >> 2) XOR ghr) &amp; (tableSize - 1)
 *   predict: pht[index] &gt;= 2 → taken
 *   update PHT: saturating ±1
 *   update GHR: ghr = ((ghr &lt;&lt; 1) | taken) &amp; historyMask
 * </pre>
 *
 * <h3>Why GShare beats Bimodal</h3>
 * <p>XOR-folding the PC with the global branch-outcome history diversifies
 * the index space and captures correlation between successive branches
 * (e.g. loop exits, if-chains). The longer the history ({@code historyBits}),
 * the more inter-branch correlation is exploited — at the cost of a longer
 * warm-up period and potential aliasing in a fixed-size PHT.
 *
 * @see BimodalPredictor
 */
@Component
public class GSharePredictor implements BranchPredictor {

    private static final int DEFAULT_TABLE_SIZE  = 1024;
    private static final int DEFAULT_HISTORY_BITS = 8;

    private final int   tableSize;
    private final int   historyBits;
    private final int   historyMask;   // (1 << historyBits) - 1
    private final int[] pht;

    private int  ghr = 0;
    private long totalPredictions;
    private long mispredictions;

    // ── constructors ─────────────────────────────────────────────────────────

    /** Default Spring bean: 1024-entry PHT, 8-bit history. */
    public GSharePredictor() {
        this(DEFAULT_TABLE_SIZE, DEFAULT_HISTORY_BITS);
    }

    /**
     * @param tableSize    PHT size; must be a power of 2
     * @param historyBits  GHR width in bits; must be in [1, 20]
     * @throws IllegalArgumentException if constraints are violated
     */
    public GSharePredictor(int tableSize, int historyBits) {
        if (Integer.bitCount(tableSize) != 1) {
            throw new IllegalArgumentException(
                    "tableSize must be a power of 2, got: " + tableSize);
        }
        if (historyBits < 1 || historyBits > 20) {
            throw new IllegalArgumentException(
                    "historyBits must be in [1, 20], got: " + historyBits);
        }
        this.tableSize   = tableSize;
        this.historyBits = historyBits;
        this.historyMask = (1 << historyBits) - 1;
        this.pht         = new int[tableSize];
        Arrays.fill(pht, 2); // Weakly Taken
    }

    // ── BranchPredictor ──────────────────────────────────────────────────────

    @Override
    public boolean predict(long pc) {
        totalPredictions++;
        return pht[getIndex(pc)] >= 2;
    }

    /**
     * Scores the outcome, updates PHT, then shifts the new outcome into GHR.
     *
     * <p>GHR update happens <em>after</em> the index is computed so that
     * {@link #predict} and {@link #update} see the same GHR state for the
     * same branch.
     */
    @Override
    public void update(long pc, boolean taken) {
        int idx = getIndex(pc);

        if ((pht[idx] >= 2) != taken) {
            mispredictions++;
        }

        if (taken) {
            pht[idx] = Math.min(3, pht[idx] + 1);
        } else {
            pht[idx] = Math.max(0, pht[idx] - 1);
        }

        ghr = ((ghr << 1) | (taken ? 1 : 0)) & historyMask;
    }

    @Override
    public void reset() {
        Arrays.fill(pht, 2);
        ghr              = 0;
        totalPredictions = 0;
        mispredictions   = 0;
    }

    @Override
    public PredictorStats getStats() {
        if (totalPredictions == 0) {
            return PredictorStats.empty();
        }
        double rate = (double) mispredictions / totalPredictions * 100.0;
        double mpki = (double) mispredictions / (totalPredictions / 1000.0);
        return new PredictorStats(totalPredictions, mispredictions, rate, mpki);
    }

    @Override
    public String getName() {
        return "GShare-" + tableSize + "-H" + historyBits;
    }

    // ── package-visible for testing ──────────────────────────────────────────

    /** Returns the current value of the Global History Register. */
    int getGhr() {
        return ghr;
    }

    // ── internals ────────────────────────────────────────────────────────────

    private int getIndex(long pc) {
        return (int) (((pc >> 2) ^ ghr) & (tableSize - 1));
    }
}
