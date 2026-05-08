# Bug Analysis - Branch Predictor Lab

## Critical Bugs Found and Fixed

### 1. TournamentPredictor GHR Corruption (CRITICAL)

**Location**: `src/main/java/kz/devchonki/predictor/predictor/impl/TournamentPredictor.java`, line 165

**Bug Description**:
The Tournament predictor's Global History Register (GHR) was being masked with `chooserMask` instead of the proper history mask. This caused the GHR to be truncated to the chooser table size (e.g., 4096 entries = 12 bits) instead of the actual history width (e.g., 8-12 bits).

**Original Code**:
```java
// ── 4. update tournament GHR ─────────────────────────────────────────
ghr = ((ghr << 1) | (taken ? 1 : 0)) & chooserMask;  // WRONG!
```

**Fixed Code**:
```java
// ── 4. update tournament GHR ─────────────────────────────────────────
int historyMask = (1 << global.getHistoryBits()) - 1;
ghr = ((ghr << 1) | (taken ? 1 : 0)) & historyMask;
```

**Impact**:
- Tournament's GHR rapidly loses history bits, causing the global (GShare) sub-predictor to lose correlation information
- This significantly degrades Tournament's prediction accuracy
- The chooser updates become less effective because they're based on corrupted GHR values
- For traces with history-dependent patterns, Tournament should outperform Bimodal but would instead perform much worse

**Example Scenario**:
For a loop-10 trace (alternating T/T/T/T/T/T/T/T/T/N pattern):
- **Expected**: Tournament should detect the pattern via GShare, achieving ~90%+ accuracy
- **Actual (with bug)**: GHR bits are lost, GShare can't maintain proper history, accuracy degraded

---

### 2. GSharePredictor Missing Public Method

**Location**: `src/main/java/kz/devchonki/predictor/predictor/impl/GSharePredictor.java`

**Bug Description**:
The `getHistoryBits()` method was missing, but TournamentPredictor needs to access it to correctly mask the GHR.

**Fixed Code** (added method):
```java
/** Returns the history bits width for this GShare instance. */
int getHistoryBits() {
    return historyBits;
}
```

**Impact**:
Allows TournamentPredictor to dynamically compute the correct history mask regardless of configuration.

---

## Code Quality Review - No Issues Found

### Harness.java - MPKI Calculation
```java
double mpki = (double) mispredictions / (total / 1000.0);
```
**Status**: ✓ CORRECT
- Mathematically equivalent to: `(mispredictions * 1000) / total`
- This is the standard MPKI formula: mispredictions per 1000 instructions

### Frontend Components - Field Names
- `ComparisonChart.jsx`: Uses correct field names from API response
- `ResultsTable.jsx`: Uses correct field names and proper sorting logic
- `PredictorForm.jsx`: Correctly generates LOOP_10_TRACE with 100 entries (10 iterations × 10 entries per iteration)

**Status**: ✓ No issues found

---

## Testing Recommendations

After applying these fixes, verify:

1. **Tournament vs Bimodal on loop_10 trace**:
   - Bimodal: Should achieve ~90% accuracy (10 misses out of 100)
   - Tournament: Should achieve >95% accuracy by leveraging GShare

2. **GShare vs Static on alternating trace**:
   - Static (always-taken): Should achieve 50% accuracy
   - GShare with 8-bit history: Should achieve >95% accuracy after warmup

3. **Chart Values**:
   - When loading sample trace, all three dynamic predictors (Bimodal, GShare, Tournament) should show significantly different miss rates
   - Tournament should outperform Bimodal in most cases

---

## Files Modified

1. `TournamentPredictor.java` - Fixed GHR masking logic
2. `GSharePredictor.java` - Added `getHistoryBits()` method

No frontend changes required - the UI was correctly implemented.
