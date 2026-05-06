package kz.devchonki.predictor.predictor.impl;

import kz.devchonki.predictor.predictor.AbstractPredictor;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Bimodal (2-bit saturating counter) predictor.
 *
 * <p>Uses a direct-mapped PHT (Pattern History Table) of {@code tableSize}
 * entries indexed by the lower bits of the branch PC.
 * Each entry is a 2-bit saturating counter:
 * <pre>
 *   00 – Strongly Not-Taken
 *   01 – Weakly  Not-Taken
 *   10 – Weakly  Taken       ← initial state
 *   11 – Strongly Taken
 * </pre>
 *
 * <p>TODO: implement full logic in a later sprint.
 */
@Component
public class BimodalPredictor extends AbstractPredictor {

    private static final int DEFAULT_TABLE_SIZE = 1024; // must be a power of 2
    private static final int WEAKLY_TAKEN = 2;
    private static final int STRONGLY_TAKEN = 3;

    private final int tableSize;
    private final int indexMask;
    private final byte[] pht;

    /** Tracks the prediction made during the last {@code predict()} per entry. */
    private boolean lastPrediction;

    public BimodalPredictor() {
        this(DEFAULT_TABLE_SIZE);
    }

    public BimodalPredictor(int tableSize) {
        if (Integer.bitCount(tableSize) != 1) {
            throw new IllegalArgumentException("tableSize must be a power of 2");
        }
        this.tableSize = tableSize;
        this.indexMask = tableSize - 1;
        this.pht = new byte[tableSize];
        Arrays.fill(pht, (byte) WEAKLY_TAKEN);
    }

    @Override
    public String getName() {
        return "Bimodal (" + tableSize + "-entry PHT)";
    }

    @Override
    public void reset() {
        super.reset();
        Arrays.fill(pht, (byte) WEAKLY_TAKEN);
    }

    @Override
    protected boolean doPrediction(long pc) {
        int idx = (int) (pc & indexMask);
        lastPrediction = pht[idx] >= 2;
        return lastPrediction;
    }

    @Override
    protected boolean getLastPrediction(long pc) {
        return lastPrediction;
    }

    @Override
    protected void doUpdate(long pc, boolean taken, boolean predicted) {
        int idx = (int) (pc & indexMask);
        if (taken) {
            if (pht[idx] < STRONGLY_TAKEN) pht[idx]++;
        } else {
            if (pht[idx] > 0) pht[idx]--;
        }
    }
}
