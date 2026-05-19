package kz.devchonki.predictor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Covers {@link ExperimentRunner#main} as a black-box CLI (writes CSV under {@code results/}).
 */
@DisplayName("ExperimentRunner CLI")
class ExperimentRunnerTest {

    @Test
    @DisplayName("main() completes and writes non-empty experiment_results.csv when traces exist")
    void main_writesResults() throws Exception {
        Path traces = Path.of("traces/synthetic/always_taken.trace");
        if (!Files.exists(traces)) {
            return;
        }
        ExperimentRunner.main(new String[0]);
        Path out = Path.of("results/experiment_results.csv");
        assertTrue(Files.exists(out), "Expected " + out.toAbsolutePath());
        String csv = Files.readString(out);
        assertTrue(csv.lines().count() > 5, "CSV should contain header + many data rows");
        assertTrue(csv.contains("predictor,trace,"), "CSV header missing");
    }
}
