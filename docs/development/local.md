# Local development and verification

Commands use PowerShell and start at the repository root unless stated otherwise.
The [Docker guide](../../docker/README.md) covers container setup and existing volumes.

## Prerequisites and configuration

- Java 21 for the backend and Android CI target; use the Maven and Gradle wrappers.
- Node.js 24 and npm for the dashboard; install locked dependencies with `npm ci`.
- PostgreSQL for native backend execution, or Docker Desktop with Linux containers.
- Android SDK levels declared in [Gradle](../../android/app/build.gradle.kts), with platform-tools for authorized device diagnostics.

### Values supplied outside the repository

The repository cannot establish installed tools, valid Google credentials, the
authorized database/volume, phone address or firewall reachability. Confirm these
for the intended environment; never infer them from template values or old logs.
Keep credentials outside Git and shared output.

Configure a Google OAuth application and its consent screen/permitted users,
enable Gmail API for email ingestion, and register `GOOGLE_REDIRECT_URI` exactly.
The [OAuth service](../../api/src/main/java/com/store/api/service/auth/GoogleOAuthService.java)
requests OpenID, profile, email and `gmail.readonly` access. Actual consent and
provider access require verification in that Google project; no credentials or
successful external configuration are supplied by the repository.

Select the confirmed local database or existing Compose volume, supply its private
password and a JWT secret of at least 32 bytes, and keep email scheduling disabled.
For Android, supply the device-reachable API address and SDK location through the
supported environment/local configuration. Do not commit workstation-specific paths.

Create `.env` only if it does not already exist:

The [local initializer](../../scripts/setup/initialize-local.ps1) checks Git,
Node.js 24/npm and Docker Compose v2 or later, creates the template only when absent and
reports missing setting names without printing their values:

```powershell
.\scripts\setup\initialize-local.ps1 -Plan
.\scripts\setup\initialize-local.ps1
```

Use `-Mode Native` to check Java 21 (respecting `JAVA_HOME`, as Maven does) and
PostgreSQL client tools instead of Docker. Use `-InstallUiDependencies` only when
you intend to run `npm ci`, including package downloads/lifecycle scripts. The
initializer never starts services, migrates data, changes Android or rotates
secrets. Missing settings return nonzero after initial template creation; fill
them privately and rerun. It does not load `.env` into the launch environment.

For manual template creation instead:

```powershell
if (-not (Test-Path -LiteralPath .env)) {
    Copy-Item -LiteralPath .env.example -Destination .env
}
```

Configure the required values from [.env.example](../../.env.example). Preserve an
existing database password and JWT secret. Do not print resolved secrets or commit
`.env`. `APP_SERVER_PUBLIC_URL` must be reachable from the Android device; its
`localhost` refers to the phone, not the development computer.

The API uses `APP_SERVER_PUBLIC_URL` for the web pairing field and QR payload.
Android builds use the same process environment variable first, then the repository
`.env` value, then legacy `BASE_URL` in `android/local.properties`, and finally a
loopback development fallback. No LAN address is hardcoded as a shared default.
Set the actual reachable API address before building an APK; changing `.env` does
not modify an APK already installed or a pairing already saved on a phone. Use
**Cambiar dirección del servidor** for an existing pairing. Placeholder addresses
in manual fields are examples, not connection settings.

The running backend retains its startup configuration. After updating `.env`, load
it into the backend's launch environment and restart the backend when authorized;
then reopen the web pairing dialog to obtain the updated default and QR. A new APK
uses its compiled fallback only when no saved pairing address takes precedence.

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

For a fully isolated local run, use installed PostgreSQL tools with
[the persistence runner](../../scripts/checks/test-persistence.ps1):

```powershell
.\scripts\checks\test-persistence.ps1 -Plan
.\scripts\checks\test-persistence.ps1 -Offline
# Select installed tools explicitly when needed:
# .\scripts\checks\test-persistence.ps1 -PgBinPath 'C:\Program Files\PostgreSQL\17\bin' -JavaHome 'C:\path\to\jdk-21'
```

This creates a new temporary cluster and five databases: four for the JVM suites,
and one for [the incremental migration regression](../../scripts/migrations/test_ingestion_provenance.sql).
It enables every opt-in suite, disables email scheduling, restores its environment
variables and stops/removes only its own cluster on exit. It never loads `.env`,
reuses an existing server or installs tools. Java 21 and Maven dependencies must
already be available; omit `-Offline` to permit Maven dependency downloads.
See [the catalogue](../../scripts/README.md) for cleanup/failure behavior.

For manually managed disposable databases, use the procedures below.

Cards and accounts have an opt-in PostgreSQL integration test. Its disposable
database setup and schema changes are documented in
[cards and accounts](../domain/financial-instruments.md#schema-preparation-and-verification).

`AppTests` now runs only when `APP_TEST_DATABASE_URL` names a disposable database
matching `jdbc:postgresql://localhost:<port>/fintrack_app_eval_<suffix>`. It explicitly
disables scheduling and uses `create-drop`: never point it at financial data.
`DeploymentPersistenceTest` requires a separate, empty
`DEPLOYMENT_TEST_DATABASE_URL` matching
`jdbc:postgresql://localhost:<port>/fintrack_deployment_eval_<suffix>`. It initializes
the versioned schema and starts the production profile with fake OAuth settings
and `ddl-auto=validate`. Reusing a populated database fails deliberately.

Both use `TEST_DB_USER` (default `postgres`) and `TEST_DB_PASSWORD`, independently
of normal application database credentials. Other persistence suites retain their
documented opt-in database settings. Never reuse real database names for tests.
For ordinary unit/controller checks without a database, exclude context suites
explicitly; external HTTP clients are mocked:

```powershell
Push-Location api
try {
    .\mvnw.cmd test '-Dtest=*Test,!*PersistenceTest,!AppTests' '-Dmail.sync.enabled=false' '-Dspring.jpa.hibernate.ddl-auto=validate' '-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:1/fintrack_review_no_database'
} finally {
    Pop-Location
}
```

After creating the two disposable databases, set their opt-in URL variables and
test credentials, then run `./mvnw.cmd verify` from `api` for context and deployment
checks as well. To include instruments and classification, configure their separate
opt-in URLs and credentials from the domain guides too; running `verify` alone
does not enable those suites. CI provisions all four disposable databases and
enables all four suites. The deployment test covers
schema validation, concurrent retries, overlapping batches and monotonic cursors.
See [production deployment](deployment.md) for setup and runtime limitations.
The Maven wrapper uses the configured Java installation; first-time dependency
downloads may require network access.

## Transaction category migration

Older databases may still have `transactions_category_check`, which only accepts
the former fixed enum. Hibernate schema updates do not remove this legacy check.
The [manual migration](../../scripts/migrations/20261008_transaction_categories.sql)
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

The [SQL regression test](../../scripts/migrations/test_transaction_categories.sql)
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

## Branding assets

The shared logo master lives in [assets/branding](../../assets/branding).
Run `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/assets/export-brand-assets.ps1` from the repository
root on Windows to regenerate the web logo, favicon, Apple touch icon and Android
launcher assets. The script uses System.Drawing, preserves the logo's proportions
and transparency, and adds safe padding for adaptive launcher masks.
It overwrites only the derived branding assets; keep the master image as the source.
Web components use `ui/src/assets/fintrack-mark.png`; browser icons live in
`ui/public/`, and Android assets live in `android/app/src/main/res/`.
Android requires API 26 or higher and uses one adaptive launcher icon for both
standard and round launcher shapes. Its foreground is exported once, with no
legacy icon copies per density. The adaptive XML and background stay unchanged
when replacing the logo; only the two Android PNG assets need regeneration.

## Existing scripts

All developer script implementations are in [scripts/](../../scripts/README.md).
The catalogue records prerequisites, effects and supported commands.
Scripts are grouped by purpose under `checks/`, `android/`, `operations/`,
`assets/`, `lib/` and `migrations/`. Maven and Gradle wrappers stay with their
build files. There is no longer an Android-module compatibility entry point.

From the repository root, preview or run routine checks:

```powershell
.\scripts\checks\verify-all.ps1 -Plan
.\scripts\checks\verify-all.ps1
.\scripts\checks\test-scripts.ps1
```

The default verifier uses the Maven wrapper with context/persistence suites
excluded, disabled email scheduling and an unreachable database URL. It includes
UI tests, lint and build, plus Android unit tests and APK assembly. Integration,
real email ingestion and device installation remain separate explicit operations.
There is no Make dependency. Use the module skip flags for focused changes.

CI behavior is defined in [.github/workflows/ci.yml](../../.github/workflows/ci.yml).
It runs module tests/builds, frontend lint, Docker checks and Windows script
regressions. Markdown-only changes skip application CI but run the separate
[documentation reference workflow](../../.github/workflows/docs.yml). See
[project progress](../PROGRESS.md) for current evidence and remaining work.
