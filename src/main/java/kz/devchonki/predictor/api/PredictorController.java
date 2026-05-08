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

    /**
     * Runs one predictor on an in-memory trace string.
     *
     * <p>Validation errors return {@code 400} with JSON {@code {"error":"..."}}
     * (never a bare 500 for bad input).
     */
    @PostMapping("/run")
    public ResponseEntity<?> run(@RequestBody(required = false) RunRequest req) {
        if (req == null) {
            return badRequest("Request body is required");
        }
        String type = req.predictorType();
        if (type == null || type.isBlank()) {
            return badRequest("predictorType is required");
        }
        String traceRaw = req.traceContent();
        if (traceRaw == null || traceRaw.isBlank()) {
            return badRequest("traceContent must not be empty");
        }

        final BranchPredictor predictor;
        try {
            predictor = factory.create(type.trim(), req.tableSize(), req.historyBits());
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }

        final List<TraceEntry> trace;
        try {
            trace = traceParser.parseFromString(traceRaw);
        } catch (IllegalArgumentException e) {
            return badRequest("Invalid trace: " + e.getMessage());
        }
        if (trace.isEmpty()) {
            return badRequest("trace contains no branch events (only blanks/comments?)");
        }

        PredictorStats stats = harness.run(predictor, trace);
        return ResponseEntity.ok(toResponse(predictor.getName(), stats));
    }

    // ── POST /api/compare ────────────────────────────────────────────────────

    @PostMapping("/compare")
    public ResponseEntity<?> compare(@RequestBody(required = false) CompareRequest req) {
        if (req == null) {
            return badRequest("Request body is required");
        }
        if (req.predictors() == null || req.predictors().isEmpty()) {
            return badRequest("predictors list must not be empty");
        }
        String traceRaw = req.traceContent();
        if (traceRaw == null || traceRaw.isBlank()) {
            return badRequest("traceContent must not be empty");
        }

        final List<TraceEntry> trace;
        try {
            trace = traceParser.parseFromString(traceRaw);
        } catch (IllegalArgumentException e) {
            return badRequest("Invalid trace: " + e.getMessage());
        }
        if (trace.isEmpty()) {
            return badRequest("trace contains no branch events");
        }

        List<RunResponse> results = new ArrayList<>();
        for (String rawType : req.predictors()) {
            if (rawType == null || rawType.isBlank()) {
                return badRequest("predictor name must not be blank");
            }
            try {
                BranchPredictor predictor = factory.create(
                        rawType.trim(), req.tableSize(), req.historyBits());
                PredictorStats stats = harness.run(predictor, trace);
                results.add(toResponse(predictor.getName(), stats));
            } catch (IllegalArgumentException e) {
                return badRequest(e.getMessage());
            }
        }
        results.sort(Comparator.comparingDouble(RunResponse::mispredictionRate));
        return ResponseEntity.ok(results);
    }

    // ── GET /api/experiment ──────────────────────────────────────────────────

    @GetMapping("/experiment")
    public ResponseEntity<?> experiment(
            @RequestParam String predictor,
            @RequestParam int minTable,
            @RequestParam int maxTable,
            @RequestParam int steps) {
        if (predictor == null || predictor.isBlank()) {
            return badRequest("predictor parameter is required");
        }
        if (steps < 1) {
            return badRequest("steps must be >= 1");
        }
        if (Integer.bitCount(minTable) != 1) {
            return badRequest("minTable must be a power of 2");
        }
        if (Integer.bitCount(maxTable) != 1) {
            return badRequest("maxTable must be a power of 2");
        }
        if (minTable > maxTable) {
            return badRequest("minTable must be <= maxTable");
        }

        List<TraceEntry> trace = traceParser.parseFromString(LOOP_10_TRACE);
        List<ExperimentPoint> points = new ArrayList<>();
        int size = minTable;
        for (int i = 0; i < steps && size <= maxTable; i++) {
            try {
                BranchPredictor p = factory.create(predictor.trim(), size, 8);
                PredictorStats stats = harness.run(p, trace);
                points.add(new ExperimentPoint(size, stats.mispredictionRate(), stats.mpki()));
            } catch (IllegalArgumentException e) {
                return badRequest(e.getMessage());
            }
            size *= 2;
        }
        return ResponseEntity.ok(points);
    }

    // ── GET /api/health ──────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "project", "Branch Predictor Lab",
                "predictors", List.of("static_taken", "bimodal", "gshare", "tournament")
        ));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    private RunResponse toResponse(String name, PredictorStats stats) {
        return new RunResponse(name, stats.totalPredictions(), stats.mispredictions(),
                stats.mispredictionRate(), stats.mpki());
    }
}
