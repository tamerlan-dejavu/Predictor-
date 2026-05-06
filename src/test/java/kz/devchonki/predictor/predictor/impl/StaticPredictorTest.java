package kz.devchonki.predictor.predictor.impl;

import kz.devchonki.predictor.model.PredictorStats;
import kz.devchonki.predictor.predictor.impl.StaticPredictor.StaticStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StaticPredictor")
class StaticPredictorTest {

    private static final long PC = 0x400000L;

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Drives the predictor through one predict→update cycle per outcome. */
    private PredictorStats runTrace(StaticPredictor predictor, long pc, boolean... outcomes) {
        predictor.reset();
        for (boolean taken : outcomes) {
            predictor.predict(pc);
            predictor.update(pc, taken);
        }
        return predictor.getStats();
    }

    /** Builds a boolean array where every element equals {@code taken}. */
    private boolean[] uniform(boolean taken, int count) {
        boolean[] arr = new boolean[count];
        if (taken) java.util.Arrays.fill(arr, true);
        return arr;
    }

    /** Builds an alternating taken/not-taken array starting with {@code taken}. */
    private boolean[] alternating(int count) {
        boolean[] arr = new boolean[count];
        for (int i = 0; i < count; i++) arr[i] = (i % 2 == 0);
        return arr;
    }

    /** Builds loop-N pattern: (n-1) taken + 1 not-taken, repeated {@code reps} times. */
    private boolean[] loopPattern(int loopLen, int reps) {
        List<Boolean> list = new ArrayList<>(loopLen * reps);
        for (int r = 0; r < reps; r++) {
            for (int i = 0; i < loopLen - 1; i++) list.add(true);
            list.add(false);
        }
        boolean[] arr = new boolean[list.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = list.get(i);
        return arr;
    }

    // ── ALWAYS_TAKEN ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("test_alwaysTaken_perfectOnAllTaken: mispredictionRate == 0%")
    void test_alwaysTaken_perfectOnAllTaken() {
        StaticPredictor p = new StaticPredictor(StaticStrategy.ALWAYS_TAKEN);
        PredictorStats stats = runTrace(p, PC, uniform(true, 1000));

        assertEquals(1000, stats.totalPredictions());
        assertEquals(0,    stats.mispredictions());
        assertEquals(0.0,  stats.mispredictionRate(), 1e-9);
        assertEquals(0.0,  stats.mpki(), 1e-9);
    }

    @Test
    @DisplayName("test_alwaysTaken_allMissOnAllNotTaken: mispredictionRate == 100%")
    void test_alwaysTaken_allMissOnAllNotTaken() {
        StaticPredictor p = new StaticPredictor(StaticStrategy.ALWAYS_TAKEN);
        PredictorStats stats = runTrace(p, PC, uniform(false, 1000));

        assertEquals(1000,  stats.totalPredictions());
        assertEquals(1000,  stats.mispredictions());
        assertEquals(100.0, stats.mispredictionRate(), 1e-9);
        assertEquals(1000.0, stats.mpki(), 1e-9);
    }

    @Test
    @DisplayName("test_alwaysTaken_alternating: mispredictionRate == 50%")
    void test_alwaysTaken_alternating() {
        StaticPredictor p = new StaticPredictor(StaticStrategy.ALWAYS_TAKEN);
        PredictorStats stats = runTrace(p, PC, alternating(1000));

        assertEquals(1000, stats.totalPredictions());
        assertEquals(500,  stats.mispredictions());
        assertEquals(50.0, stats.mispredictionRate(), 1e-9);
        assertEquals(500.0, stats.mpki(), 1e-9);
    }

    // ── ALWAYS_NOT_TAKEN ──────────────────────────────────────────────────────

    @Test
    @DisplayName("test_alwaysNotTaken_perfectOnAllNotTaken: mispredictionRate == 0%")
    void test_alwaysNotTaken_perfectOnAllNotTaken() {
        StaticPredictor p = new StaticPredictor(StaticStrategy.ALWAYS_NOT_TAKEN);
        PredictorStats stats = runTrace(p, PC, uniform(false, 1000));

        assertEquals(1000, stats.totalPredictions());
        assertEquals(0,    stats.mispredictions());
        assertEquals(0.0,  stats.mispredictionRate(), 1e-9);
    }

    @Test
    @DisplayName("ALWAYS_NOT_TAKEN predicts 100% miss on all-taken trace")
    void test_alwaysNotTaken_allMissOnAllTaken() {
        StaticPredictor p = new StaticPredictor(StaticStrategy.ALWAYS_NOT_TAKEN);
        PredictorStats stats = runTrace(p, PC, uniform(true, 1000));

        assertEquals(1000,  stats.mispredictions());
        assertEquals(100.0, stats.mispredictionRate(), 1e-9);
    }

    // ── BTFN ──────────────────────────────────────────────────────────────────

    /**
     * PC with bit 15 set → BTFN predicts taken (backward/loop branch).
     * On a loop-10 trace (9 taken + 1 not-taken × 100) it should only miss
     * the loop-exit (not-taken) branches → miss rate ≤ 20%.
     */
    @Test
    @DisplayName("test_btfn_backwardLoop: rate < 20% on loop_10 trace with backward PC")
    void test_btfn_backwardLoop() {
        // bit 15 set: 0x408000 & 0x8000 = 0x8000 != 0 → BTFN predicts taken
        final long backwardPc = 0x408000L;
        StaticPredictor p = new StaticPredictor(StaticStrategy.BTFN);
        PredictorStats stats = runTrace(p, backwardPc, loopPattern(10, 100));

        assertEquals(1000, stats.totalPredictions());
        // only the 100 loop-exit (not-taken) branches are mispredicted
        assertEquals(100, stats.mispredictions());
        assertEquals(10.0, stats.mispredictionRate(), 1e-9);
        assertTrue(stats.mispredictionRate() < 20.0,
                "Expected < 20% miss rate for backward-branch BTFN, got "
                        + stats.mispredictionRate() + "%");
    }

    @Test
    @DisplayName("BTFN predicts not-taken for forward branch (bit 15 clear)")
    void test_btfn_forwardBranch_predictsNotTaken() {
        // 0x400000 & 0x8000 = 0 → BTFN predicts not-taken
        final long forwardPc = 0x400000L;
        StaticPredictor p = new StaticPredictor(StaticStrategy.BTFN);
        p.predict(forwardPc);
        p.update(forwardPc, false); // not-taken → correct prediction

        assertEquals(0, p.getStats().mispredictions());
    }

    @Test
    @DisplayName("BTFN predicts taken for backward branch (bit 15 set)")
    void test_btfn_backwardBranch_predictsTaken() {
        final long backwardPc = 0xFFFF8000L;
        assertTrue((backwardPc & 0x8000L) != 0, "sanity: bit 15 must be set");
        StaticPredictor p = new StaticPredictor(StaticStrategy.BTFN);
        p.predict(backwardPc);
        p.update(backwardPc, true); // taken → correct prediction

        assertEquals(0, p.getStats().mispredictions());
    }

    // ── getName ───────────────────────────────────────────────────────────────

    @ParameterizedTest
    @EnumSource(StaticStrategy.class)
    @DisplayName("getName returns 'Static-<STRATEGY>'")
    void getName_returnsCorrectFormat(StaticStrategy strategy) {
        StaticPredictor p = new StaticPredictor(strategy);
        assertEquals("Static-" + strategy.name(), p.getName());
    }

    // ── reset ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("reset clears all counters")
    void reset_clearsCounters() {
        StaticPredictor p = new StaticPredictor(StaticStrategy.ALWAYS_TAKEN);
        runTrace(p, PC, uniform(false, 500)); // accumulate some misses

        p.reset();
        PredictorStats stats = p.getStats();
        assertEquals(0,   stats.totalPredictions());
        assertEquals(0,   stats.mispredictions());
        assertEquals(0.0, stats.mispredictionRate());
    }

    @Test
    @DisplayName("getStats returns empty when no predictions made")
    void getStats_emptyWhenNoPredictions() {
        StaticPredictor p = new StaticPredictor(StaticStrategy.ALWAYS_TAKEN);
        PredictorStats stats = p.getStats();
        assertEquals(PredictorStats.empty(), stats);
    }
}
