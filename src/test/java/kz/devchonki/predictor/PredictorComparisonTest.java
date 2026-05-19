package kz.devchonki.predictor;

import kz.devchonki.predictor.harness.Harness;
import kz.devchonki.predictor.model.PredictorStats;
import kz.devchonki.predictor.model.TraceEntry;
import kz.devchonki.predictor.parser.TraceParser;
import kz.devchonki.predictor.predictor.impl.BimodalPredictor;
import kz.devchonki.predictor.predictor.impl.GSharePredictor;
import kz.devchonki.predictor.predictor.impl.StaticPredictor;
import kz.devchonki.predictor.predictor.impl.StaticPredictor.StaticStrategy;
import kz.devchonki.predictor.predictor.impl.TournamentPredictor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Integration-style comparison of predictors on shared traces.
 */
@DisplayName("Predictor comparison (integration)")
class PredictorComparisonTest {

    private Harness harness;

    @BeforeEach
    void setUp() {
        harness = new Harness(new TraceParser());
    }

    private List<TraceEntry> loopTrace(long pc, int loopLen, int repetitions) {
        List<TraceEntry> list = new ArrayList<>(loopLen * repetitions);
        for (int r = 0; r < repetitions; r++) {
            for (int i = 0; i < loopLen - 1; i++) {
                list.add(new TraceEntry(pc, true));
            }
            list.add(new TraceEntry(pc, false));
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

    /** Period-8 pattern repeated many times — correlated global history. */
    private List<TraceEntry> correlatedPeriodTrace(long pc, int repeats) {
        boolean[] period = {true, true, true, true, false, true, true, false};
        List<TraceEntry> list = new ArrayList<>(period.length * repeats);
        for (int r = 0; r < repeats; r++) {
            for (boolean t : period) {
                list.add(new TraceEntry(pc, t));
            }
        }
        return list;
    }

    private List<TraceEntry> multiPcAliasStressTrace(int numPcs, int cycles) {
        List<TraceEntry> list = new ArrayList<>(numPcs * cycles);
        for (int c = 0; c < cycles; c++) {
            for (int i = 0; i < numPcs; i++) {
                long pc = 0x400000L + (long) i * 4;
                list.add(new TraceEntry(pc, true));
            }
        }
        return list;
    }

    @Test
    @DisplayName("On compound trace (all-NT warmup + loop), Bimodal beats static always-taken")
    void test_bimodalBetterThanStatic_loopTrace() {
        // Prefix of always-not-taken branches punishes static always-taken heavily,
        // while bimodal quickly saturates toward not-taken. Tail is classic loop-10.
        List<TraceEntry> trace = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            trace.add(new TraceEntry(0x400100L, false));
        }
        trace.addAll(loopTrace(0x400200L, 10, 100)); // 1000 further branches

        PredictorStats staticStats = harness.run(
                new StaticPredictor(StaticStrategy.ALWAYS_TAKEN), trace);
        PredictorStats bimodalStats = harness.run(new BimodalPredictor(1024), trace);

        assertTrue(bimodalStats.mispredictionRate() < staticStats.mispredictionRate(),
                String.format("Bimodal(%.2f%%) should beat Static-taken(%.2f%%)",
                        bimodalStats.mispredictionRate(), staticStats.mispredictionRate()));
    }

    @Test
    @DisplayName("On alternating trace, GShare beats Bimodal (correlated global pattern)")
    void test_gshareBetterThanBimodal_correlatedTrace() {
        List<TraceEntry> trace = alternating(0x400000L, 2000);

        PredictorStats bimodalStats = harness.run(new BimodalPredictor(1024), trace);
        PredictorStats gshareStats = harness.run(new GSharePredictor(1024, 1), trace);

        assertTrue(gshareStats.mispredictionRate() < bimodalStats.mispredictionRate(),
                String.format("GShare(%.2f%%) should beat Bimodal(%.2f%%) on alternating trace",
                        gshareStats.mispredictionRate(), bimodalStats.mispredictionRate()));
    }

    @ParameterizedTest(name = "trace kind {0}: tournament ≤ min(bimodal, gshare) + tolerance")
    @CsvSource({
            "loop",
            "alternating",
            "correlated"
    })
    @DisplayName("Tournament miss rate is no worse than the weaker of Bimodal and GShare (with tolerance)")
    void test_tournamentBestOrEqual_allTraces(String kind) {
        List<TraceEntry> trace = switch (kind) {
            case "loop" -> loopTrace(0x400000L, 10, 150);
            case "alternating" -> alternating(0x400000L, 1500);
            case "correlated" -> correlatedPeriodTrace(0x400000L, 200);
            default -> throw new IllegalArgumentException(kind);
        };

        PredictorStats b = harness.run(new BimodalPredictor(1024), trace);
        PredictorStats g = harness.run(new GSharePredictor(1024, 8), trace);
        PredictorStats t = harness.run(new TournamentPredictor(1024, 4096, 12, 4096), trace);

        double minSingle = Math.min(b.mispredictionRate(), g.mispredictionRate());

        assertTrue(t.mispredictionRate() <= minSingle + 3.0,
                String.format("Tournament(%.2f%%) should be ≤ min(bimodal,gshare)(%.2f%%) + 3%%",
                        t.mispredictionRate(), minSingle));
    }

    @ParameterizedTest
    @DisplayName("Larger Bimodal table reduces aliasing misses on multi-PC stress trace")
    @CsvSource({
            "16, 4096",
            "32, 2048",
            "64, 1024"
    })
    void test_largerTableBetter_bimodal(int smallSize, int largeSize) {
        List<TraceEntry> trace = multiPcAliasStressTrace(64, 60);

        PredictorStats smallTab = harness.run(new BimodalPredictor(smallSize), trace);
        PredictorStats largeTab = harness.run(new BimodalPredictor(largeSize), trace);

        assertTrue(largeTab.mispredictions() <= smallTab.mispredictions(),
                String.format("Bimodal(%d) should have ≤ misses than Bimodal(%d); got %d vs %d",
                        largeSize,
                        smallSize,
                        largeTab.mispredictions(),
                        smallTab.mispredictions()));
    }

    @ParameterizedTest
    @DisplayName("More GShare history bits improves accuracy on long correlated period")
    @CsvSource({
            "2, 12",
            "4, 10",
            "2, 8"
    })
    void test_moreHistoryBetter_gshare(int shortBits, int longBits) {
        List<TraceEntry> trace = correlatedPeriodTrace(0x400000L, 400);

        PredictorStats shortHist = harness.run(
                new GSharePredictor(2048, shortBits), trace);
        PredictorStats longHist = harness.run(
                new GSharePredictor(2048, longBits), trace);

        assertTrue(longHist.mispredictions() <= shortHist.mispredictions(),
                String.format("GShare(H=%d) should have ≤ misses than GShare(H=%d); %d vs %d",
                        longBits, shortBits,
                        longHist.mispredictions(), shortHist.mispredictions()));
    }
}
