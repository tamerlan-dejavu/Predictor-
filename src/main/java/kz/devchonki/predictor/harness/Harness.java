package kz.devchonki.predictor.harness;

import kz.devchonki.predictor.model.PredictorStats;
import kz.devchonki.predictor.model.TraceEntry;
import kz.devchonki.predictor.parser.TraceParser;
import kz.devchonki.predictor.predictor.BranchPredictor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Drives a {@link BranchPredictor} through a sequence of {@link TraceEntry}
 * records, collects misprediction statistics, and returns a {@link PredictorStats}
 * snapshot.
 *
 * <p>Predict/update protocol per entry:
 * <ol>
 *   <li>{@code prediction = predictor.predict(pc)}</li>
 *   <li>{@code predictor.update(pc, taken)}</li>
 *   <li>if {@code prediction != taken} → count misprediction</li>
 * </ol>
 */
@Service
public class Harness {

    private final TraceParser traceParser;

    public Harness(TraceParser traceParser) {
        this.traceParser = traceParser;
    }

    /**
     * Runs the predictor over an in-memory trace list.
     *
     * @param predictor predictor under test (will be reset first)
     * @param trace     ordered branch events
     * @return accuracy statistics after processing the full trace
     */
    public PredictorStats run(BranchPredictor predictor, List<TraceEntry> trace) {
        predictor.reset();

        long total = 0;
        long mispredictions = 0;

        for (TraceEntry entry : trace) {
            boolean prediction = predictor.predict(entry.pc());
            predictor.update(entry.pc(), entry.taken());
            if (prediction != entry.taken()) {
                mispredictions++;
            }
            total++;
        }

        if (total == 0) {
            return PredictorStats.empty();
        }

        double mispredictionRate = (double) mispredictions / total * 100.0;
        double mpki = (double) mispredictions / (total / 1000.0);

        return new PredictorStats(total, mispredictions, mispredictionRate, mpki);
    }

    /**
     * Convenience overload: parses the trace file, then calls
     * {@link #run(BranchPredictor, List)}.
     *
     * @param predictor predictor under test
     * @param traceFile path to the trace file
     * @return accuracy statistics after processing the full trace
     * @throws IOException if the file cannot be read
     */
    public PredictorStats run(BranchPredictor predictor, Path traceFile) throws IOException {
        List<TraceEntry> trace = traceParser.parse(traceFile);
        return run(predictor, trace);
    }
}
