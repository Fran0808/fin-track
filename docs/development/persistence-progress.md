# Isolated backend validation and local initialization

Updated October 21, 2026. Scope: backend schema/persistence acceptance and local
setup tooling. No Android application changes, device installation, live email
ingestion or application-service restart.

## Implementation

- [Local initializer](../../scripts/setup/initialize-local.ps1): checks Git, Node 24,
  npm and Compose >=2 (Docker mode) or Java 21/PostgreSQL client tools (native mode).
  Creates `.env` only if absent; preserves existing values and never prints them.
  Missing configuration/short JWT secrets return nonzero. `-Plan` does nothing;
  UI dependency installation is an explicit option. No service/database operations.
- [Persistence runner](../../scripts/checks/test-persistence.ps1): creates its own
  temporary PostgreSQL cluster, loopback port, synthetic credentials and five
  databases. Enables all four opt-in JVM suites, disables email scheduling and
  runs Maven `verify`. Restores environment variables and stops/removes only its
  generated cluster. Failed shutdown preserves the directory for diagnosis.
- [Migration regression](../../scripts/migrations/test_ingestion_provenance.sql):
  requires an empty `fintrack_provenance_eval_` database, builds a synthetic legacy
  schema and applies the provenance migration twice. Verifies preservation of
  unknown historical ownership and text, accepted new references and rejected
  nonexistent user/transaction references. CI now runs it in its own database.

## Executed evidence

- Initial run on PostgreSQL 17.4/Java 22 passed the SQL regression and 246 backend
  tests without failures/errors/skips, including context and all persistence suites.
  The runner was then restricted to the documented Java 21 requirement.
- Java 21.0.10/PostgreSQL 17.4 run in PowerShell 7 passed the SQL regression and
  full Maven `verify`/backend packaging: 246 tests, zero failures/errors/skips.
  Suite breakdown: `AppTests` 1, classification 3, instruments 3, deployment 3;
  the remaining 236 are database-free tests. Temporary cluster shutdown and
  directory removal were confirmed.
- The final Windows PowerShell 5.1 run with Java 21.0.10 also passed the SQL
  regression, 246 tests (zero failures/errors/skips) and backend packaging.
  Cluster shutdown and directory removal were confirmed after both Java 21 runs.
- 18 script regression groups passed in PowerShell 7 and Windows PowerShell 5.1.
  Fixtures cover non-writing previews, template creation, existing-file
  preservation, private output, version rejection and optional npm failure.
- The initializer ran successfully against the existing local `.env` in Docker
  mode (Node 24.17.0, Compose 5.3.0) and native mode with Java 21 under Windows
  PowerShell 5.1. Existing configuration was preserved; npm installation was not
  requested. Provider credentials and database access were not tested.
- Documentation references passed in both shells, `git diff --check` passed,
  and Android application/delivery paths have no diff.

Actual JVM reports and packaged backend are in ignored `api/target/`. They are
local evidence; these generated outputs do not travel with a clone. Tests used
mocked external HTTP and synthetic financial fixtures. Temporary PostgreSQL
processes are separate from the installed service and the application database.

## Remaining boundaries

Hosted CI must run after the user commits/pushes these changes; local verification
does not prove GitHub execution. No production migration was applied. Google
consent, real synchronization, deployment and all Android acceptance remain outside
this scope. Forced termination of the runner can bypass its cleanup.

## Review and commit proposal

Initialization is already committed as `fec0361`; its missing tests/catalogue
were completed in `2dde5b5`. The remaining index was empty when inspected.
Use these three atomic commits in order. The user executes the commands from
the root; the agent has not committed or pushed.

### 1. Provenance migration regression

The SQL file includes its disposable-database requirements and assertions.

```powershell
git add scripts/migrations/test_ingestion_provenance.sql
git diff --cached --check
git commit -m "test(db): verify ingestion provenance migration"
```

### 2. Isolated PostgreSQL runner

The runner depends on the SQL regression above. Include its preview test and
catalogue/use/cleanup documentation; initializer blocks are already committed.

```powershell
git add scripts/checks/test-persistence.ps1 scripts/checks/test-scripts.ps1 scripts/README.md
git diff --cached --check
git commit -m "feat(tooling): add isolated PostgreSQL test runner"
```

### 3. CI migration regression

Enable the SQL regression in CI and publish the executed validation record,
deployment verification pointer and current checkpoint. These references are
added after both local tools exist in Git.

```powershell
git add .github/workflows/ci.yml docs/PROGRESS.md docs/README.md
git add docs/development/deployment.md docs/development/persistence-progress.md
git diff --cached --check
git commit -m "ci(api): run provenance migration regression"
```
