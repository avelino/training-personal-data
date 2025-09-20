# AGENT Guide – Training Personal Data

This document centralizes all knowledge about the project for developers, operators, and AI agents. It replaces scattered docs; treat this file as the single source of truth.

## 1) Purpose

Training Personal Data collects and analyzes personal health/fitness data, with first-class support for Oura Ring. It stores normalized daily metrics, produces Weekly Health Insights, and persists AI-generated analysis in a structured format for future querying and dashboards.

## 2) High-level Architecture

- Language/runtime: Clojure (Babashka)
- Database: PostgreSQL (Supabase-compatible)
- Sources: Oura Ring REST APIs (daily activity, sleep, readiness, heart rate, workouts, tags); Wahoo (workouts)
- Storage: Normalized tables (+ raw JSON)
- Insights: Weekly aggregation + GPT analysis persisted to DB
- Automation: GitHub Actions for periodic sync and weekly insights

```
Oura API → ETL (bb tasks) → Postgres (normalized + raw JSON)
                           → Weekly insights (SQL + GPT) → ouraring_weekly_insights
```

## 2.1) Architecture diagrams

### System overview

```mermaid
flowchart LR
  subgraph Oura Cloud
    OURA[Oura REST APIs]
  end

  subgraph ETL
    BB[bb tasks (Clojure/Babashka)]
    NORM[Normalize records]
  end

  subgraph DB[(PostgreSQL)]
    RAW[Raw JSON tables]
    NORMTBL[Normalized daily tables]
    WEEK[ouraring_weekly_insights]
  end

  OURA --> BB --> NORM --> DB
  DB <--> WEEK
```

### Generic ingestion pipeline

```mermaid
flowchart LR
  CFG[Endpoint Config (data)] --> PIPE[Generic Pipeline]
  PIPE -->|fetch-fn| FETCH[Fetch endpoint data]
  PIPE -->|normalize-fn| TRANSFORM[Normalize records]
  PIPE -->|ensure-table| SCHEMA[Create-if-not-exists]
  PIPE -->|extract-values-fn| SAVE[Batch save]
  SAVE --> DB[(PostgreSQL)]
```

### Weekly insights flow

```mermaid
flowchart LR
  RANGE[Date inside week] --> WEEKCALC[Compute Mon→Sun]
  WEEKCALC --> Q1[SQL: Sleep (avg, arrays)]
  WEEKCALC --> Q2[SQL: Readiness avg]
  WEEKCALC --> Q3[SQL: Activity avg]
  Q1 --> FORMAT[Format payload (units + availability)]
  Q2 --> FORMAT
  Q3 --> FORMAT
  FORMAT --> GPT[GPT Analysis]
  GPT --> PARSE[Parse text/table/cross-insight]
  PARSE --> SAVE[Persist insight (dates, numbers, JSONB)]
  SAVE --> WEEK[(ouraring_weekly_insights)]
```

## 3) Environment & Configuration

Create a .env file at repository root:

```env
OURA_TOKEN=your_oura_ring_api_token
WAHOO_TOKEN=your_wahoo_api_token
SUPABASE_HOST=your_database_host
SUPABASE_PORT=5432
SUPABASE_USER=postgres
SUPABASE_PASSWORD=your_database_password
SUPABASE_DB_NAME=your_database_name
OPENAI_API_KEY=your_openai_api_key
```

Wahoo OAuth auto-refresh (optional but recommended for CLI tasks):

```env
# If set, the runner will refresh an access token at startup using these:
WAHOO_CLIENT_ID=your_wahoo_client_id
WAHOO_CLIENT_SECRET=your_wahoo_client_secret
WAHOO_REFRESH_TOKEN=your_long_lived_refresh_token

# Optional path to persist rotated refresh_token automatically on each run
# (the Wahoo API rotates refresh tokens on refresh).
WAHOO_REFRESH_TOKEN_FILE=.secrets/wahoo_refresh_token
```

Configuration loader: `src/training_personal_data/config.clj` (reads env vars, validates presence).

## 4) Commands (Babashka tasks)

- Sync Oura Ring data for a range:
  - `bb run:oura "YYYY-MM-DD" "YYYY-MM-DD"`
- Sync Wahoo workouts for a date range (uses WAHOO_TOKEN env). If
  `WAHOO_REFRESH_TOKEN`, `WAHOO_CLIENT_ID`, and `WAHOO_CLIENT_SECRET` are set,
  the task will auto-refresh a new access token on startup and use it for the run.
  - `bb run:wahoo "YYYY-MM-DD" "YYYY-MM-DD"`
  - First run without a refresh token: provide an authorization code to bootstrap
    and persist the refresh token
    - Required envs for bootstrap: `WAHOO_CLIENT_ID`, `WAHOO_CLIENT_SECRET`,
      `WAHOO_AUTH_CODE`, `WAHOO_REDIRECT_URI` and optionally `WAHOO_REFRESH_TOKEN_FILE`
- Generate weekly insights (for a date inside the target week):
  - `bb -m training-personal-data.insights.week YYYY-MM-DD`
- Run tests:
  - `bb test`

## 5) Database Schema (summary)

Created automatically on demand. Core tables:

- `ouraring_daily_activity`
- `ouraring_daily_sleep`
- `ouraring_daily_readiness`
- `ouraring_heart_rate`
- `ouraring_workout`
- `ouraring_tags`
- `wahoo_workout`

Weekly insights output table:

- `ouraring_weekly_insights` with columns (subset shown):
  - `id text` (e.g., `week_2024-12-30`)
  - `week_start date`, `week_end date`, `week_range text`
  - `avg_sleep_score double precision`
  - `avg_sleep_duration double precision` (hours)
  - `avg_sleep_quality double precision` (percentage)
  - `avg_readiness_score double precision`
  - `avg_active_calories double precision`
  - `avg_activity_score double precision`
  - `gpt_analysis text`
  - `gpt_metrics_table jsonb` (structured table of metrics)
  - `gpt_cross_data_insight text`
  - `raw_data jsonb` (raw weekly aggregates for traceability)

Wahoo tables (subset):

- `wahoo_workout` with columns (subset shown):
  - `id text` (primary key)
  - `starts timestamp`, `created_at timestamp`, `updated_at timestamp`
  - `minutes integer`, `name text`, `plan_id text NULL`, `workout_token text NULL`, `workout_type_id integer`
  - `workout_summary jsonb` (structured per workout when available)
  - `raw_json jsonb` (raw workout record)

## 6) Weekly Insights – Rules & Implementation

Namespace: `src/training_personal_data/insights/week.clj`

- Week range: Monday → Sunday. The date passed to `-main` is any day within the target week; the system computes week_start (Monday) and week_end (Sunday).
- Sleep duration unit: Oura stores `total_sleep` in minutes (in our dataset). We
  - Exclude zeros from the average: `AVG(CASE WHEN total_sleep > 0 THEN total_sleep ELSE NULL END)`
  - Convert minutes → hours by dividing by 60: `... / 60 as avg_sleep_duration`
- Readiness & Activity: average scores/calories computed for the week; may be missing if no records.
- Types when saving weekly insight:
  - `week_start` and `week_end` are `java.sql.Date`
  - JSON fields are stored as JSONB with explicit `?::jsonb` casts in SQL
- English-only outputs (MDC): all logs, prompts, and terminal messages are in English.

Observability (sample events):

- `:date-range-calculation`, `:query-sleep-data-range`, `:individual-sleep-records`,
  `:sleep-data-retrieved`, `:readiness-data-retrieved`, `:activity-data-retrieved`,
  `:sleep-duration-analysis`, `:formatted-data-for-gpt`, `:db-save-insight`, `:complete`.

## 7) Prompting & GPT Integration

Namespaces:

- `training-personal-data.insights.prompt`: builds the prompt and calls the GPT API.
- `training-personal-data.insights.week`: constructs the input payload and saves outputs.

Prompt content:

- Explicit units (scores 0–100, sleep duration in hours, sleep quality %).
- Data availability flags (available/missing) to avoid hallucinations.
- Response structure requested:
  1) A concise summary of the week
  2) A metrics table: Metric | Value | Interpretation | Recommendation
  3) A cross-data insight paragraph

Persistence:

- Raw GPT text → `gpt_analysis`
- Parsed metrics table (when available) → `gpt_metrics_table` (JSONB)
- Cross-metric narrative → `gpt_cross_data_insight`
- Fallback behavior when GPT errors occur: save available data, log errors.

## 8) GitHub Actions

- `cron.yml`: syncs Oura data periodically (every 3 hours), supports manual dispatch.
- `weekly-insights.yml`: runs every Monday at 08:00 UTC; computes previous Sunday and runs weekly insights. Requires secrets:
  - `OPENAI_API_KEY`, `SUPABASE_DB_NAME`, `SUPABASE_HOST`, `SUPABASE_USER`, `SUPABASE_PASSWORD`

## 9) Testing

- Execute all tests: `bb test`
- Coverage includes:
  - Date range computation (Mon→Sun)
  - `safe-double` conversion (numbers, numeric strings, nil/empty/invalid → nil; zero → nil)
  - Formatting for GPT (units and availability flags)

## 10) Conventions (MDC)

- All user-facing text must be in English (logs, CLI output, prompts, comments intended for readers).
- Logging: structured maps with `:event` plus salient fields.
- SQL: cast parameter types explicitly when comparing with `timestamp` (e.g., `?::timestamp`).
- Persist JSON as JSONB with explicit `?::jsonb` casts.
- DB layer enforces casts automatically for common columns:
  - Timestamps: `starts`, `created_at`, `updated_at` → `?::timestamp`
  - JSON: `raw_json`, `workout_summary`, `raw_data`, `gpt_metrics_table`, and any `*_json` → `?::jsonb`

## 11) Troubleshooting

- Error: `column "week_start" is of type date but expression is of type character varying`
  - Cause: inserting string instead of SQL date
  - Fix: convert with `java.sql.Date/valueOf` before saving
- Error: `column "raw_data"/"gpt_metrics_table" is of type jsonb but expression is of type character varying`
  - Cause: string not cast to JSONB
  - Fix: use `?::jsonb` in SQL and provide valid JSON string
- Error: `column "workout_summary" is of type jsonb but expression is of type character varying`
  - Cause: providing a JSON string without JSONB cast
  - Fix: the DB layer already casts this column to `?::jsonb`; ensure the value is a valid JSON string
- Error: `operator does not exist: timestamp without time zone >= character varying`
  - Cause: comparing timestamp column with string parameter
  - Fix: cast parameters: `WHERE timestamp >= ?::timestamp AND timestamp <= ?::timestamp`
- Error: `column "starts" is of type timestamp without time zone but expression is of type character varying`
  - Cause: inserting ISO8601 string without cast
  - Fix: the DB layer now casts `starts`/`created_at`/`updated_at` to `?::timestamp`
- Sleep duration shows ~0 hours
  - Causes: zeros included in average; minutes not converted to hours
  - Fix: `AVG(CASE WHEN total_sleep > 0 THEN total_sleep ELSE NULL END)/60`

## 12) Contributing

- Branch: `feature/<short-name>`
- Run tests locally: `bb test`
- Ensure English-only text and structured logging
- Open PR with clear title/summary (see example in previous PR templates)

## 13) Roadmap (suggested)

- Add monthly insights and trend analysis
- Build lightweight dashboard (charts of scores/duration)
- Extend parsing of GPT outputs into more granular JSON schemas
- Add alerts (e.g., if sleep duration below threshold)

---
This AGENT guide is authoritative. If you change behavior, update this file in the same PR.

## Appendix A) Oura ingestion pipeline architecture

The Oura ingestion moved from near-duplicate endpoint code to a generic, data-driven pipeline. This removes duplication, centralizes behavior, and simplifies tests.

### A.1 Goals

- Eliminate copy/paste across endpoints (activity, sleep, readiness, tags, workouts, heart-rate)
- Single point for error handling, logging, batching, and persistence
- Configuration-as-data to add/modify endpoints without new code

### A.2 Design

```
Endpoint Config (data) → Generic Pipeline → DB
```

- Endpoint Config fields (example):
  - name: "activity"
  - table-name, columns, schema
  - fetch-fn (calls Oura API)
  - normalize-fn (map API record → normalized map)
  - extract-values-fn (normalized map → DB values vector)

- Generic pipeline stages:
  1) fetch (with token and date range)
  2) transform each record (normalize)
  3) ensure table exists (create-if-not-exists)
  4) save records (batch-friendly, consistent logging)

### A.3 Benefits

- Maintenance: changes to flow live in one place
- Testability: test pipeline once; endpoints test only config/transform
- Performance: supports batching/parallelization; connection reuse-friendly
- Consistency: uniform logging/events across endpoints

### A.4 Usage example (conceptual)

```clojure
(def activity-config
  {:name "activity"
   :table-name activity-db/table-name
   :columns activity-db/columns
   :schema activity-db/schema
   :fetch-fn activity-api/fetch
   :normalize-fn activity-api/normalize
   :extract-values-fn activity-db/extract-values})

(pipeline/execute-pipeline activity-config token start-date end-date db-spec)
```

### A.5 Migration status

- Generic pipeline available and integrated
- Legacy per-endpoint cores can be deleted once configs and tests are fully aligned

### A.6 Logging/observability (ingestion)

- `:endpoint-sync` events: start/process/complete
- Counts of processed records
- Structured error events with endpoint identifiers
