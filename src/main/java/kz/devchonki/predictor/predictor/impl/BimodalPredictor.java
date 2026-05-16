package kz.devchonki.predictor.predictor.impl;

import kz.devchonki.predictor.model.PredictorStats;
import kz.devchonki.predictor.predictor.BranchPredictor;

import java.util.Arrays;

/**
 * Bimodal (2-bit saturating counter) branch predictor.
 *
 * <h3>Pattern History Table (PHT)</h3>
 * <p>A direct-mapped array of {@code tableSize} 2-bit saturating counters,
 * each representing one of four states:
 * <pre>
 *   0 – Strongly Not-Taken
 *   1 – Weakly   Not-Taken
 *   2 – Weakly   Taken      ← initial state (Weakly Taken)
 *   3 – Strongly Taken
 * </pre>
 *
 * <h3>Prediction rule</h3>
 * <p>Predict <em>taken</em> when {@code pht[index] >= 2}.
 *
 * <h3>Indexing</h3>
 * <p>{@code index = (pc >> 2) & (tableSize - 1)} — the two lowest PC bits are
 * skipped because instructions are at least 4-byte aligned; the next
 * {@code log2(tableSize)} bits select the PHT entry.
 *
 * <h3>Update rule</h3>
 * <p>Saturating increment on taken, saturating decrement on not-taken.
 * Misprediction is scored in {@link #update} by comparing the counter state
 * at the time of update against the true outcome.
 */
public class BimodalPredictor implements BranchPredictor {

    private static final int DEFAULT_TABLE_SIZE = 1024;

    private final int   tableSize;
    private final int[] pht;

    private long totalPredictions;
    private long mispredictions;

    // ── constructors ─────────────────────────────────────────────────────────

    /** Default constructor: 1024-entry PHT. */
    public BimodalPredictor() {
        this(DEFAULT_TABLE_SIZE);
    }

    /**
     * @param tableSize number of PHT entries; must be a power of 2
     * @throws IllegalArgumentException if {@code tableSize} is not a power of 2
     */
    public BimodalPredictor(int tableSize) {
        if (Integer.bitCount(tableSize) != 1) {
            throw new IllegalArgumentException(
                    "tableSize must be a power of 2, got: " + tableSize);
        }
        this.tableSize = tableSize;
        this.pht = new int[tableSize];
        Arrays.fill(pht, 2); // Weakly Taken
    }

    // ── BranchPredictor ──────────────────────────────────────────────────────

    @Override
    public boolean predict(long pc) {
        totalPredictions++;
        return pht[getIndex(pc)] >= 2;
    }

    /**
     * Scores the outcome and updates the PHT counter.
     *
     * <p>Misprediction is counted here, not in {@link #predict}, to keep
     * {@code predict} side-effect-free with respect to accuracy tracking.
     */
    @Override
    public void update(long pc, boolean taken) {
        int idx = getIndex(pc);
        boolean prediction = pht[idx] >= 2;
        if (prediction != taken) {
            mispredictions++;
        }
        if (taken) {
            pht[idx] = Math.min(3, pht[idx] + 1);
        } else {
            pht[idx] = Math.max(0, pht[idx] - 1);
        }
    }

    @Override
    public void reset() {
        totalPredictions = 0;
        mispredictions   = 0;
        Arrays.fill(pht, 2); // back to Weakly Taken
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
        return "Bimodal-" + tableSize;
    }

    // ── package-visible helpers ───────────────────────────────────────────────

    /**
     * Returns the prediction for {@code pc} WITHOUT modifying any counter or PHT.
     * Used by {@link TournamentPredictor} so that calling predict-on-behalf-of-tournament
     * does not inflate this predictor's own {@code totalPredictions}.
     */
    boolean peek(long pc) {
        return pht[getIndex(pc)] >= 2;
    }

    // ── internals ────────────────────────────────────────────────────────────

    /**
     * Maps a branch PC to a PHT index.
     * Skips the two lowest bits (instruction alignment) and masks to table size.
     */
    private int getIndex(long pc) {
        return (int) ((pc >> 2) & (tableSize - 1));
    }
}
