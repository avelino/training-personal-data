# Training Personal Data

A data collection and analysis tool for personal health and fitness tracking, currently supporting Oura Ring integration.

## Features

- Automated data collection from Oura Ring API
- Data storage in Supabase database
- Scheduled data syncing via GitHub Actions

## Requirements

- [Babashka](https://babashka.org/) - A Clojure scripting runtime
- Oura Ring API token
- Supabase database instance

## Environment Variables

### Required
- `OURA_TOKEN` - Your Oura Ring API access token

### Optional (for Supabase storage)
- `SUPABASE_HOST` - Supabase database host
- `SUPABASE_USER` - Database username
- `SUPABASE_PASSWORD` - Database password
- `SUPABASE_PORT` - Database port
- `SUPABASE_DB_NAME` - Database name