package kz.devchonki.predictor.predictor.impl;

import kz.devchonki.predictor.model.PredictorStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GSharePredictor")
class GSharePredictorTest {

    private static final long PC = 0x400000L;

    // ── helpers ───────────────────────────────────────────────────────────────

    private PredictorStats runTrace(GSharePredictor p, long pc, boolean... outcomes) {
        p.reset();
        for (boolean taken : outcomes) {
            p.predict(pc);
            p.update(pc, taken);
        }
        return p.getStats();
    }

    private boolean[] uniform(boolean taken, int count) {
        boolean[] arr = new boolean[count];
        if (taken) java.util.Arrays.fill(arr, true);
        return arr;
    }

    private boolean[] alternating(int count) {
        boolean[] arr = new boolean[count];
        for (int i = 0; i < count; i++) arr[i] = (i % 2 == 0); // T,N,T,N,...
        return arr;
    }

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

    // ── loop-10 vs bimodal ────────────────────────────────────────────────────

    @Test
    @DisplayName("test_loop10_gshare_better_bimodal: GShare(1024,8) miss% <= Bimodal(1024)")
    void test_loop10_gshare_better_bimodal() {
        boolean[] trace = loopPattern(10, 100); // 1000 entries

        GSharePredictor  gshare  = new GSharePredictor(1024, 8);
        BimodalPredictor bimodal = new BimodalPredictor(1024);

        PredictorStats gsStats = runTrace(gshare,  PC, trace);
        PredictorStats bmStats;
        bimodal.reset();
        bmStats = runBimodal(bimodal, PC, trace);

        // GShare should match or beat Bimodal on a loop-10 pattern
        assertTrue(gsStats.mispredictionRate() <= bmStats.mispredictionRate() + 1.0,
                String.format("GShare(%.2f%%) should be <= Bimodal(%.2f%%) + 1%%",
                        gsStats.mispredictionRate(), bmStats.mispredictionRate()));

        // Both should be well below 15%
        assertTrue(gsStats.mispredictionRate() < 15.0,
                "GShare miss rate on loop-10 should be < 15%, got " + gsStats.mispredictionRate());
    }

    private PredictorStats runBimodal(BimodalPredictor p, long pc, boolean... outcomes) {
        p.reset();
        for (boolean taken : outcomes) {
            p.predict(pc);
            p.update(pc, taken);
        }
        return p.getStats();
    }

    // ── GHR update correctness ────────────────────────────────────────────────

    @Test
    @DisplayName("test_ghr_updates_correctly: after T,T,N,T → ghr == 0b1101 & mask")
    void test_ghr_updates_correctly() {
        // historyBits=4, historyMask=0b1111
        GSharePredictor p = new GSharePredictor(1024, 4);

        // Feed outcomes in order: T, T, N, T
        // GHR shifts left and ORs the outcome each time:
        //   after T: ghr = (0<<1)|1 = 0b0001
        //   after T: ghr = (1<<1)|1 = 0b0011
        //   after N: ghr = (3<<1)|0 = 0b0110
        //   after T: ghr = (6<<1)|1 = 0b1101
        boolean[] sequence = {true, true, false, true};
        for (boolean taken : sequence) {
            p.predict(PC);
            p.update(PC, taken);
        }

        assertEquals(0b1101, p.getGhr(),
                "GHR after T,T,N,T should be 0b1101 = 13");
    }

    @Test
    @DisplayName("GHR is masked to historyBits width")
    void test_ghr_masked_to_historyBits() {
        GSharePredictor p = new GSharePredictor(1024, 4); // mask = 0b1111

        // Feed 10 all-taken outcomes — GHR should never exceed 0b1111
        for (int i = 0; i < 10; i++) {
            p.predict(PC);
            p.update(PC, true);
        }

        assertEquals(0b1111, p.getGhr(),
                "GHR should be saturated at historyMask (0b1111) after all-taken");
    }

    @Test
    @DisplayName("GHR after all-not-taken outcomes is 0")
    void test_ghr_allNotTaken_isZero() {
        GSharePredictor p = new GSharePredictor(1024, 8);
        for (int i = 0; i < 20; i++) {
            p.predict(PC);
            p.update(PC, false);
        }
        assertEquals(0, p.getGhr(), "GHR should be 0 after all-not-taken outcomes");
    }

    // ── historyBits comparison ────────────────────────────────────────────────

    @Test
    @DisplayName("test_historyBits4_vs_12: historyBits=12 at least as good on correlated trace")
    void test_historyBits4_vs_12() {
        // Build a correlated trace: a period-8 pattern repeated 200 times
        // Pattern: T,T,T,T,N,T,T,N → 8-branch period (1600 entries total)
        boolean[] period = {true, true, true, true, false, true, true, false};
        boolean[] trace  = new boolean[period.length * 200];
        for (int i = 0; i < trace.length; i++) trace[i] = period[i % period.length];

        GSharePredictor g4  = new GSharePredictor(1024, 4);
        GSharePredictor g12 = new GSharePredictor(4096, 12);

        PredictorStats s4  = runTrace(g4,  PC, trace);
        PredictorStats s12 = runTrace(g12, PC, trace);

        // historyBits=12 with a larger table should learn the period-8 pattern better
        // Allow a small tolerance: g12 should have fewer or equal misses
        assertTrue(s12.mispredictions() <= s4.mispredictions() + 10,
                String.format("historyBits=12 (misses=%d) should be <= historyBits=4 (misses=%d) + 10",
                        s12.mispredictions(), s4.mispredictions()));
    }

    // ── reset clears GHR ─────────────────────────────────────────────────────

    @Test
    @DisplayName("test_reset_clears_ghr: after reset() ghr == 0")
    void test_reset_clears_ghr() {
        GSharePredictor p = new GSharePredictor(1024, 8);

        // Drive GHR to a non-zero state
        for (int i = 0; i < 10; i++) {
            p.predict(PC);
            p.update(PC, true);
        }
        assertNotEquals(0, p.getGhr(), "GHR should be non-zero before reset");

        p.reset();

        assertEquals(0, p.getGhr(), "GHR should be 0 after reset()");
        assertEquals(PredictorStats.empty(), p.getStats());
    }

    @Test
    @DisplayName("reset restores PHT to Weakly Taken: no misses on always-taken after reset")
    void test_reset_restoresPht() {
        GSharePredictor p = new GSharePredictor(1024, 8);
        runTrace(p, PC, uniform(false, 200)); // drive PHT toward Strongly Not-Taken

        p.reset(); // PHT back to Weakly Taken, GHR = 0

        // Always-taken trace from a clean state: PHT starts at 2 → first prediction correct
        PredictorStats stats = runTrace(p, PC, uniform(true, 1000));
        assertEquals(0, stats.mispredictions(),
                "After reset PHT is Weakly Taken → 0 misses on all-taken trace");
    }

    // ── alternating: GShare > Bimodal ────────────────────────────────────────

    @Test
    @DisplayName("test_gshare_alternating: GShare(historyBits=1) learns T,N pattern perfectly")
    void test_gshare_alternating() {
        // With historyBits=1, GHR holds just the last outcome.
        // The PHT index alternates: when GHR=0 → idx_A; when GHR=1 → idx_B.
        // idx_A always sees "taken" (every other branch), idx_B always "not-taken".
        // After 2 warmup entries the predictor is perfect.
        GSharePredictor  gshare  = new GSharePredictor(1024, 1);
        BimodalPredictor bimodal = new BimodalPredictor(1024);

        boolean[] trace = alternating(1000); // T,N,T,N,...

        PredictorStats gsStats = runTrace(gshare,  PC, trace);
        PredictorStats bmStats = runBimodal(bimodal, PC, trace);

        // GShare should dramatically outperform Bimodal on alternating pattern
        assertTrue(gsStats.mispredictionRate() < bmStats.mispredictionRate(),
                String.format("GShare(%.2f%%) should beat Bimodal(%.2f%%) on alternating trace",
                        gsStats.mispredictionRate(), bmStats.mispredictionRate()));

        // Bimodal should be ~50%; GShare should be far below
        assertTrue(bmStats.mispredictionRate() >= 40.0,
                "Bimodal should be ~50% on alternating trace");
        assertTrue(gsStats.mispredictionRate() < 5.0,
                "GShare(historyBits=1) should be < 5% on alternating trace after warmup");
    }

    // ── validation ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("non-power-of-2 tableSize throws IllegalArgumentException")
    void test_invalidTableSize_throws() {
        assertThrows(IllegalArgumentException.class, () -> new GSharePredictor(1000, 8));
    }

    @Test
    @DisplayName("historyBits=0 throws IllegalArgumentException")
    void test_historyBitsZero_throws() {
        assertThrows(IllegalArgumentException.class, () -> new GSharePredictor(1024, 0));
    }

    @Test
    @DisplayName("historyBits=21 throws IllegalArgumentException")
    void test_historyBitsTooLarge_throws() {
        assertThrows(IllegalArgumentException.class, () -> new GSharePredictor(1024, 21));
    }

    @Test
    @DisplayName("valid parameters are accepted")
    void test_validParams_accepted() {
        assertDoesNotThrow(() -> new GSharePredictor(16,   1));
        assertDoesNotThrow(() -> new GSharePredictor(1024, 8));
        assertDoesNotThrow(() -> new GSharePredictor(4096, 20));
    }

    // ── getName ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getName returns 'GShare-<tableSize>-H<historyBits>'")
    void test_getName() {
        assertEquals("GShare-1024-H8",  new GSharePredictor(1024, 8).getName());
        assertEquals("GShare-512-H4",   new GSharePredictor(512,  4).getName());
    }

    // ── getStats ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getStats returns empty when no predictions have been made")
    void test_getStats_empty() {
        assertEquals(PredictorStats.empty(), new GSharePredictor(1024, 8).getStats());
    }
}
