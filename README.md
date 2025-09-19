# Training Personal Data

Turn your wearable data into actionable health insights.

This project syncs Oura Ring data into PostgreSQL and generates Weekly Health Insights using SQL + GPT. It stores normalized metrics and structured AI analysis so you can query trends, build dashboards, and automate feedback loops.

## What it does

- Sync Oura Ring: Activity, Sleep, Readiness, Heart Rate, Workouts, Tags
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

4) Run tests:

```bash
bb test
```

## Learn more

See the full project guide with architecture, workflows, schemas, troubleshooting, and conventions:

- AGENT Guide: [AGENT.md](./AGENT.md)
