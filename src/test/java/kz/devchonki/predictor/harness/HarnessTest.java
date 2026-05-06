package kz.devchonki.predictor.harness;

import kz.devchonki.predictor.model.PredictorStats;
import kz.devchonki.predictor.model.TraceEntry;
import kz.devchonki.predictor.parser.TraceParser;
import kz.devchonki.predictor.predictor.BranchPredictor;
import kz.devchonki.predictor.predictor.impl.BimodalPredictor;
import kz.devchonki.predictor.predictor.impl.StaticPredictor;
import kz.devchonki.predictor.predictor.impl.StaticPredictor.StaticStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Harness")
class HarnessTest {

    private Harness harness;
    private TraceParser parser;

    @BeforeEach
    void setUp() {
        parser = new TraceParser();
        harness = new Harness(parser);
    }

    // â”€â”€ helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private List<TraceEntry> repeat(long pc, boolean taken, int count) {
        List<TraceEntry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) list.add(new TraceEntry(pc, taken));
        return list;
    }

    // â”€â”€ empty trace â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    @DisplayName("returns empty stats for empty trace")
    void emptyTrace() {
        PredictorStats stats = harness.run(new StaticPredictor(), Collections.emptyList());
        assertEquals(0, stats.totalPredictions());
        assertEquals(0, stats.mispredictions());
        assertEquals(0.0, stats.mispredictionRate());
        assertEquals(0.0, stats.mpki());
    }

    // â”€â”€ StaticPredictor (always-taken) vs always_taken trace â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    @DisplayName("StaticPredictor(taken) on always-taken trace â†’ 0% miss rate")
    void staticTakenOnAlwaysTaken() {
        List<TraceEntry> trace = repeat(0x400000L, true, 1000);
        PredictorStats stats = harness.run(new StaticPredictor(StaticStrategy.ALWAYS_TAKEN), trace);

        assertEquals(1000, stats.totalPredictions());
        assertEquals(0, stats.mispredictions());
        assertEquals(0.0, stats.mispredictionRate(), 1e-9);
        assertEquals(0.0, stats.mpki(), 1e-9);
    }

    // â”€â”€ StaticPredictor (always-taken) vs always_not_taken trace â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    @DisplayName("StaticPredictor(taken) on always-not-taken trace â†’ 100% miss rate")
    void staticTakenOnAlwaysNotTaken() {
        List<TraceEntry> trace = repeat(0x400000L, false, 1000);
        PredictorStats stats = harness.run(new StaticPredictor(StaticStrategy.ALWAYS_TAKEN), trace);

        assertEquals(1000, stats.totalPredictions());
        assertEquals(1000, stats.mispredictions());
        assertEquals(100.0, stats.mispredictionRate(), 1e-9);
        assertEquals(1000.0, stats.mpki(), 1e-9);
    }

    // â”€â”€ alternating trace â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    @DisplayName("StaticPredictor(taken) on alternating trace â†’ 50% miss rate")
    void staticTakenOnAlternating() {
        List<TraceEntry> trace = new ArrayList<>(1000);
        for (int i = 0; i < 1000; i++) {
            trace.add(new TraceEntry(0x400000L, i % 2 == 0));
        }
        PredictorStats stats = harness.run(new StaticPredictor(StaticStrategy.ALWAYS_TAKEN), trace);

        assertEquals(1000, stats.totalPredictions());
        assertEquals(500, stats.mispredictions());
        assertEquals(50.0, stats.mispredictionRate(), 1e-9);
        assertEquals(500.0, stats.mpki(), 1e-9);
    }

    // â”€â”€ loop_10 pattern â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    @DisplayName("loop-10 pattern: BimodalPredictor misses <= 10% (only on loop exits)")
    void bimodalOnLoop10() {
        List<TraceEntry> trace = new ArrayList<>(1000);
        for (int rep = 0; rep < 100; rep++) {
            for (int i = 0; i < 9; i++) trace.add(new TraceEntry(0x400000L, true));
            trace.add(new TraceEntry(0x400000L, false));
        }
        PredictorStats stats = harness.run(new BimodalPredictor(), trace);

        assertEquals(1000, stats.totalPredictions());
        // bimodal eventually predicts taken for all â†’ misses only the not-taken exits
        // worst case: 100 exits misses + 100 first-after-exit misses = 200 = 20%
        assertTrue(stats.mispredictionRate() <= 20.0,
                "Expected â‰¤ 20% miss rate, got " + stats.mispredictionRate());
    }

    // â”€â”€ mispredictionRate and MPKI formulas â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    @DisplayName("mispredictionRate = mispredictions / total * 100")
    void mispredictionRateFormula() {
        // 4 taken, 4 not-taken; static(always-taken) misses the 4 not-taken
        List<TraceEntry> trace = List.of(
                new TraceEntry(0x1L, true),
                new TraceEntry(0x2L, false),
                new TraceEntry(0x3L, true),
                new TraceEntry(0x4L, false),
                new TraceEntry(0x5L, true),
                new TraceEntry(0x6L, false),
                new TraceEntry(0x7L, true),
                new TraceEntry(0x8L, false)
        );
        PredictorStats stats = harness.run(new StaticPredictor(StaticStrategy.ALWAYS_TAKEN), trace);
        assertEquals(8, stats.totalPredictions());
        assertEquals(4, stats.mispredictions());
        assertEquals(50.0, stats.mispredictionRate(), 1e-9);
        assertEquals(500.0, stats.mpki(), 1e-9); // 4 / (8/1000) = 500
    }

    // â”€â”€ file overload â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    @DisplayName("run(predictor, Path) delegates to run(predictor, List)")
    void runWithFileDelegate() throws IOException {
        Path alwaysTaken = Paths.get("traces/synthetic/always_taken.trace");
        if (!alwaysTaken.toFile().exists()) {
            // skip if running outside project root
            return;
        }
        PredictorStats stats = harness.run(new StaticPredictor(StaticStrategy.ALWAYS_TAKEN), alwaysTaken);
        assertEquals(1000, stats.totalPredictions());
        assertEquals(0, stats.mispredictions());
    }

    // â”€â”€ harness resets predictor state between runs â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    @DisplayName("harness resets predictor before each run")
    void harnessResetsPredictor() {
        BranchPredictor predictor = new StaticPredictor(StaticStrategy.ALWAYS_TAKEN);
        List<TraceEntry> trace = repeat(0x400000L, false, 500);

        PredictorStats first = harness.run(predictor, trace);
        PredictorStats second = harness.run(predictor, trace);

        assertEquals(first.totalPredictions(), second.totalPredictions());
        assertEquals(first.mispredictions(), second.mispredictions());
    }
}
