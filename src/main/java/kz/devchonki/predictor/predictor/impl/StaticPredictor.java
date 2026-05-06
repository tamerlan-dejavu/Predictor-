package kz.devchonki.predictor.predictor.impl;

import kz.devchonki.predictor.model.PredictorStats;
import kz.devchonki.predictor.predictor.BranchPredictor;
import org.springframework.stereotype.Component;

/**
 * Stateless static predictor — no learning, no internal history tables.
 *
 * <p>The prediction rule is fixed at construction time via {@link StaticStrategy}:
 * <ul>
 *   <li>{@link StaticStrategy#ALWAYS_TAKEN} — predict every branch taken</li>
 *   <li>{@link StaticStrategy#ALWAYS_NOT_TAKEN} — predict every branch not-taken</li>
 *   <li>{@link StaticStrategy#BTFN} — Backward-Taken / Forward-Not-Taken:
 *       uses bit 15 of the PC as a direction heuristic; loops branch backward
 *       (high-address → low-address), which is cheaply approximated by
 *       {@code (pc & 0x8000L) != 0}.</li>
 * </ul>
 *
 * <p>Counters ({@code totalPredictions}, {@code mispredictions}) are updated in the
 * {@link #predict}/{@link #update} pair — {@link #predict} counts the attempt and
 * {@link #update} scores the outcome.
 */
@Component
public class StaticPredictor implements BranchPredictor {

    // ── strategy enum ────────────────────────────────────────────────────────

    public enum StaticStrategy {
        /** Always predict the branch will be taken. */
        ALWAYS_TAKEN,
        /** Always predict the branch will not be taken. */
        ALWAYS_NOT_TAKEN,
        /**
         * Backward-Taken / Forward-Not-Taken heuristic.
         * Bit 15 of the PC is used as a cheap direction signal:
         * if set, the branch is likely backward (loop) → predict taken;
         * otherwise predict not-taken.
         */
        BTFN
    }

    // ── state ────────────────────────────────────────────────────────────────

    private final StaticStrategy strategy;
    private long totalPredictions = 0;
    private long mispredictions   = 0;

    // ── constructors ─────────────────────────────────────────────────────────

    /** Default Spring bean: always-taken. */
    public StaticPredictor() {
        this(StaticStrategy.ALWAYS_TAKEN);
    }

    public StaticPredictor(StaticStrategy strategy) {
        this.strategy = strategy;
    }

    // ── BranchPredictor ──────────────────────────────────────────────────────

    /**
     * Returns the prediction for the given PC and increments {@code totalPredictions}.
     * Misprediction counting happens in {@link #update}.
     */
    @Override
    public boolean predict(long pc) {
        totalPredictions++;
        return predictWithoutCount(pc);
    }

    /**
     * Compares the prediction for {@code pc} against the true {@code taken} outcome
     * and increments {@code mispredictions} if they differ.
     */
    @Override
    public void update(long pc, boolean taken) {
        if (predictWithoutCount(pc) != taken) {
            mispredictions++;
        }
    }

    @Override
    public void reset() {
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
        return "Static-" + strategy.name();
    }

    // ── internals ────────────────────────────────────────────────────────────

    /**
     * Pure prediction function — does NOT touch any counter.
     * Used by both {@link #predict} (which adds the counter separately) and
     * {@link #update} (which needs the same answer without side effects).
     */
    private boolean predictWithoutCount(long pc) {
        return switch (strategy) {
            case ALWAYS_TAKEN     -> true;
            case ALWAYS_NOT_TAKEN -> false;
            case BTFN             -> (pc & 0x8000L) != 0;
        };
    }
}
