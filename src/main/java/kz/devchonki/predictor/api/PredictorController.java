package kz.devchonki.predictor.api;

import kz.devchonki.predictor.harness.Harness;
import kz.devchonki.predictor.model.PredictorStats;
import kz.devchonki.predictor.model.TraceEntry;
import kz.devchonki.predictor.parser.TraceParser;
import kz.devchonki.predictor.predictor.BranchPredictor;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PredictorController {

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record RunRequest(String predictorType, int tableSize, int historyBits, String traceContent) {}
    public record RunResponse(String predictorName, long totalPredictions, long mispredictions,
                              double mispredictionRate, double mpki) {}
    public record CompareRequest(List<String> predictors, int tableSize, int historyBits, String traceContent) {}
    public record ExperimentPoint(int tableSize, double mispredictionRate, double mpki) {}

    // ── synthetic trace: loop with 10 iterations (9 taken + 1 exit) × 10 ──

    private static final String LOOP_10_TRACE;
    static {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) sb.append("0x400000 1\n");
            sb.append("0x400000 0\n");
        }
        LOOP_10_TRACE = sb.toString();
    }

    // ── dependencies ─────────────────────────────────────────────────────────

    private final Harness harness;
    private final TraceParser traceParser;
    private final PredictorFactory factory;

    public PredictorController(Harness harness, TraceParser traceParser, PredictorFactory factory) {
        this.harness = harness;
        this.traceParser = traceParser;
        this.factory = factory;
    }

    // ── CORS ─────────────────────────────────────────────────────────────────

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("http://localhost:5173")
                        .allowedMethods("GET", "POST", "OPTIONS");
            }
        };
    }

    // ── POST /api/run ─────────────────────────────────────────────────────────

    @PostMapping("/run")
    public ResponseEntity<?> run(@RequestBody RunRequest req) {
        BranchPredictor predictor;
        try {
            predictor = factory.create(req.predictorType(), req.tableSize(), req.historyBits());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown predictor type"));
        }
        List<TraceEntry> trace = traceParser.parseFromString(req.traceContent());
        PredictorStats stats = harness.run(predictor, trace);
        return ResponseEntity.ok(toResponse(predictor.getName(), stats));
    }

    // ── POST /api/compare ────────────────────────────────────────────────────

    @PostMapping("/compare")
    public ResponseEntity<List<RunResponse>> compare(@RequestBody CompareRequest req) {
        List<TraceEntry> trace = traceParser.parseFromString(req.traceContent());
        List<RunResponse> results = new ArrayList<>();
        for (String type : req.predictors()) {
            BranchPredictor predictor = factory.create(type, req.tableSize(), req.historyBits());
            PredictorStats stats = harness.run(predictor, trace);
            results.add(toResponse(predictor.getName(), stats));
        }
        results.sort(Comparator.comparingDouble(RunResponse::mispredictionRate));
        return ResponseEntity.ok(results);
    }

    // ── GET /api/experiment ──────────────────────────────────────────────────

    @GetMapping("/experiment")
    public ResponseEntity<List<ExperimentPoint>> experiment(
            @RequestParam String predictor,
            @RequestParam int minTable,
            @RequestParam int maxTable,
            @RequestParam int steps) {
        List<TraceEntry> trace = traceParser.parseFromString(LOOP_10_TRACE);
        List<ExperimentPoint> points = new ArrayList<>();
        int size = minTable;
        for (int i = 0; i < steps && size <= maxTable; i++, size *= 2) {
            BranchPredictor p = factory.create(predictor, size, 8);
            PredictorStats stats = harness.run(p, trace);
            points.add(new ExperimentPoint(size, stats.mispredictionRate(), stats.mpki()));
        }
        return ResponseEntity.ok(points);
    }

    // ── GET /api/health ──────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "predictors", List.of("static_taken", "bimodal", "gshare", "tournament")
        ));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private RunResponse toResponse(String name, PredictorStats stats) {
        return new RunResponse(name, stats.totalPredictions(), stats.mispredictions(),
                stats.mispredictionRate(), stats.mpki());
    }
}
