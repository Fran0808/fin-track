# FinTrack local Docker environment

The root `docker-compose.yml` is the only Compose entry point. It runs
PostgreSQL 17 and the Java 21 API. Adminer is optional. React runs locally
with Vite; Android runs on a device or emulator.

This configuration is for local development. Public deployment requires
HTTPS, public OAuth URLs, and separate frontend hosting configuration.

## Branding and compatibility

FinTrack is the product name. Existing Docker container and volume identifiers
are retained for compatibility. Android also retains its application ID,
`wallet_pulse_db` database, and `wallet_pulse_pairing_prefs` preferences. Device
tokens keep the `wp_dev_` prefix. These internal names do not affect the displayed
brand and must not be replaced without planning a migration.

The Google OAuth consent screen name is managed separately in Google Cloud.
Changing the displayed brand does not require rotating OAuth credentials.

## Setup

Install Docker Desktop with Linux containers and Node.js 24 for the frontend.
Run all Docker commands below from the repository root.

If `.env` does not exist, copy `.env.example` to `.env`. Never overwrite an
existing `.env` or commit its credentials. Configure:

- `DB_PASSWORD`: retain the existing password when reusing a database volume.
  Changing this variable does not reset credentials in an initialized database.
- `JWT_SECRET`: a private random value of at least 32 bytes. Changing it
  invalidates existing login tokens.
- `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`: Google OAuth credentials.
- `GOOGLE_REDIRECT_URI`: must exactly match an authorized redirect URI in Google.
- `APP_SERVER_PUBLIC_URL`: the API address reachable from Android. For a physical
  phone on the same network, use the computer's LAN IP and published API port,
  such as `http://192.168.1.50:8080`. Check Windows Firewall if unreachable.
- `FRONTEND_SUCCESS_URL` and `FRONTEND_ERROR_URL`: browser return URLs. Defaults
  target Vite on `localhost:5173` for a browser on the development computer.

The API receives `.env` at runtime. Compose overrides `DB_HOST`, `DB_PORT`, and
`SERVER_PORT` inside the container to use `postgres:5432` and API port `8080`.
PostgreSQL and Adminer are published only on the computer's loopback interface.
The API is published on host interfaces so Android can reach it.

Keep `API_PORT=8080` with the current Vite proxy. Changing it also requires
updating `ui/vite.config.ts`, the public API URL, and OAuth callback URLs.
`GMAIL_SYNC_FIXED_DELAY_MS` controls the email scheduler interval;
`GMAIL_SYNC_CRON` is not supported. The template disables automatic email sync;
enable it deliberately after configuring email access.

## Existing database volumes

The root Compose retains the `postgres_data` volume key and original PostgreSQL
container name. With the same project name, it reuses the original root stack's
volume. No data migration or deletion is performed automatically.

If you previously used `docker/docker-compose.yml`, inspect existing resources
before starting the consolidated stack:

```powershell
docker ps -a --format '{{.Names}} {{.Label "com.docker.compose.project"}}'
docker volume ls
docker inspect walletpulse-postgres --format '{{json .Mounts}}'
```

Use the project label and mounted volume to identify the correct database.
If the old project was `docker` with volume `docker_postgres_data`, stop its old
containers before starting the new stack and set `COMPOSE_PROJECT_NAME=docker`
in `.env` to reuse that project's volume. For a custom project name, retain
that exact name instead. Do not start both stacks against the same data.
Back up important data before changing an existing deployment. Never use
`docker compose down -v` unless you intend to delete the database.

## Run the backend and database

```powershell
docker compose config --quiet
docker compose up -d --build
docker compose logs -f api
```

In a separate terminal, run the frontend:

```powershell
cd ui
npm ci
npm run dev
```

Open `http://localhost:5173`. Vite forwards `/api` requests to `localhost:8080`.
The API image contains compiled code; rebuild it after backend source changes.

To debug the API in an IDE instead, start only PostgreSQL:

```powershell
docker compose up -d postgres
```

Stop any running API container first with `docker compose stop api` to free port
8080. Configure the IDE to load `.env`; Spring Boot does not automatically load
this dotenv file when running outside Compose.

## Optional database viewer

```powershell
docker compose --profile tools up -d adminer
```

Open `http://localhost:8081`, select PostgreSQL, use `postgres` as the server,
and enter the database name and credentials from `.env`.

## Status and shutdown

```powershell
docker compose --profile tools ps
docker compose logs -f api
docker compose --profile tools down
```

The shutdown command preserves the database volume.

## Verification

`docker compose --profile tools config --quiet` checks configuration without
printing resolved secrets. Avoid sharing unredacted `docker compose config`
output. `docker compose build api` verifies image construction. CI runs both
checks with dummy credentials; module tests remain in their existing CI jobs.
Image construction alone does not verify OAuth, email access, or Android pairing.
