# FinTrack

Personal cashflow tracking from authorized wallet notifications and bank emails.

FinTrack brings incoming money, expenses and internal transfers into one dashboard.
Classify movements, associate them with cards or accounts, and explore where your
money goes. It tracks money moved, not live bank balances; internal transfers are
excluded from expenses. Listen Service is the repository name.

## Features

- **Notification capture:** Android processes payment notifications from explicitly
  supported packages, stores movements locally and synchronizes them with the API.
- **Bank email ingestion:** parse supported transaction confirmations through the
  email ingestion pipeline. Automatic synchronization is disabled in the environment template.
- **Cashflow dashboard:** period summaries, expense charts and transaction filters
  for dates, amounts, category, tags, payment channel and financial instrument.
- **Classification:** system and custom categories, subcategories, tags and personal notes.
- **Cards and accounts:** register financial instruments, associate movements and
  review assignment suggestions before confirming them.
- **CSV export:** export transactions matching the selected filters.
- **User-scoped access:** Google OAuth and JWT for the dashboard, with separate
  device credentials and QR pairing for Android synchronization.
- **Per-user deduplication:** repeated transaction hashes do not create another
  movement for the same user. Cross-source deduplication has limitations described below.

## Quickstart

The local Docker setup runs PostgreSQL 17 and the API. The React dashboard runs
separately with Vite. Commands below use PowerShell from the repository root.
For native API execution, see the [development guide](docs/development.md).

### 1. Prerequisites

- Docker Desktop with Linux containers, or Docker Engine with Compose v2.
- Node.js 24 and npm for the dashboard.
- Google OAuth credentials for dashboard sign-in.
- For Android development: Java 21, the Android SDK and a device or emulator
  supporting Android API 26 or later. See [Android instructions](android/AGENTS.md).

### 2. Configure the environment

Create `.env` only if it does not already exist:

```powershell
if (-not (Test-Path -LiteralPath .env)) {
    Copy-Item -LiteralPath .env.example -Destination .env
}
```

Edit the values using [.env.example](.env.example) as the reference:

| Variable | Local setup |
| --- | --- |
| `DB_PASSWORD` | Required by Compose. Preserve the existing password when reusing an initialized database. |
| `JWT_SECRET` | Required by Compose. Use a private random value of at least 32 bytes. |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Required by the current Compose configuration and Google sign-in. |
| `GOOGLE_REDIRECT_URI` | Register the exact callback URL in Google; the default is `http://localhost:8080/api/v1/auth/google/callback`. |
| `APP_SERVER_PUBLIC_URL` | Required by Compose. Use the API address reachable from Android; a physical phone needs your computer's LAN address. |
| `API_PORT` | Keep `8080` unless you also update the Vite proxy and callback URLs. |
| `GMAIL_SYNC_ENABLED` | Leave `false` until email access is configured and automatic ingestion is intended. |

Keep `.env`, tokens and financial data private. For OAuth return URLs, port mapping
and existing Docker volumes, see the [Docker setup guide](docker/README.md#setup).

### 3. Start the database and API

If you have an existing database, read [existing database volumes](docker/README.md#existing-database-volumes)
before starting a different Compose stack or changing its project name.

```powershell
docker compose config --quiet
docker compose up -d --build
docker compose ps
```

`config --quiet` validates configuration without printing resolved credentials.
Rebuild the API image after backend source changes.

### 4. Start the dashboard

In a separate terminal, from the repository root:

```powershell
cd ui
npm ci
npm run dev
```

Open [the dashboard](http://localhost:5173) and sign in with Google. Vite forwards
`/api` requests to the API at `localhost:8080`.

Adminer is optional: run `docker compose --profile tools up -d adminer` and open
[the database viewer](http://localhost:8081). Connection details are in the
[Docker guide](docker/README.md#optional-database-viewer).

### 5. Connect Android

Build the Android app using the [development guide](docs/development.md#dashboard-and-android-verification),
install it on your intended device, pair it using the dashboard's QR code and enable
notification access. For a physical phone, check that the configured API URL is
reachable from the device. See [notification capture and recovery](docs/android-notification-recovery.md)
for synchronization behavior and device verification.

## Updating an existing installation

Back up the intended database before schema changes. Older databases can retain a
fixed category constraint that rejects current catalog names; follow the
[transaction category migration](docs/development.md#transaction-category-migration).
Hibernate schema updates alone do not remove that legacy constraint.

For Docker deployments, rebuild the API with `docker compose up -d --build api`
after backend changes. For native execution, restart the API with the updated code.
The [Docker guide](docker/README.md) explains volume reuse and shutdown. Removing
volumes with `docker compose down -v` deletes the stored database.

## Project structure

| Path | Responsibility |
| --- | --- |
| `api/` | Java 21 / Spring Boot 4 API: authentication, ingestion, classification and analytics. |
| `android/` | Kotlin / Jetpack Compose app: notification capture, local storage and device pairing. |
| `ui/` | React / TypeScript / Vite dashboard. |
| `docker/` | Container builds and local deployment guide. |
| `scripts/` | PowerShell tools and manual SQL migrations. |
| `docs/` | Architecture, contracts, development and financial domain rules. |
| `.github/` | CI workflows and contribution templates. |

## Documentation and verification

Start with the [documentation index](docs/README.md). Detailed references:

- [Architecture](docs/architecture.md): service boundaries and ingestion flow.
- [Development](docs/development.md): local setup, module checks and test isolation precautions.
- [API contracts](docs/contracts.md): authentication, endpoints and client coordination.
- [Cashflow rules](docs/domain/cashflow.md): financial interpretation and deduplication.
- [Cards and accounts](docs/domain/financial-instruments.md): registration and assignment behavior.
- [CI configuration](.github/workflows/ci.yml): checks currently run in GitHub Actions.
- [Agent working agreements](AGENTS.md): repository and module instructions.

For dashboard changes, run `npm test`, `npm run lint` and `npm run build` from `ui/`.
Before backend context tests, select an isolated test database and disable email
scheduling as described in [backend verification](docs/development.md#backend-verification).

Review the [script limitations](docs/development.md#existing-scripts) before running
developer automation. `verify-all.ps1` does not cover every documented check or
isolate the backend database. `test-api.ps1` can select a real user and insert a
movement; email-sync scripts can import real financial data. They are not ordinary
unit tests. `build-apk.ps1` can also install an APK when requested through its options.

## Current limitations

- Capture depends on supported notification packages and bank email formats.
- Email and Android use different hashes; the same payment captured through both
  sources is not guaranteed to deduplicate.
- Registered products currently use PEN. Transactions do not carry a currency
  field, and no currency conversion is performed.
- Local backend context tests are not isolated by default; use a disposable database.
- The Compose setup is for local development. Public deployment requires HTTPS,
  public OAuth URLs and separate frontend hosting configuration.
