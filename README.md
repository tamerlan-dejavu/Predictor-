# Branch Predictor Lab

[![Java CI](https://github.com/tamerlan-dejavu/Predictor-/actions/workflows/ci.yml/badge.svg)](https://github.com/tamerlan-dejavu/Predictor-/actions/workflows/ci.yml)

**Topic 3 — Computer Architecture & OS**  
**Team:** Девчонки — Тамерлан, Дария, Маша, Диана · *devchonki*  
**Stack:** Java 17 · Spring Boot 3.2 · Maven · React (Vite) · Recharts

**Release:** **v1.0.0**

## Overview

The lab implements branch predictors, replays traces from `traces/synthetic/`, and reports misprediction rate (%) and MPKI. A **Spring Boot REST API** and a **React dashboard** provide experiments and charts.

| Predictor | Type | Status |
|-----------|------|--------|
| Static (Always-Taken / Always-NT / BTFN) | Static | ✅ |
| Bimodal (2-bit saturating PHT) | Dynamic | ✅ |
| GShare (GHR ⊕ PC index) | Dynamic | ✅ |
| Tournament (Bimodal + GShare + chooser) | Hybrid | ✅ |

## Results

Сводка по **синтетическим трассам** (`experiment_results.csv`, Step 1: таблица 1024 / H8 для динамических предсказателей). Десятичный разделитель в CSV — запятая (европейский формат экспорта).

### Таблица misprediction rate (%)

| Predictor | alternating | always_not_taken | always_taken | loop_10 |
|-----------|-------------|------------------|--------------|---------|
| Static-ALWAYS_TAKEN | 50.0 | 100.0 | 0.0 | 10.0 |
| Static-ALWAYS_NOT_TAKEN | 50.0 | 0.0 | 100.0 | 90.0 |
| Static-BTFN | 50.0 | 0.0 | 100.0 | 90.0 |
| Bimodal-1024 | 50.0 | 0.1 | 0.0 | 10.0 |
| GShare-1024-H8 | 0.4 | 0.1 | 0.0 | 10.0 |
| Tournament (L=1024, G=4096,H12) | 0.7 | 0.1 | 0.0 | 0.3 |

**Выводы (кратко):** на чередующейся трассе корреляционный **GShare** и **Tournament** резко лучше бимодального; на **loop_10** статический always-taken совпадает с бимодалем по уровню промаха (~10%); **Tournament** может на этих трассах догнать лучший из компонентов. Полные sweep-таблицы по размеру PHT и длине истории — в `results/experiment_results.csv` и `results/ANALYSIS.md`.

### ASCII (фрагмент experiment CSV)

```
predictor                          trace              miss%   mpki
---------------------------------  -----------------  ------  -----
Static-ALWAYS_TAKEN               alternating        50.00   500.0 / 1000 ветвей
GShare-1024-H8                    alternating         0.40     4.0
Tournament(...GShare-4096-H12)    alternating         0.70     7.0
Static-ALWAYS_TAKEN               loop_10             10.00   100.0
Bimodal-1024                      loop_10             10.00   100.0
Tournament(...)-                  loop_10              0.30     3.0
```

## Getting Started

### Prerequisites

- JDK 17+
- Maven 3.9+
- Node.js 20+ (для фронтенда)

### Quick Start (Full Demo)

**Windows:**
```cmd
run.bat
```

**Linux / macOS:**
```bash
chmod +x run.sh
./run.sh
```

This launches both backend (http://localhost:8080) and frontend (http://localhost:5173) in separate terminal windows, then automatically opens the dashboard in your browser.

**If already running, just open the UI:**

**Windows:**
```cmd
open.bat
```

**Linux / macOS:**
```bash
chmod +x open.sh
./open.sh
```

---

### Manual Setup

#### Backend

```bash
mvn spring-boot:run
```

API: **http://localhost:8080** — например `GET /api/health`, `POST /api/run`, `GET /api/experiment`.

#### Frontend (в отдельном терминале)

```bash
cd frontend
npm install   # или npm ci
npm run dev
```

Дашборд: **http://localhost:5173** (прокси к `/api` настроен в `vite.config.js` при необходимости).

### Продакшен: Render (API) + Vercel (фронт)

1. **Render — Spring Boot**  
   - Вариант A: в корне репозитория есть [`render.yaml`](render.yaml) и [`Dockerfile`](Dockerfile): создайте **Blueprint** или **Web Service** с runtime **Docker**.  
   - После деплоя скопируйте публичный URL сервиса, например `https://branch-predictor-lab-api.onrender.com`.  
   - В **Environment** задайте **`APP_CORS_ALLOWED_ORIGINS`** = URL фронта на Vercel (точное совпадение origin, без слэша в конце), например `https://your-app.vercel.app`. Несколько origin через запятую.  
   - Порт: в `application.properties` уже задано `server.port=${PORT:8080}` — Render подставляет `PORT` сам.

2. **Vercel — React (Vite)**  
   - **Root Directory:** `frontend` · **Build Command:** `npm run build` · **Output Directory:** `dist`.  
   - **Environment Variables:** `VITE_API_BASE_URL` = URL API **без** `/api` и без завершающего слэша (как в [`frontend/.env.example`](frontend/.env.example)), например `https://branch-predictor-lab-api.onrender.com`.  
   - SPA-роуты: в корне фронта лежит [`frontend/vercel.json`](frontend/vercel.json) (fallback на `index.html`).

3. Порядок: сначала задеплойте API на Render, затем пропишите его URL в Vercel и **пересоберите** фронт; после этого обновите **`APP_CORS_ALLOWED_ORIGINS`** на Render под финальный домен Vercel.

### Tests & coverage

```bash
mvn clean test
mvn verify          # тесты + JaCoCo check (line coverage ≥ 60 %)
```

Отчёт покрытия: `target/site/jacoco/index.html`.

### Health check

```bash
curl http://localhost:8080/api/health
```

## Live Demo (Production)

**Frontend:** https://predictor-chi.vercel.app/  
**Backend API:** https://predictor-api-whg0.onrender.com

### Quick launch for defense:

```cmd
# Windows
.\open.bat
```

```bash
# Linux / macOS
./open.sh
```

Or run the full demo launcher:

```cmd
# Windows
.\run.bat
```

```bash
# Linux / macOS
./run.sh
```

Both will open the dashboard in your browser.

⚠️ **Note:** Render backend may be asleep after 15 minutes of inactivity. If you see connection errors, wait 30-60 seconds for it to wake up.

### What to show:

1. **Interactive Dashboard** — https://predictor-chi.vercel.app/
   - Predictor selection (Static, Bimodal, GShare, Tournament)
   - Trace selection (alternating, always_taken, always_not_taken, loop_10)
   - Configuration sliders (table size, history bits)
   - Real-time results (misprediction rate %, MPKI)

2. **Charts & Experiments** — click "Run Experiment" to show:
   - Full sweep table (all predictors × all traces)
   - Comparison plots (GShare history length effect, tournament trade-offs)

3. **Backend API** — https://predictor-api-whg0.onrender.com
   - `GET /api/health` — status check
   - `GET /api/experiment` — batch results as JSON

## Project Structure

```
src/main/java/kz/devchonki/predictor/
├── BranchPredictorApplication.java
├── ExperimentRunner.java              # CLI: полный прогон экспериментов → results/
├── api/
│   ├── PredictorController.java
│   ├── PredictorFactory.java
│   └── WebCorsConfig.java
├── harness/Harness.java
├── model/  (PredictorStats, TraceEntry)
├── parser/TraceParser.java
└── predictor/
    ├── BranchPredictor.java
    └── impl/
        ├── StaticPredictor.java
        ├── BimodalPredictor.java
        ├── GSharePredictor.java
        └── TournamentPredictor.java
frontend/                  # React + Vite + Recharts
traces/synthetic/        # эталонные .trace
results/                 # experiment_results.csv, ANALYSIS.md
```

## Adding a New Predictor

1. Реализуйте `BranchPredictor` в `predictor/impl/`.
2. Зарегистрируйте тип в `PredictorFactory` и при необходимости в `GET /api/health`.
3. Добавьте JUnit-тесты и прогон через `Harness`.

## AI Usage

See [AI_USAGE.md](AI_USAGE.md).
