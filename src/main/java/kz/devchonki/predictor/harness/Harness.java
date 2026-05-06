package kz.devchonki.predictor.harness;

import kz.devchonki.predictor.model.PredictorStats;
import kz.devchonki.predictor.model.TraceEntry;
import kz.devchonki.predictor.predictor.BranchPredictor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Drives a {@link BranchPredictor} through a sequence of {@link TraceEntry}
 * records and returns the final accuracy statistics.
 *
 * <p>TODO: implement {@link #run(BranchPredictor, List)}.
 */
@Service
public class Harness {

    /**
     * Feeds every trace entry into the predictor (predict → update cycle)
     * and returns the cumulative stats.
     *
     * @param predictor the predictor under test
     * @param trace     ordered list of branch events
     * @return accuracy stats after processing the full trace
     */
    public PredictorStats run(BranchPredictor predictor, List<TraceEntry> trace) {
        predictor.reset();
        for (TraceEntry entry : trace) {
            predictor.predict(entry.pc());
            predictor.update(entry.pc(), entry.taken());
        }
        return predictor.getStats();
    }
}
