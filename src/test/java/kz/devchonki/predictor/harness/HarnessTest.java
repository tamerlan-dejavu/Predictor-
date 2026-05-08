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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Harness")
class HarnessTest {

    private Harness harness;

    @BeforeEach
    void setUp() {
        harness = new Harness(new TraceParser());
    }

    private List<TraceEntry> repeat(long pc, boolean taken, int count) {
        List<TraceEntry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new TraceEntry(pc, taken));
        }
        return list;
    }

    private List<TraceEntry> alternating(long pc, int count) {
        List<TraceEntry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new TraceEntry(pc, i % 2 == 0));
        }
        return list;
    }

    @Test
    @DisplayName("Perfect predictor: always-taken on all-taken trace yields 0% misprediction rate")
    void test_perfectPredictor() {
        PredictorStats stats = harness.run(
                new StaticPredictor(StaticStrategy.ALWAYS_TAKEN),
                repeat(0x400000L, true, 500));

        assertEquals(500, stats.totalPredictions());
        assertEquals(0, stats.mispredictions());
        assertEquals(0.0, stats.mispredictionRate(), 1e-9);
        assertEquals(0.0, stats.mpki(), 1e-9);
    }

    @Test
    @DisplayName("Worst predictor: always-taken on all-not-taken trace yields 100% misprediction rate")
    void test_worstPredictor() {
        PredictorStats stats = harness.run(
                new StaticPredictor(StaticStrategy.ALWAYS_TAKEN),
                repeat(0x400000L, false, 500));

        assertEquals(500, stats.totalPredictions());
        assertEquals(500, stats.mispredictions());
        assertEquals(100.0, stats.mispredictionRate(), 1e-9);
        assertEquals(1000.0, stats.mpki(), 1e-9); // 500 / (500/1000)
    }

    @Test
    @DisplayName("Alternating trace: static always-taken yields approximately 50% miss rate")
    void test_alternating50() {
        PredictorStats stats = harness.run(
                new StaticPredictor(StaticStrategy.ALWAYS_TAKEN),
                alternating(0x400000L, 1000));

        assertEquals(1000, stats.totalPredictions());
        assertEquals(500, stats.mispredictions());
        assertEquals(50.0, stats.mispredictionRate(), 1e-9);

        PredictorStats bimodalStats = harness.run(
                new BimodalPredictor(1024),
                alternating(0x400000L, 1000));
        assertTrue(bimodalStats.mispredictionRate() >= 45.0
                        && bimodalStats.mispredictionRate() <= 55.0,
                "Bimodal on alternating should be near 50%, got "
                        + bimodalStats.mispredictionRate());
    }

    @Test
    @DisplayName("Empty trace: total predictions 0, rates zero")
    void test_emptyTrace() {
        PredictorStats stats = harness.run(
                new StaticPredictor(),
                Collections.emptyList());

        assertEquals(0, stats.totalPredictions());
        assertEquals(0, stats.mispredictions());
        assertEquals(0.0, stats.mispredictionRate());
        assertEquals(0.0, stats.mpki());
    }

    @Test
    @DisplayName("MPKI: 100 predictions and 10 misses yields MPKI = 100.0")
    void test_mpkiCalculation() {
        // Static always-taken: 90 taken + 10 not-taken => 10 mispredictions
        List<TraceEntry> trace = new ArrayList<>(100);
        trace.addAll(repeat(0x400000L, true,90));
        trace.addAll(repeat(0x400004L, false,10));

        PredictorStats stats = harness.run(
                new StaticPredictor(StaticStrategy.ALWAYS_TAKEN), trace);

        assertEquals(100, stats.totalPredictions());
        assertEquals(10, stats.mispredictions());
        assertEquals(10.0, stats.mispredictionRate(), 1e-9);
        // mpki = misses / (total/1000) = 10 / 0.1 = 100
        assertEquals(100.0, stats.mpki(), 1e-9);
    }

    @Test
    @DisplayName("Harness calls predictor.reset() before each run so consecutive runs start clean")
    void test_resetBetweenRuns() {
        BranchPredictor predictor = new StaticPredictor(StaticStrategy.ALWAYS_TAKEN);
        List<TraceEntry> badTrace = repeat(0x400000L, false, 100);

        PredictorStats first = harness.run(predictor, badTrace);
        assertEquals(100, first.mispredictions());

        // Second run must reset predictor state inside harness.run
        PredictorStats second = harness.run(predictor, repeat(0x400000L, true, 100));
        assertEquals(100, second.totalPredictions());
        assertEquals(0, second.mispredictions(),
                "After reset, always-taken on all-taken should have zero misses");
    }
}
