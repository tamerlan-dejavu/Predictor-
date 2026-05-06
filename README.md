# Branch Predictor Lab

[![Java CI](https://github.com/devchonki/branch-predictor-lab/actions/workflows/ci.yml/badge.svg)](https://github.com/devchonki/branch-predictor-lab/actions/workflows/ci.yml)

**Topic 3 — Computer Architecture & OS**  
**Team:** devchonki  
**Stack:** Java 17 · Spring Boot 3.2 · Maven

## Overview

This lab implements and evaluates several branch-prediction algorithms by
replaying recorded branch traces and measuring misprediction rates (MPKI).

| Predictor | Type | Status |
|-----------|------|--------|
| Static Always-Taken | Static | ✅ stub |
| Bimodal (2-bit PHT) | Dynamic | ✅ stub |

## Getting Started

### Prerequisites

- JDK 17+
- Maven 3.9+

### Run the application

```bash
mvn spring-boot:run
```

The server starts on **http://localhost:8080**.

### Health check

```bash
curl http://localhost:8080/api/health
# {"status":"UP","project":"Branch Predictor Lab"}
```

### Run tests

```bash
mvn test
```

## Project Structure

```
src/main/java/kz/devchonki/predictor/
├── BranchPredictorApplication.java   # Spring Boot entry point
├── api/
│   └── PredictorController.java      # REST endpoints
├── harness/
│   └── Harness.java                  # Drives predictor through a trace
├── model/
│   ├── PredictorStats.java           # Accuracy metrics record
│   └── TraceEntry.java               # Single branch event record
├── parser/
│   └── TraceParser.java              # Reads trace files
└── predictor/
    ├── BranchPredictor.java          # Interface
    ├── AbstractPredictor.java        # Shared bookkeeping
    └── impl/
        ├── StaticPredictor.java
        └── BimodalPredictor.java
traces/                               # Place .trace files here (gitignored)
```

## Adding a New Predictor

1. Create `impl/YourPredictor.java` extending `AbstractPredictor`.
2. Implement `doPrediction(long pc)`, `doUpdate(long, boolean, boolean)`, and `getName()`.
3. Annotate with `@Component` so Spring can inject it.
4. Write a unit test in `src/test/`.

## AI Usage

See [AI_USAGE.md](AI_USAGE.md).
