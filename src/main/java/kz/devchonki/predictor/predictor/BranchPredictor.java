package kz.devchonki.predictor.predictor;

import kz.devchonki.predictor.model.PredictorStats;

/**
 * Contract for every branch-prediction algorithm in this lab.
 */
public interface BranchPredictor {

    /**
     * Returns the predicted outcome for a branch at the given program counter.
     *
     * @param pc program counter of the branch instruction
     * @return {@code true} if the predictor thinks the branch will be taken
     */
    boolean predict(long pc);

    /**
     * Provides ground-truth feedback so the predictor can update its state.
     *
     * @param pc    program counter of the branch instruction
     * @param taken actual outcome (true = branch was taken)
     */
    void update(long pc, boolean taken);

    /** Resets internal state (counters, tables) to initial values. */
    void reset();

    /** Returns an immutable snapshot of the current accuracy metrics. */
    PredictorStats getStats();

    /** Human-readable name of this predictor (e.g. "Static Always-Taken"). */
    String getName();
}
