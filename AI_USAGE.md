# AI_USAGE.md — Branch Predictor Lab

## Инструмент: Claude Code (sonnet-4 / opus-4 / haiku-4-5)

Проект реализован с использованием Claude Code (в составе **Cursor Agent**) как основного AI-ассистента по коду. Использовался для: начальной структуры Maven/Spring Boot, реализации предсказателей, парсера и harness, REST API и тестов, настройки CI/JaCoCo, финальной валидации edge cases и правок документации.

Все сгенерированные фрагменты проходили ручной просмотр, `mvn test` и при необходимости доработку (например, порядок обновления GHR, scoring в Tournament, валидация `/api/run`).

## Примеры использования

### 1. Генерация BimodalPredictor

**Задача:** реализовать PHT с 2-bit saturating counters и корректной индексацией.

**Промпт (суть):** «Реализуй BimodalPredictor.java. PHT = int[], значения 0–3, predict если counter ≥ 2, индекс `(pc >> 2) & (tableSize - 1)`, обновление saturating.»

**Результат:** полный класс с `getIndex()`, `predict()`, `update()`, конструктор с проверкой степени двойки, `reset()`, `getStats()`, позже — метод `peek()` для Tournament.

**Что понял разработчик:** saturating decrement обязан ограничивать пол `Math.max(0, counter - 1)` (и аналогично `Math.min(3, …)` при taken); иначе счётчик «уезжает» за допустимый диапазон 2-bit.

---

### 2. Отладка двойного счётчика в Tournament

**Задача:** внутренние предсказатели получали лишние `totalPredictions`; финальный misprediction иногда считался после изменения chooser.

**Промпт (суть):** «Tournament вызывает `local.predict()` и `global.predict()` — они инкрементируют счётчики. Это баг? Как исправить? Плюс scoring должен совпадать с тем, что вернули пользователю.»

**Результат:**

- добавлены `BimodalPredictor.peek(pc)` и `GSharePredictor.peek(pc)` без побочных эффектов на метрики;
- финальный промах считается по снимку решения (`lastUsedGlobal` + последние предсказания суб-агентов) **до** мутации chooser.

**Что понял разработчик:** вызов «настоящего» `predict()` ради значения нарушает SRP метрик компонента; peek изолирует чтение состояния PHT/GHR.

---

### 3. REST API с CORS

**Задача:** фронтенд на Vite (`localhost:5173`) не мог вызывать Spring Boot из-за CORS.

**Промпт (суть):** «Настрой CORS для Spring Boot 3, разреши origin `http://localhost:5173`, методы GET/POST/OPTIONS для `/api/**`.»

**Результат:** `@Bean WebMvcConfigurer` с `registry.addMapping("/api/**").allowedOrigins("http://localhost:5173").allowedMethods("GET", "POST", "OPTIONS")` в `PredictorController` (или вынесенный конфиг по желанию команды).

---

### 4. React LineChart с двумя осями (Recharts)

**Задача:** на одном графике показать misprediction rate (%) и MPKI.

**Промпт (суть):** «LineChart в Recharts с двумя Y-осями: слева rate%, справа MPKI.»

**Результат:** два `YAxis` с `yAxisId="left"` и `yAxisId="right"`, у правой оси `orientation="right"`, у `Line` — соответствующие `yAxisId`.

---

### 5. TraceParser и Harness (дополнительно)

**Задача:** парсинг трасс `hex-pc` + `1/0`, пропуск комментариев; прогон предсказателя с подсчётом miss rate и MPKI в harness.

**Результат:** `parse(Path)`, `parseFromString`, BOM-strip; `Harness.run` с явной формулой rate в процентах и MPKI.

**Что проверили:** синтетические трассы в `traces/synthetic/`, unit-тесты на граничные строки и UTF-8 BOM.

---

### 6. Финальная валидация API (edge cases)

**Задача:** пустой `traceContent`, неверный `predictorType`, `tableSize` не степень двойки — отдавать **400** и JSON `{"error":"..."}`, не 500.

**Результат:** явные проверки в `PredictorController`, `MockMvc`-тесты в `PredictorControllerTest`.

---

## Статистика (оценочная доля AI-кода)

Оценки **приблизительные**, отражают объём первично сгенерированного или существенно предложенного AI с последующей вычиткой/рефакторингом человеком.

| Модуль | % AI-кода (оценка) | Комментарий |
|--------|-------------------|-------------|
| `predictor/impl/` | **~75–85%** | Static, Bimodal, GShare, Tournament + правки peek/GHR/scoring |
| `harness/` | **~80%** | Цикл predict/update, метрики; интеграция с Spring `@Service` |
| `parser/` | **~85%** | Разбор строк, BOM, исключения на мусор |
| `api/` | **~60–70%** | Контроллер, DTO, CORS; валидация и тесты после ревью |
| `frontend/` | **~55–70%** | Страницы, графики, API-клиент; стили и мелкие багфиксы вручную |

---

## Журнал (кратко)

| Дата | Задача |
|------|--------|
| 2026-05-06 | Стартовая структура проекта, CI, первые заглушки |
| 2026-05-06–08 | Predictors, Harness, Parser, трассы, сравнительные тесты |
| 2026-05-08 | JaCoCo 0.8.14, REST edge cases, `PredictorControllerTest`, обновление `AI_USAGE.md` |

*AI-generated код не подлежит сдаче без понимания логики предсказателя и прогона тестов.*
