package kz.devchonki.predictor.predictor;

import kz.devchonki.predictor.model.PredictorStats;

/**
 * Shared bookkeeping for all concrete predictors.
 * Subclasses implement {@link #doPrediction(long)} and
 * {@link #doUpdate(long, boolean, boolean)} and call
 * {@link #recordOutcome(boolean)} from their {@code update} override.
 */
public abstract class AbstractPredictor implements BranchPredictor {

    protected long totalPredictions;
    protected long mispredictions;

    @Override
    public final boolean predict(long pc) {
        totalPredictions++;
        return doPrediction(pc);
    }

    @Override
    public final void update(long pc, boolean taken) {
        boolean predicted = getLastPrediction(pc);
        if (predicted != taken) {
            mispredictions++;
        }
        doUpdate(pc, taken, predicted);
    }

    @Override
    public void reset() {
        totalPredictions = 0;
        mispredictions = 0;
    }

    @Override
    public PredictorStats getStats() {
        double rate = totalPredictions == 0 ? 0.0
                : (double) mispredictions / totalPredictions;
        double mpki = totalPredictions == 0 ? 0.0
                : (double) mispredictions / totalPredictions * 1000.0;
        return new PredictorStats(totalPredictions, mispredictions, rate, mpki);
    }

    // ── template methods ────────────────────────────────────────────────────

    /** Core prediction logic — must NOT modify counters. */
    protected abstract boolean doPrediction(long pc);

    /**
     * Update internal state after learning the true outcome.
     *
     * @param pc        program counter
     * @param taken     actual outcome
     * @param predicted what the predictor said in the last {@code predict()} call
     */
    protected abstract void doUpdate(long pc, boolean taken, boolean predicted);

    /**
     * Returns the prediction that was made during the most recent
     * {@link #predict(long)} call for {@code pc}.
     * Default implementation re-runs the prediction (safe for stateless predictors).
     * Stateful predictors should override to cache or track the last prediction.
     */
    protected boolean getLastPrediction(long pc) {
        totalPredictions--; // undo the count bump from predict()
        return doPrediction(pc);
    }
}
