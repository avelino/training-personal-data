# Training Personal Data

A tool to collect and store personal training data from various sources. Currently supports Oura Ring data synchronization.

## Features

- Oura Ring data synchronization:
  - Daily Activity
  - Daily Sleep
  - Daily Readiness
  - Heart Rate
  - Workouts
  - Tags

## Requirements

- [Babashka](https://github.com/babashka/babashka#installation) (>= 1.12.196)
- PostgreSQL (>= 14)
- Oura Ring API Token

## Environment Variables

Create a `.env` file in the project root with:

```env
OURA_TOKEN=your_oura_ring_api_token
SUPABASE_HOST=your_database_host
SUPABASE_PORT=5432
SUPABASE_USER=postgres
SUPABASE_PASSWORD=your_database_password
SUPABASE_DB_NAME=your_database_name
```

## Installation

1. Clone the repository:
```bash
git clone https://github.com/avelino/training-personal-data.git
cd training-personal-data
```

2. Install dependencies:
```bash
bb prepare  # If needed for future dependencies
```

## Usage

### Sync Oura Ring Data

To sync data for a specific date range:
```bash
bb run:oura "2024-01-01" "2024-12-31"
```

### Running Tests

```bash
bb test
```

## Database Schema

The project creates the following tables:

- `ouraring_daily_activity`: Daily activity metrics
- `ouraring_daily_sleep`: Sleep analysis and metrics
- `ouraring_daily_readiness`: Daily readiness scores and contributors
- `ouraring_heart_rate`: Heart rate measurements
- `ouraring_workout`: Workout sessions
- `ouraring_tags`: User-defined tags

Each table includes:
- Primary data from the Oura API
- Raw JSON data for future reference
- Timestamp of data collection

## GitHub Actions

The project includes two GitHub Actions workflows:

1. **Tests** (`tests.yml`):
   - Runs on every push and pull request
   - Executes all project tests
   - Uses Ubuntu 24.04 and Babashka 1.12.196

2. **Cron** (`cron.yml`):
   - Runs every 3 hours
   - Syncs Oura Ring data for the last day
   - Can be manually triggered using workflow_dispatch
   - Requires environment secrets configuration

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [Oura Ring API](https://cloud.ouraring.com/docs/) for providing access to health data
- [Babashka](https://github.com/babashka/babashka) for the amazing Clojure scripting runtime