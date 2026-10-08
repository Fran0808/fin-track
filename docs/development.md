# Local development and verification

Commands use PowerShell and start at the repository root unless stated otherwise.
The [Docker guide](../docker/README.md) covers container setup and existing volumes.

## Prerequisites and configuration

- Java 21 for the backend and Android CI target; use the Maven and Gradle wrappers.
- Node.js 24 and npm for the dashboard; install locked dependencies with `npm ci`.
- PostgreSQL for native backend execution, or Docker Desktop with Linux containers.
- Android SDK levels declared in [Gradle](../android/app/build.gradle.kts), with platform-tools for authorized device diagnostics.

Create `.env` only if it does not already exist:

```powershell
if (-not (Test-Path -LiteralPath .env)) {
    Copy-Item -LiteralPath .env.example -Destination .env
}
```

Configure the required values from [.env.example](../.env.example). Preserve an
existing database password and JWT secret. Do not print resolved secrets or commit
`.env`. `APP_SERVER_PUBLIC_URL` must be reachable from the Android device; its
`localhost` refers to the phone, not the development computer.

Compose loads `.env`; native Spring Boot does not load it automatically. Configure
the IDE's environment, or load the simple `KEY=value` entries into the current
PowerShell process before starting the API:

```powershell
foreach ($configurationLine in Get-Content -LiteralPath .env) {
    if ($configurationLine -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
        $configurationName = $Matches[1]
        $configurationValue = $Matches[2].Trim().Trim('"').Trim("'")
        [Environment]::SetEnvironmentVariable($configurationName, $configurationValue, 'Process')
    }
}
```

This snippet matches the template's simple values. It does not evaluate shell
expressions, interpolate variables or parse inline comments. Do not run it in a
terminal used for isolated tests: use an explicitly configured test environment.

## Native local execution

For an installed PostgreSQL server, point `DB_HOST`, `DB_PORT` and `DB_NAME` at the
intended local database. Alternatively, start only Compose PostgreSQL as described
in the Docker guide. Do not run two database servers on the same port.

After loading the environment, start the backend:

```powershell
Push-Location api
try {
    .\mvnw.cmd spring-boot:run
} finally {
    Pop-Location
}
```

In another terminal, start the dashboard:

```powershell
Push-Location ui
try {
    npm ci
    npm run dev
} finally {
    Pop-Location
}
```

The defaults are API `8080`, Vite `5173` and PostgreSQL `5432`. Vite's proxy targets
`localhost:8080`; changing that port requires updating the proxy and relevant
OAuth/server URLs. Use the desktop browser at `http://localhost:5173`.
For Android, pair using an API address reachable from the device and check network
and firewall configuration. Check installed version name and code before diagnosis.

## Backend verification

Cards and accounts have an opt-in PostgreSQL integration test. Its disposable
database setup and schema changes are documented in
[cards and accounts](domain/financial-instruments.md#schema-preparation-and-verification).

The current `AppTests` starts a full context without a dedicated test profile.
Default application configuration can update the database schema and enable email
scheduling. Never assume `clean test` is isolated from the local financial database.

Until test isolation is implemented, select a separate test database with the
expected schema, explicitly disable scheduling and use `ddl-auto=validate` to
avoid schema changes during verification. These flags do not create or isolate
the database; it must be configured first. Unit tests mock external clients.

```powershell
Push-Location api
try {
    .\mvnw.cmd clean test '-Dmail.sync.enabled=false' '-Dspring.jpa.hibernate.ddl-auto=validate'
} finally {
    Pop-Location
}
```

The Maven wrapper uses the configured environment and Java installation. CI has a
disposable PostgreSQL service; local commands do not yet reproduce that isolation.
First-time dependency downloads may require network access.

## Transaction category migration

Older databases may still have `transactions_category_check`, which only accepts
the former fixed enum. Hibernate schema updates do not remove this legacy check.
The [manual migration](../scripts/migrations/20261008_transaction_categories.sql)
drops only that check and expands `category` to 100 characters to match the catalog.
It preserves existing rows and category values, runs in a transaction with bounded
lock/statement timeouts, and can be repeated safely.

Confirm the intended database and back it up before applying the migration. With
PostgreSQL connection credentials configured securely, run from the repository root:

```powershell
psql -X -v ON_ERROR_STOP=1 -d finance_db -f scripts/migrations/20261008_transaction_categories.sql
```

Replace `finance_db` with the confirmed target and supply host/user options as needed.
Deploy the updated backend to enable catalog validation and safe error messages.
The updated dashboard sends an empty category to explicitly remove an assignment.
Do not restore the old check after new category values have been saved; that rollback
would reject those values.

The [SQL regression test](../scripts/migrations/test_transaction_categories.sql)
creates a synthetic `public.transactions` table. Run it only in a newly created,
disposable database with no financial data. It checks the original failure,
idempotency, preservation of existing values, custom names and unclassified rows.

## Dashboard and Android verification

From `ui`, run `npm test`, `npm run lint` and `npm run build` after application
changes. From `android`, run `./gradlew.bat testDebugUnitTest assembleDebug` on
Windows, or `./gradlew testDebugUnitTest assembleDebug` on Linux.

Run checks for the affected modules and synchronize clients when contracts change.
Documentation-only changes need link, consistency and diff checks. When delivery
includes running the application, verify that the current process or APK contains
the changed code. Report compilation, installation and runtime checks separately.

## Existing scripts

These scripts are existing tools, with limitations that have not been fixed by
this documentation update:

| Script | Current behavior and limitation |
| --- | --- |
| [verify-all.ps1](../scripts/verify-all.ps1) | Runs backend tests, UI tests/build and Android unit tests. It uses system Maven, does not isolate the backend database, and omits frontend lint and APK assembly. |
| [build-apk.ps1](../scripts/build-apk.ps1) | Builds/copies an APK and optionally installs it. It reports SHA-256 but not verified APK version metadata; installation failure can still end with a success message. |
| [test-api.ps1](../scripts/test-api.ps1) | Can select the first real user's token, insert a transaction and attempt cleanup using a fixed local password. Reported failures do not reliably determine its exit code. Do not use it as a safe default test. |
| [test-email-sync.ps1](../scripts/test-email-sync.ps1) | Calls live email ingestion and can write financial records. Its current requests do not supply the required web JWT. It is an operational script, not an isolated parser test. |
| [test-yape-notification.ps1](../scripts/test-yape-notification.ps1) | Clears logcat and posts a shell notification; it does not reproduce an authorized Yape package notification. Worker log presence is not proof of successful synchronization. |

CI behavior is defined in [.github/workflows/ci.yml](../.github/workflows/ci.yml).
It runs module tests/builds and Docker checks, but currently omits frontend lint
and skips workflows for Markdown-only changes. Script alignment, isolated tests
and a read-only environment diagnostic command belong to the next stage.
