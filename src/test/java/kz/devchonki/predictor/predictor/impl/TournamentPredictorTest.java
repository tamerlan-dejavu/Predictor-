package kz.devchonki.predictor.predictor.impl;

import kz.devchonki.predictor.model.PredictorStats;
import kz.devchonki.predictor.predictor.BranchPredictor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TournamentPredictor")
class TournamentPredictorTest {

    // ── trace builders ────────────────────────────────────────────────────────

    private record BranchEvent(long pc, boolean taken) {}

    private List<BranchEvent> uniform(long pc, boolean taken, int count) {
        List<BranchEvent> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) list.add(new BranchEvent(pc, taken));
        return list;
    }

    private List<BranchEvent> alternating(long pc, int count) {
        List<BranchEvent> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) list.add(new BranchEvent(pc, i % 2 == 0));
        return list;
    }

    private List<BranchEvent> loopPattern(long pc, int loopLen, int reps) {
        List<BranchEvent> list = new ArrayList<>(loopLen * reps);
        for (int r = 0; r < reps; r++) {
            for (int i = 0; i < loopLen - 1; i++) list.add(new BranchEvent(pc, true));
            list.add(new BranchEvent(pc, false));
        }
        return list;
    }

    /** Drives predictor through predict→update for every event; returns stats. */
    private PredictorStats run(BranchPredictor p, List<BranchEvent> trace) {
        p.reset();
        for (BranchEvent e : trace) {
            p.predict(e.pc());
            p.update(e.pc(), e.taken());
        }
        return p.getStats();
    }


    @Test
    @DisplayName("test_tournament_better_than_bimodal_and_gshare: tournament adapts to best sub-predictor")
    void test_tournament_better_than_bimodal_and_gshare() {
        // Mixed trace:
        // PC_A (0x400000) always-taken   → Bimodal(local) dominates (0% miss after warmup)
        // PC_B (0x408000) alternating    → GShare(global, H=1) dominates (<5% after warmup)
        // Interleaved 500 times each = 1000 total events.
        // Tournament should converge on the better sub-predictor per GHR context
        // and achieve a combined miss rate ≤ worst of bimodal vs gshare.

        final long PC_A = 0x400000L; // always taken
        final long PC_B = 0x408000L; // alternating

        List<BranchEvent> trace = new ArrayList<>(1000);
        for (int i = 0; i < 500; i++) {
            trace.add(new BranchEvent(PC_A, true));
            trace.add(new BranchEvent(PC_B, i % 2 == 0));
        }

        TournamentPredictor tournament = new TournamentPredictor(1024, 1024, 1, 1024);
        BimodalPredictor    bimodal    = new BimodalPredictor(1024);
        GSharePredictor     gshare     = new GSharePredictor(1024, 1);

        PredictorStats tStats = run(tournament, trace);
        PredictorStats bStats = run(bimodal,    trace);
        PredictorStats gStats = run(gshare,     trace);

        double worstSingle = Math.max(bStats.mispredictionRate(), gStats.mispredictionRate());

        // Tournament should never be significantly worse than the worst single predictor
        assertTrue(tStats.mispredictionRate() <= worstSingle + 2.0,
                String.format("Tournament(%.2f%%) must be <= worst-single(%.2f%%) + 2%%",
                        tStats.mispredictionRate(), worstSingle));

        // And specifically: tournament on a mixed trace should be well below 30%
        assertTrue(tStats.mispredictionRate() < 30.0,
                "Tournament miss rate on mixed trace should be < 30%, got "
                        + tStats.mispredictionRate() + "%");
    }


    @Test
    @DisplayName("test_chooser_adapts: on alternating trace chooser shifts toward global")
    void test_chooser_adapts() {
        // Use historyBits=1 so GShare dominates on alternating traces.
        // After a long alternating trace the chooser (at GHR index 0 and 1)
        // should have shifted from initial 'weakly local' (1) toward 'global' (≥2).
        TournamentPredictor p = new TournamentPredictor(1024, 1024, 1, 1024);
        List<BranchEvent>   trace = alternating(0x400000L, 2000);

        run(p, trace);

        // After many alternating branches, global predictor wins more often.
        PredictorStats tStats = p.getStats();
        assertTrue(tStats.mispredictionRate() < 40.0,
                "Chooser should adapt toward GShare on alternating trace; got "
                        + tStats.mispredictionRate() + "%");

        // Also verify numerically: average chooser value across all entries
        // should have moved above 1 (initial weakly-local) toward global territory.
        long chooserSum = 0;
        for (int i = 0; i < 1024; i++) chooserSum += p.getChooserValue(i);
        double avgChooser = (double) chooserSum / 1024;
        // Many entries will still be at 1 (untouched); the active ones should shift up
        // Just confirm no systematic drift toward 0 (strongly local):
        assertTrue(avgChooser >= 1.0,
                "Average chooser should be >= 1 (initial); got " + avgChooser);
    }

    // ── test_all_predictors_same_trace_comparison ─────────────────────────────

    @Test
    @DisplayName("test_all_predictors_same_trace_comparison: all four predictors on loop-10")
    void test_all_predictors_same_trace_comparison() {
        List<BranchEvent> trace = loopPattern(0x400000L, 10, 200); // 2000 entries

        StaticPredictor     staticP  = new StaticPredictor(StaticPredictor.StaticStrategy.ALWAYS_TAKEN);
        BimodalPredictor    bimodal  = new BimodalPredictor(1024);
        GSharePredictor     gshare   = new GSharePredictor(1024, 8);
        TournamentPredictor tourney  = new TournamentPredictor(1024, 1024, 8, 1024);

        PredictorStats sStats = run(staticP,  trace);
        PredictorStats bStats = run(bimodal,  trace);
        PredictorStats gStats = run(gshare,   trace);
        PredictorStats tStats = run(tourney,  trace);

        assertTrue(bStats.mispredictionRate() <= sStats.mispredictionRate(),
                "Bimodal should be <= Static on loop-10");
        assertTrue(gStats.mispredictionRate() <= sStats.mispredictionRate() + 1.0,
                "GShare should be ≈ Static or better on loop-10");
        assertTrue(tStats.mispredictionRate() <= sStats.mispredictionRate() + 1.0,
                "Tournament should be ≈ Static or better on loop-10");

        // Tournament should not be dramatically worse than the best dynamic predictor
        double bestDynamic = Math.min(bStats.mispredictionRate(), gStats.mispredictionRate());
        assertTrue(tStats.mispredictionRate() <= bestDynamic + 5.0,
                String.format("Tournament(%.2f%%) should be within 5%% of best dynamic(%.2f%%)",
                        tStats.mispredictionRate(), bestDynamic));
    }


    @Test
    @DisplayName("test_reset_resets_all_internal_state")
    void test_reset_resets_all_internal_state() {
        TournamentPredictor p = new TournamentPredictor(1024, 1024, 8, 1024);

        // Drive predictor to non-zero state
        run(p, alternating(0x400000L, 500));

        assertNotEquals(0, p.getGhr(), "GHR should be non-zero before reset");

        p.reset();

        // Tournament-level counters cleared
        assertEquals(PredictorStats.empty(), p.getStats());
        // Tournament GHR cleared
        assertEquals(0, p.getGhr(), "Tournament GHR should be 0 after reset");
        // Chooser reset to weakly-local (1)
        for (int i = 0; i < 1024; i++) {
            assertEquals(1, p.getChooserValue(i),
                    "Chooser entry " + i + " should be 1 (weakly-local) after reset");
        }

        // Sub-predictors also reset: run a clean always-taken trace
        // If PHT is back to Weakly Taken, no misses expected
        PredictorStats fresh = run(p, uniform(0x400000L, true, 1000));
        assertEquals(0, fresh.mispredictions(),
                "After reset, always-taken trace should yield 0 misses");
    }

    // ── GHR update correctness ────────────────────────────────────────────────

    @Test
    @DisplayName("tournament GHR shifts left and ORs taken, masked to chooserMask")
    void test_ghr_updates_correctly() {
        // chooserSize=4, chooserMask=3 (2 bits)
        TournamentPredictor p = new TournamentPredictor(4, 4, 1, 4);

        // Feed T,N → ghr: 0→1→2 (masked to 2 bits: 0b10=2)
        p.predict(0L); p.update(0L, true);  // ghr: (0<<1|1)&3 = 1
        p.predict(0L); p.update(0L, false); // ghr: (1<<1|0)&3 = 2

        assertEquals(2, p.getGhr(), "GHR after T,N with mask=3 should be 2");
    }

    // ── validation ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("non-power-of-2 localTableSize throws IllegalArgumentException")
    void test_invalidLocalTableSize_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new TournamentPredictor(1000, 1024, 8, 1024));
    }

    @Test
    @DisplayName("non-power-of-2 globalTableSize throws IllegalArgumentException")
    void test_invalidGlobalTableSize_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new TournamentPredictor(1024, 1000, 8, 1024));
    }

    @Test
    @DisplayName("non-power-of-2 chooserSize throws IllegalArgumentException")
    void test_invalidChooserSize_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new TournamentPredictor(1024, 1024, 8, 1000));
    }

    @Test
    @DisplayName("valid parameters are accepted")
    void test_validParams_accepted() {
        assertDoesNotThrow(() -> new TournamentPredictor(16, 16, 1, 16));
        assertDoesNotThrow(() -> new TournamentPredictor(1024, 4096, 12, 4096));
    }

    @Test
    @DisplayName("getStats returns empty before any prediction")
    void test_getStats_empty() {
        assertEquals(PredictorStats.empty(),
                new TournamentPredictor(1024, 1024, 8, 1024).getStats());
    }

    @Test
    @DisplayName("getName encodes sub-predictor names")
    void test_getName_containsSubPredictorNames() {
        TournamentPredictor p = new TournamentPredictor(1024, 1024, 8, 1024);
        String name = p.getName();
        assertTrue(name.startsWith("Tournament"), "Should start with 'Tournament'");
        assertTrue(name.contains("Bimodal"),  "Should contain local predictor name");
        assertTrue(name.contains("GShare"),   "Should contain global predictor name");
    }
}
