# Production deployment

Use the `prod` Spring profile. Development Compose defaults intentionally permit
local HTTP and schema updates; they are not a production configuration.

## Required environment

Configure private environment variables through the hosting provider. Never commit
the resolved values. See [.env.example](../../.env.example) for local names.

| Variable | Production requirement |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DATABASE_JDBC_URL` | PostgreSQL JDBC URL for the confirmed target, with provider-required TLS options, for example `jdbc:postgresql://host:5432/fintrack?sslmode=verify-full`. |
| `DB_USER`, `DB_PASSWORD` | Dedicated application role and its password; restrict network access to the API. |
| `JWT_SECRET` | Private random secret of at least 32 UTF-8 bytes; required in all environments. Preserve an existing secure secret to preserve sessions. |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Credentials for the production Google OAuth application; configure the consent screen and permitted users/scopes. |
| `APP_SERVER_PUBLIC_URL` | Public HTTPS API origin, such as `https://fintrack.example.com`. |
| `GOOGLE_REDIRECT_URI` | Exact registered URL, such as `https://fintrack.example.com/api/v1/auth/google/callback`. |
| `FRONTEND_SUCCESS_URL` | `https://fintrack.example.com?auth=google_connected` |
| `FRONTEND_ERROR_URL` | `https://fintrack.example.com?auth=google_error` |
| `CORS_ALLOWED_ORIGINS` | Explicit HTTPS origins separated by commas; include `https://fintrack.example.com`, without a trailing slash, path or wildcard. |
| `GMAIL_SYNC_ENABLED` | `false` until intentional real ingestion is authorized. |
| `GMAIL_SYNC_BATCH_SIZE` | Gmail page size, 1–500, default 20; every matching page is visited. |
| `APP_BUSINESS_ZONE` | `America/Lima` by default, used for calendar-period comparisons. |
| `DB_POOL_SIZE` | Default 5 connections per API instance; fit this to the database quota. |
| `PORT` / `SERVER_PORT` | HTTP listening port; `PORT` takes precedence. |

The production profile refuses missing credentials, insecure public URLs, wildcard
CORS and schema modes other than `validate`. It disables SQL statement output and
uses secure, HttpOnly, SameSite=Lax session cookies. Startup validation supplements
deployment review; it cannot verify provider credentials or connectivity.

## HTTPS and browser sessions

Serve the built dashboard and `/api` through **one public HTTPS origin**, forwarding
API requests and the Google callback to the backend. Serve `ui/dist` with SPA
fallback. Preserve the session cookie and avoid logging query strings or request
bodies on authentication routes. Keep the API's internal port private behind the
proxy, configure request/time limits there and trust forwarded headers only from
that proxy.

OAuth state lasts ten minutes and is bound to the initiating browser. The callback
places a single-use code in the URL fragment; the dashboard removes it immediately
and exchanges it within sixty seconds using the same session. The JWT is returned
in JSON and retained by the existing dashboard Bearer-token mechanism. Deploy the
backend and dashboard together; the old `?token=` callback is no longer accepted.

Start with one API instance. OAuth sessions are currently held in memory: restarts
interrupt pending logins. Multiple replicas need shared sessions or sticky routing
before enabling them. No cloud deployment or real Google sign-in is performed by
the repository tests.

## Database preparation

Production validates the schema and never alters it automatically. Back up an
existing database and confirm its host, database and role before applying SQL.

For a **new empty database only**, initialize all current tables and indexes with
[the initial schema](../../scripts/migrations/20261014_empty_database.sql):

```powershell
psql -X -v ON_ERROR_STOP=1 -d fintrack -f scripts/migrations/20261014_empty_database.sql
```

Provide the confirmed host/user and credentials securely, for example through
PostgreSQL's password file. The script runs transactionally and deliberately fails
if an application table already exists. Do not run it against an existing installation.

For an existing installation, retain all rows and apply only needed incremental
changes: [category migration](local.md#transaction-category-migration),
[classification rules](../domain/classification.md) and
[financial instruments](../domain/financial-instruments.md#schema-preparation-and-verification).
Do not temporarily enable Hibernate `update` in production to bypass validation.
Apply the [ingestion provenance migration](../../scripts/migrations/20261021_ingestion_provenance.sql)
before deploying this backend to an existing validated schema. It adds nullable
user references to raw logs and processed-email markers, and a transaction reference
to raw logs. It preserves rows and existing hashes without guessing historical
ownership. New writes populate these references; historical nulls remain unknown.
The script is repeatable for the expected legacy schema; it does not repair
pre-existing columns with incompatible types or missing constraints. No migration
is applied by the agent as part of ordinary verification.
Hibernate validation does not inspect every index/check: verify the unique
`idx_tx_hash_user` index and absence of the old `transactions_category_check` too.
The new code uses user-scoped processed-email keys in the existing column; old
markers remain and emails can be re-read once, with movement hashes preserved.

## Reliability and limits

Authenticated email ingestion requires that user's Google authorization. Global
IMAP settings are retained for legacy tooling, with no authenticated fallback.
Gmail pages are processed incrementally, with explicit connection/read timeouts.
Failed downloads, malformed bodies or failed persistence prevent cursor advancement;
already committed movements remain safe to retry. Cursor advancement is monotonic.
Email parsing and hashing use the full body; only the optional stored diagnostic
copy is capped at 65536 characters, so large HTML templates remain ingestible.
Unsupported formats can still be marked processed and require controlled reprocessing
when support is added. First sync of a large mailbox can be slow; manual sync is
currently synchronous and the response collects saved movement summaries.

PostgreSQL transaction-level advisory locks serialize equal user/hash ingestion.
Batch locks are acquired in stable order. The existing unique index remains the
final constraint. Input and export limits are in [the API contract](../architecture/contracts.md).
Cross-source deduplication still depends on matching hashes; this change does not
merge different email and Android identities automatically.

## Verification and rollout

Run [the documented checks](local.md#backend-verification) against disposable
databases. `DeploymentPersistenceTest` applies the empty schema and starts a
production-profile context with fake credentials, validating concurrent retries,
tenant isolation, batches and cursor updates. CI provisions fresh databases for
this test, `AppTests`, instruments and classification, each in its own disposable
database. Reusing the deployment test schema intentionally fails initialization.

Build the backend image from `docker/Dockerfile.api` and dashboard with `npm run
build`. The image uses Java 21, America/Lima and a heap capped at 60% of available
container memory; allow additional memory for threads and the JVM. Set actual
container resource limits in the provider.

After deployment, verify Google login/replay rejection, two-user isolation and a
synthetic device sync on the actual HTTPS domain. Authorize any real ingestion
separately, then verify it before enabling the scheduler. Keep database backups and
test restoration. Reverse-proxy behavior, provider TLS, real Google consent and
phone connectivity require checks on the selected host.
