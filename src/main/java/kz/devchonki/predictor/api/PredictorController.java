package kz.devchonki.predictor.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST API for the Branch Predictor Lab.
 *
 * <p>TODO: add endpoints for running a trace, selecting a predictor, and
 * retrieving detailed results.
 */
@RestController
@RequestMapping("/api")
public class PredictorController {

    /**
     * Simple liveness probe.
     *
     * <p>GET /api/health → {@code {"status":"UP","project":"Branch Predictor Lab"}}
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "project", "Branch Predictor Lab"
        ));
    }
}
