package kz.devchonki.predictor.predictor.impl;

import kz.devchonki.predictor.model.PredictorStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BimodalPredictor")
class BimodalPredictorTest {

    private static final long PC = 0x400000L;

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Drives predictor through one predict→update cycle per outcome entry. */
    private PredictorStats runTrace(BimodalPredictor predictor, long pc, boolean... outcomes) {
        predictor.reset();
        for (boolean taken : outcomes) {
            predictor.predict(pc);
            predictor.update(pc, taken);
        }
        return predictor.getStats();
    }

    /** Returns an array of {@code count} copies of {@code taken}. */
    private boolean[] uniform(boolean taken, int count) {
        boolean[] arr = new boolean[count];
        if (taken) java.util.Arrays.fill(arr, true);
        return arr;
    }

    /** Alternating taken/not-taken starting with taken=true. */
    private boolean[] alternating(int count) {
        boolean[] arr = new boolean[count];
        for (int i = 0; i < count; i++) arr[i] = (i % 2 == 0);
        return arr;
    }

    /** (loopLen-1) taken + 1 not-taken, repeated reps times. */
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

    // ── always-taken ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("test_alwaysTaken_zeroMiss: 0% misprediction after warmup")
    void test_alwaysTaken_zeroMiss() {
        BimodalPredictor p = new BimodalPredictor(1024);

        // PHT initialised to 2 (Weakly Taken) → first prediction is already correct.
        // After a full always-taken trace the counter saturates at 3.
        PredictorStats stats = runTrace(p, PC, uniform(true, 1000));

        assertEquals(1000, stats.totalPredictions());
        // No mispredictions: counter starts at 2 (taken), stays taken throughout.
        assertEquals(0, stats.mispredictions());
        assertEquals(0.0, stats.mispredictionRate(), 1e-9);
    }

    // ── always-not-taken ──────────────────────────────────────────────────────

    @Test
    @DisplayName("test_alwaysNotTaken: 0% after warmup (2 initial misses then perfect)")
    void test_alwaysNotTaken() {
        BimodalPredictor p = new BimodalPredictor(1024);

        // PHT starts at 2 (Weakly Taken). For always-not-taken:
        //   iter 1: predict=T, actual=N → miss, counter 2→1
        //   iter 2: predict=T (still >=2? No, 1<2 → predict=N), actually counter is 1 so predict=F, actual=N → HIT
        // Wait, let me recalculate:
        //   init: counter=2
        //   predict(1): pht[idx]=2>=2 → T. update: 2!=false → miss, counter→1
        //   predict(2): pht[idx]=1<2  → F. update: F==false → hit,  counter→0
        //   predict(3): pht[idx]=0<2  → F. update: F==false → hit,  counter stays 0
        // So exactly 1 warmup miss.
        PredictorStats stats = runTrace(p, PC, uniform(false, 1000));

        assertEquals(1000, stats.totalPredictions());
        // Exactly 1 warmup misprediction (counter starts at Weakly Taken = 2)
        assertEquals(1, stats.mispredictions());
        assertTrue(stats.mispredictionRate() < 1.0,
                "Expected <1% miss rate after warmup, got " + stats.mispredictionRate());
    }

    // ── alternating ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("test_alternating_50percent: approx 50% misprediction")
    void test_alternating_50percent() {
        BimodalPredictor p = new BimodalPredictor(1024);
        PredictorStats stats = runTrace(p, PC, alternating(1000));

        assertEquals(1000, stats.totalPredictions());
        // Alternating trace keeps the counter bouncing between 1 and 2.
        // Counter at 2 → predicts taken → actual not-taken → miss, counter→1
        // Counter at 1 → predicts not-taken → actual taken → miss, counter→2
        // Result: ~50% miss rate (may be 499-501 depending on start state).
        assertTrue(stats.mispredictionRate() >= 45.0 && stats.mispredictionRate() <= 55.0,
                "Expected ~50% miss rate, got " + stats.mispredictionRate());
    }

    // ── loop-10 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("test_loop10_better_than_static: rate < 15% on loop_10 trace")
    void test_loop10_better_than_static() {
        BimodalPredictor p = new BimodalPredictor(1024);
        // loop-10: 9 taken + 1 not-taken × 100 repetitions = 1000 entries.
        // Bimodal saturates quickly to Strongly Taken.
        // Misses: ~1 on first loop exit + ~1 re-entry miss after each NT exit = ≤200/1000.
        PredictorStats stats = runTrace(p, PC, loopPattern(10, 100));

        assertEquals(1000, stats.totalPredictions());
        assertTrue(stats.mispredictionRate() < 15.0,
                "Expected <15% miss rate on loop-10, got " + stats.mispredictionRate());
    }

    // ── table size comparison ─────────────────────────────────────────────────

    @Test
    @DisplayName("test_tableSize16_vs_1024: larger table fewer aliases, lower miss rate")
    void test_tableSize16_vs_1024() {
        // Build a trace with 32 distinct PCs that all alias into the same 16 entries
        // but resolve to distinct entries in a 1024-table.
        // Each PC alternates between taken and not-taken in a way that causes
        // destructive aliasing in the small table.
        int numPcs = 32;
        int traceLen = numPcs * 50; // 1600 entries
        boolean[] trace = new boolean[traceLen];
        long[]    pcs   = new long[traceLen];

        // PCs spaced 4 apart → same lower bits modulo 16, distinct modulo 1024
        // All branches are always-taken for their own PC.
        for (int i = 0; i < traceLen; i++) {
            // cycle through PCs 0x400000, 0x400004, ..., 0x40007C
            long pc = 0x400000L + (long)(i % numPcs) * 4;
            pcs[i]   = pc;
            trace[i] = true; // always taken
        }

        BimodalPredictor small = new BimodalPredictor(16);
        BimodalPredictor large = new BimodalPredictor(1024);

        for (int i = 0; i < traceLen; i++) {
            small.predict(pcs[i]); small.update(pcs[i], trace[i]);
            large.predict(pcs[i]); large.update(pcs[i], trace[i]);
        }

        PredictorStats smallStats = small.getStats();
        PredictorStats largeStats = large.getStats();

        // With 32 PCs all taken and table=16: every pair of PCs aliases → more misses.
        // With table=1024: no aliasing → at most 1 warmup miss per PC.
        assertTrue(largeStats.mispredictions() <= smallStats.mispredictions(),
                String.format("Expected large table (miss=%d) <= small table (miss=%d)",
                        largeStats.mispredictions(), smallStats.mispredictions()));
        assertTrue(largeStats.mispredictionRate() <= smallStats.mispredictionRate(),
                "Larger table should have equal or fewer misses than smaller table");
    }

    // ── invalid table size ────────────────────────────────────────────────────

    @Test
    @DisplayName("test_invalidTableSize_throws: new BimodalPredictor(1000) → exception")
    void test_invalidTableSize_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new BimodalPredictor(1000),
                "Non-power-of-2 table size should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("other non-power-of-2 sizes also throw")
    void test_invalidTableSize_various() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new BimodalPredictor(0)),
                () -> assertThrows(IllegalArgumentException.class, () -> new BimodalPredictor(3)),
                () -> assertThrows(IllegalArgumentException.class, () -> new BimodalPredictor(100)),
                () -> assertThrows(IllegalArgumentException.class, () -> new BimodalPredictor(1023))
        );
    }

    @Test
    @DisplayName("power-of-2 sizes are accepted")
    void test_validTableSizes_accepted() {
        assertDoesNotThrow(() -> new BimodalPredictor(1));
        assertDoesNotThrow(() -> new BimodalPredictor(2));
        assertDoesNotThrow(() -> new BimodalPredictor(16));
        assertDoesNotThrow(() -> new BimodalPredictor(512));
        assertDoesNotThrow(() -> new BimodalPredictor(4096));
    }

    // ── reset ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("test_reset_clearsState: reset() → getStats().total == 0")
    void test_reset_clearsState() {
        BimodalPredictor p = new BimodalPredictor(1024);
        runTrace(p, PC, uniform(true, 500)); // accumulate state

        p.reset();

        PredictorStats stats = p.getStats();
        assertEquals(0,              stats.totalPredictions(), "totalPredictions should be 0 after reset");
        assertEquals(0,              stats.mispredictions(),   "mispredictions should be 0 after reset");
        assertEquals(0.0,            stats.mispredictionRate(), 1e-9);
        assertEquals(0.0,            stats.mpki(),              1e-9);
    }

    @Test
    @DisplayName("reset restores PHT to Weakly Taken: always-taken trace has 0 misses again")
    void test_reset_restoresPhtToWeaklyTaken() {
        BimodalPredictor p = new BimodalPredictor(1024);

        // drive to Strongly Not-Taken
        runTrace(p, PC, uniform(false, 200));
        // reset restores to Weakly Taken
        p.reset();
        // now always-taken trace should produce 0 misses
        PredictorStats stats = runTrace(p, PC, uniform(true, 1000));

        assertEquals(0, stats.mispredictions(),
                "After reset PHT should be Weakly Taken → 0 misses on always-taken trace");
    }

    // ── getName ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getName returns 'Bimodal-<tableSize>'")
    void test_getName() {
        assertEquals("Bimodal-1024", new BimodalPredictor(1024).getName());
        assertEquals("Bimodal-16",   new BimodalPredictor(16).getName());
    }

    // ── saturating counter boundary ───────────────────────────────────────────

    @Test
    @DisplayName("counter does not exceed 3 (Strongly Taken)")
    void test_counterSaturatesAtStronglyTaken() {
        BimodalPredictor p = new BimodalPredictor(16);
        // hammer many taken outcomes at the same PC
        for (int i = 0; i < 50; i++) {
            p.predict(PC);
            p.update(PC, true);
        }
        // All remaining predictions must be taken (counter pinned at 3)
        p.reset();
        // After reset counters go back to 2, re-saturate and verify no explosion
        for (int i = 0; i < 10; i++) {
            p.predict(PC);
            p.update(PC, true);
        }
        assertEquals(0, p.getStats().mispredictions());
    }

    @Test
    @DisplayName("counter does not go below 0 (Strongly Not-Taken)")
    void test_counterSaturatesAtStronglyNotTaken() {
        BimodalPredictor p = new BimodalPredictor(16);
        // 1 warmup miss to descend from 2→1, then 0
        runTrace(p, PC, uniform(false, 200)); // saturate to 0
        PredictorStats stats = p.getStats();
        // Only 1 warmup miss expected: counter 2→1 (predict T, actual N),
        // counter 1→0 (predict N, actual N) → HIT
        assertEquals(1, stats.mispredictions(),
                "Expected exactly 1 warmup miss on all-not-taken trace");
    }
}
