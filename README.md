# Training Personal Data

Turn your wearable data into actionable health insights.

This project syncs Oura Ring and Wahoo data into PostgreSQL and generates Weekly Health Insights using SQL + GPT. It stores normalized metrics and structured AI analysis so you can query trends, build dashboards, and automate feedback loops.

## What it does

- Sync Oura Ring (activity, sleep, readiness, heart rate, workouts, tags)
- Sync Wahoo workouts
- Normalize and store daily metrics (+ raw JSON)
- Compute Monday→Sunday weekly insights
- Generate, parse, and persist AI analysis (text + JSONB)
- Automate weekly insights via GitHub Actions


## Quick start

1) Set environment variables in a `.env` file (DB + Oura + OpenAI)
2) Sync data for a date range:

```bash
bb run:oura "2024-01-01" "2024-12-31"
```

3) Generate weekly insights (pass any date within the week):

```bash
bb -m training-personal-data.insights.week 2024-12-30
```

3) Sync Wahoo workouts (requires Wahoo OAuth client + tokens):

```bash
bb run:wahoo "2024-09-01" "2024-09-30"
```

4) Run tests:

```bash
bb test
```

## Learn more

See the full project guide with architecture, workflows, schemas, troubleshooting, and conventions:

- AGENT Guide: [AGENT.md](./AGENT.md)
