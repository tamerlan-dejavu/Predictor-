package kz.devchonki.predictor.model;

/**
 * Immutable snapshot of predictor accuracy metrics.
 *
 * @param totalPredictions  total number of branch predictions made
 * @param mispredictions    number of incorrect predictions
 * @param mispredictionRate mispredictions / totalPredictions (0.0 when no predictions)
 * @param mpki              mispredictions per kilo-instruction
 */
public record PredictorStats(
        long totalPredictions,
        long mispredictions,
        double mispredictionRate,
        double mpki
) {
    public static PredictorStats empty() {
        return new PredictorStats(0, 0, 0.0, 0.0);
    }
}
