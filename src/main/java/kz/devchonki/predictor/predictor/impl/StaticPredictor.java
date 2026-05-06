package kz.devchonki.predictor.predictor.impl;

import kz.devchonki.predictor.predictor.AbstractPredictor;
import org.springframework.stereotype.Component;

/**
 * Static predictor — always predicts "taken" (or always "not-taken" depending
 * on {@code alwaysTaken} flag).  No dynamic state, no learning.
 *
 * <p>TODO: wire {@code alwaysTaken} via constructor / Spring config.
 */
@Component
public class StaticPredictor extends AbstractPredictor {

    private final boolean alwaysTaken;
    private boolean lastPrediction;

    public StaticPredictor() {
        this(true);
    }

    public StaticPredictor(boolean alwaysTaken) {
        this.alwaysTaken = alwaysTaken;
    }

    @Override
    public String getName() {
        return alwaysTaken ? "Static Always-Taken" : "Static Always-Not-Taken";
    }

    @Override
    protected boolean doPrediction(long pc) {
        lastPrediction = alwaysTaken;
        return lastPrediction;
    }

    @Override
    protected boolean getLastPrediction(long pc) {
        return lastPrediction;
    }

    @Override
    protected void doUpdate(long pc, boolean taken, boolean predicted) {
        // stateless — nothing to update
    }
}
