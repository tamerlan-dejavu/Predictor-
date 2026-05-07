package kz.devchonki.predictor;

import kz.devchonki.predictor.harness.Harness;
import kz.devchonki.predictor.model.PredictorStats;
import kz.devchonki.predictor.model.TraceEntry;
import kz.devchonki.predictor.parser.TraceParser;
import kz.devchonki.predictor.predictor.BranchPredictor;
import kz.devchonki.predictor.predictor.impl.BimodalPredictor;
import kz.devchonki.predictor.predictor.impl.GSharePredictor;
import kz.devchonki.predictor.predictor.impl.StaticPredictor;
import kz.devchonki.predictor.predictor.impl.StaticPredictor.StaticStrategy;
import kz.devchonki.predictor.predictor.impl.TournamentPredictor;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Standalone experiment runner for the Branch Predictor Lab.
 *
 * <p>Runs four experiments and emits Markdown tables to stdout plus a CSV
 * summary to {@code results/experiment_results.csv}.
 *
 * <h2>Analysis of Results</h2>
 *
 * <h3>1. Why Bimodal fails on the alternating trace (50 % miss rate, invariant
 * to table size)</h3>
 * <p>Bimodal maintains one 2-bit saturating counter per branch PC. All
 * counters are initialised to 2 (Weakly Taken). On the alternating trace
 * T,NT,T,NT…, the counter at PC 0x400000 oscillates between states 2 and 3
 * forever, never falling below the decision threshold of 2. The predictor
 * therefore always says "taken" and misses every not-taken outcome →
 * exactly 50 % miss rate. Increasing the table size changes nothing because
 * <em>aliasing is not the problem</em>: only one PC is active. The root cause
 * is that Bimodal has no access to the previous branch outcome — it cannot
 * distinguish "I'm about to see the taken half of an alternating pair" from
 * "I'm about to see the not-taken half".
 *
 * <h3>2. Why GShare is superior on correlated branches</h3>
 * <p>GShare XORs the PC with the Global History Register (GHR) to select a
 * PHT entry. For the alternating trace, a single branch PC is always active,
 * so the effective index is just the GHR value. After the first 8 entries the
 * GHR alternates between two fixed values (0b01010101 and 0b10101010 with an
 * 8-bit history). Each value maps to a distinct PHT slot: one slot learns
 * "predict taken", the other learns "predict not-taken". After ≈4 warmup
 * misses GShare achieves near-zero miss rate on the alternating trace. The
 * general principle: any branch whose outcome correlates with the N most
 * recent branch outcomes is a candidate for GShare improvement. Longer
 * histories capture longer correlations at the cost of a slower warm-up and
 * potential aliasing in a fixed-size PHT.
 *
 * <h3>3. When Tournament does not outperform its best component</h3>
 * <p>Tournament's chooser table starts at "weakly local" (counter = 1) and
 * is only updated when the two sub-predictors <em>disagree</em>. On
 * single-PC synthetic traces one sub-predictor tends to dominate throughout:
 * <ul>
 *   <li><b>always_taken / always_not_taken</b>: both Bimodal and GShare
 *       converge to perfect prediction after one or two warmup misses.
 *       Tournament is never better, and the chooser never meaningfully
 *       trains.</li>
 *   <li><b>alternating</b>: GShare dominates after ~4 misses; Bimodal never
 *       learns. The chooser migrates toward GShare but incurs extra misses
 *       during the transition, so Tournament's total miss rate may exceed
 *       GShare's.</li>
 *   <li><b>loop_10</b>: Bimodal learns the loop exit after one miss per
 *       cycle; GShare can learn it faster. The chooser gradually tilts
 *       toward whichever is better, but both are already close to 10 % miss
 *       rate before the chooser stabilises.</li>
 * </ul>
 * <p>Tournament shines on real mixed workloads where some branches are
 * better served by local (Bimodal) prediction while others benefit from
 * global correlation (GShare), allowing per-branch routing.
 *
 * <h3>4. Table size needed for 90 % of maximum accuracy</h3>
 * <p>For these single-PC synthetic traces aliasing is absent even at 16
 * entries, so every table size produces identical miss rates — 16 entries
 * is already at the ceiling. In real SPEC CPU workloads with thousands of
 * distinct branch PCs competing for PHT entries, empirical data shows that
 * Bimodal and GShare reach ≈90 % of their asymptotic accuracy at around
 * 1 024–4 096 entries; returns beyond 8 192 are strongly diminishing.
 * Tournament is less sensitive to table size because the chooser partially
 * masks aliasing in the sub-predictors.
 */
public class ExperimentRunner {

    private static final TraceParser PARSER  = new TraceParser();
    private static final Harness     HARNESS = new Harness(PARSER);

    private static final Path TRACES_DIR   = Path.of("traces/synthetic");
    private static final Path RESULTS_FILE = Path.of("results/experiment_results.csv");

    private static final String[] ALL_TRACES = {
        "alternating.trace", "always_not_taken.trace", "always_taken.trace", "loop_10.trace"
    };

    // ── data types ────────────────────────────────────────────────────────────

    record Row(String predictor, String trace, int tableSize, int historyBits,
               double mispredictionRate, double mpki) {
        String toCsv() {
            return String.format("%s,%s,%d,%d,%.4f,%.4f",
                    predictor, trace, tableSize, historyBits, mispredictionRate, mpki);
        }
    }

    /** Predictor + its metadata for the CSV. */
    record Config(BranchPredictor predictor, int tableSize, int historyBits) {}

    // ── entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        List<Row> all = new ArrayList<>();

        step1(all);
        step2(all);
        step3(all);
        step4(all);

        saveCsv(all);
    }

    // ── Step 1: all predictors × all traces (default configs) ─────────────────

    private static void step1(List<Row> out) throws Exception {
        List<Config> cfgs = List.of(
                new Config(new StaticPredictor(StaticStrategy.ALWAYS_TAKEN),     0,    0),
                new Config(new StaticPredictor(StaticStrategy.ALWAYS_NOT_TAKEN), 0,    0),
                new Config(new StaticPredictor(StaticStrategy.BTFN),             0,    0),
                new Config(new BimodalPredictor(1024),                        1024,    0),
                new Config(new GSharePredictor(1024, 8),                      1024,    8),
                new Config(new TournamentPredictor(),                         1024,   12)
        );

        System.out.println("\n## Step 1 — All predictors on all traces (default configs)\n");
        int PW = 44, TW = 20;
        printHeader(PW, TW);

        for (String tf : ALL_TRACES) {
            List<TraceEntry> trace = PARSER.parse(TRACES_DIR.resolve(tf));
            String tname = stripExt(tf);
            for (Config c : cfgs) {
                PredictorStats s = HARNESS.run(c.predictor(), trace);
                out.add(new Row(c.predictor().getName(), tname,
                        c.tableSize(), c.historyBits(), s.mispredictionRate(), s.mpki()));
                printRow(c.predictor().getName(), tname,
                        s.mispredictionRate(), s.mpki(), PW, TW);
            }
        }
    }

    // ── Step 2: Bimodal — tableSize sweep ─────────────────────────────────────

    private static void step2(List<Row> out) throws Exception {
        int[]    sizes  = {16, 64, 256, 1024, 4096, 16384, 65536};
        String[] traces = {"alternating.trace", "loop_10.trace"};

        System.out.println("\n## Step 2 — Bimodal: tableSize vs misprediction rate\n");
        int PW = 18, TW = 12;
        printHeader(PW, TW);

        for (String tf : traces) {
            List<TraceEntry> trace = PARSER.parse(TRACES_DIR.resolve(tf));
            String tname = stripExt(tf);
            for (int sz : sizes) {
                BimodalPredictor p = new BimodalPredictor(sz);
                PredictorStats s = HARNESS.run(p, trace);
                out.add(new Row(p.getName(), tname, sz, 0, s.mispredictionRate(), s.mpki()));
                printRow(p.getName(), tname, s.mispredictionRate(), s.mpki(), PW, TW);
            }
        }
    }

    // ── Step 3: GShare — historyBits sweep (tableSize=4096) ───────────────────

    private static void step3(List<Row> out) throws Exception {
        int[]    bits   = {2, 4, 6, 8, 10, 12, 14, 16};
        int      tbl    = 4096;
        String[] traces = {"alternating.trace", "loop_10.trace"};

        System.out.println("\n## Step 3 — GShare: historyBits sweep (tableSize=4096)\n");
        int PW = 22, TW = 12;
        printHeader(PW, TW);

        for (String tf : traces) {
            List<TraceEntry> trace = PARSER.parse(TRACES_DIR.resolve(tf));
            String tname = stripExt(tf);
            for (int h : bits) {
                GSharePredictor p = new GSharePredictor(tbl, h);
                PredictorStats s = HARNESS.run(p, trace);
                out.add(new Row(p.getName(), tname, tbl, h, s.mispredictionRate(), s.mpki()));
                printRow(p.getName(), tname, s.mispredictionRate(), s.mpki(), PW, TW);
            }
        }
    }

    // ── Step 4: Final comparison — tuned configs, all traces ──────────────────

    private static void step4(List<Row> out) throws Exception {
        List<Config> cfgs = List.of(
                new Config(new StaticPredictor(StaticStrategy.ALWAYS_TAKEN),     0,    0),
                new Config(new StaticPredictor(StaticStrategy.ALWAYS_NOT_TAKEN), 0,    0),
                new Config(new StaticPredictor(StaticStrategy.BTFN),             0,    0),
                new Config(new BimodalPredictor(4096),                        4096,    0),
                new Config(new GSharePredictor(4096, 8),                      4096,    8),
                new Config(new TournamentPredictor(4096, 4096, 8, 1024),      4096,    8)
        );

        System.out.println(
                "\n## Step 4 — Final comparison: tuned configs on all traces\n");
        System.out.println(
                "> Bimodal(4096) | GShare(4096,H8) | Tournament(L=4096,G=4096,H8,C=1024)");
        System.out.println("> No SPEC CPU traces found — synthetic traces only.\n");
        int PW = 46, TW = 20;
        printHeader(PW, TW);

        for (String tf : ALL_TRACES) {
            List<TraceEntry> trace = PARSER.parse(TRACES_DIR.resolve(tf));
            String tname = stripExt(tf);
            for (Config c : cfgs) {
                PredictorStats s = HARNESS.run(c.predictor(), trace);
                out.add(new Row(c.predictor().getName(), tname,
                        c.tableSize(), c.historyBits(), s.mispredictionRate(), s.mpki()));
                printRow(c.predictor().getName(), tname,
                        s.mispredictionRate(), s.mpki(), PW, TW);
            }
        }
    }

    // ── CSV output ────────────────────────────────────────────────────────────

    private static void saveCsv(List<Row> rows) throws Exception {
        Files.createDirectories(RESULTS_FILE.getParent());
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(RESULTS_FILE))) {
            w.println("predictor,trace,tableSize,historyBits,mispredictionRate,mpki");
            rows.forEach(r -> w.println(r.toCsv()));
        }
        System.out.printf("%nSaved %d rows → %s%n", rows.size(), RESULTS_FILE.toAbsolutePath());
    }

    // ── Markdown helpers ──────────────────────────────────────────────────────

    private static void printHeader(int pw, int tw) {
        System.out.printf("| %-" + pw + "s | %-" + tw + "s | %9s | %9s |%n",
                "Predictor", "Trace", "MissRate%", "MPKI");
        System.out.printf("|%s|%s|%s|%s|%n",
                "-".repeat(pw + 2), "-".repeat(tw + 2),
                "-".repeat(11), "-".repeat(11));
    }

    private static void printRow(String pred, String trace,
                                  double rate, double mpki, int pw, int tw) {
        System.out.printf("| %-" + pw + "s | %-" + tw + "s | %9.4f | %9.4f |%n",
                pred, trace, rate, mpki);
    }

    private static String stripExt(String fname) {
        int i = fname.lastIndexOf('.');
        return i < 0 ? fname : fname.substring(0, i);
    }
}
