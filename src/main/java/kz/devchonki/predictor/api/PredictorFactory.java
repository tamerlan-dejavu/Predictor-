package kz.devchonki.predictor.api;

import kz.devchonki.predictor.predictor.BranchPredictor;
import kz.devchonki.predictor.predictor.impl.BimodalPredictor;
import kz.devchonki.predictor.predictor.impl.GSharePredictor;
import kz.devchonki.predictor.predictor.impl.StaticPredictor;
import kz.devchonki.predictor.predictor.impl.StaticPredictor.StaticStrategy;
import kz.devchonki.predictor.predictor.impl.TournamentPredictor;
import org.springframework.stereotype.Component;

@Component
public class PredictorFactory {

    public BranchPredictor create(String type, int tableSize, int historyBits) {
        return switch (type.toLowerCase()) {
            case "static_taken" -> new StaticPredictor(StaticStrategy.ALWAYS_TAKEN);
            case "static_nt"    -> new StaticPredictor(StaticStrategy.ALWAYS_NOT_TAKEN);
            case "btfn"         -> new StaticPredictor(StaticStrategy.BTFN);
            case "bimodal"      -> new BimodalPredictor(tableSize);
            case "gshare"       -> new GSharePredictor(tableSize, historyBits);
            case "tournament"   -> new TournamentPredictor(tableSize, tableSize, historyBits, tableSize);
            default             -> throw new IllegalArgumentException("Unknown predictor type: " + type);
        };
    }
}
