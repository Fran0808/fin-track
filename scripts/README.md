# Developer scripts

Run commands from the repository root in Windows PowerShell 5.1 or PowerShell 7
on Windows. Implementations live here; build-system wrappers remain in their
modules. Commands resolve repository paths from their script location.
The Android-module compatibility entry point and the retired shell notification
simulator have been removed. Use only the paths in this catalogue.

| Folder | Responsibility |
| --- | --- |
| `checks/` | Routine verification, script regressions and non-writing API probes |
| `setup/` | Local prerequisite and configuration preparation without service startup |
| `android/` | APK delivery and isolated device capture tests |
| `operations/` | Explicit operations that can access real user services/data |
| `assets/` | Derived branding asset generation |
| `migrations/` | Manual SQL migrations and their isolated regression fixture |
| `lib/` | Shared internal helpers |

PowerShell is the task runner. A Makefile would add another prerequisite and a
second command interface without adding isolation or verification guarantees.

| Entry point | Prerequisites | Behavior and effects |
| --- | --- | --- |
| `setup/initialize-local.ps1` | Git, Node 24/npm; Docker Compose v2 or later (default) or Java 21/PostgreSQL client (`-Mode Native`) | Creates `.env` only if absent, preserves existing values, checks required setting names/JWT length without printing secrets. No services or database access. Missing settings return nonzero after template creation. `-InstallUiDependencies` explicitly runs `npm ci`; `-Plan` has no effects. |
| `checks/test-persistence.ps1` | Java 21, installed PostgreSQL tools, cached Maven dependencies for `-Offline` | Creates its own temporary PostgreSQL cluster on a free loopback port, four suite databases and four SQL regression databases. Runs provenance, metadata and operation migration regressions and full Maven `verify`, disables email scheduling, restores environment and stops/removes only its own cluster. No application database/device access. `-PgBinPath` and `-JavaHome` select installed tools; `-Plan` does not execute them. |
| `checks/verify-all.ps1` | Java 21, Node 24, installed UI dependencies, Android SDK; wrapper dependencies available | Runs database-free backend tests, UI tests/lint/build, Android JVM tests and debug APK assembly. No installation or live ingestion. Nonzero on any selected failure. |
| `checks/test-scripts.ps1` | PowerShell | Parses scripts and exercises failure handling, command selection and mocked HTTP/command operations; exports branding only in a temporary repository. No real API, database or device. |
| `checks/test-docs.ps1` | PowerShell, Git | Checks local Markdown file links, rooted script references and `docs/*.md` paths in `.env.example`, including untracked files; does not check web availability or heading fragments. |
| `checks/test-api.ps1` | Running API; optional `FINTRACK_DEVICE_TOKEN` for the intended user | Checks anonymous rejection, invalid device rejection and optionally the supplied device credential. No database queries, financial writes or identity output. Does not prove ingestion works. |
| `operations/sync-emails.ps1` | Running API, intended user's `FINTRACK_WEB_JWT`, connected Google account | Default checks mailbox connection only. `-Sync -AllowFinancialWrites` imports real email and can write financial records; requires task authorization. Prints status/counts, not message samples or transactions. |
| `android/build-apk.ps1` | Java/SDK, SDK `aapt2`, Gradle dependencies | Builds debug APK; checks actual package/version against Gradle; copies to `android/build/deliveries/FinTrack.apk` by default and reports SHA-256. `-OutputPath` overrides destination. |
| `android/run-capture-tests.ps1` | Disposable booted AVD named `FinTrackCapture`, explicit `-Serial emulator-N`, SDK/ADB | Builds and installs isolated test APKs and executes persisted capture/lifecycle tests. Revokes test listener access on exit. Requires task authorization for installation; never part of default verification. |
| `android/test-capture-reboot.ps1` | Same disposable AVD, built isolated APKs, explicit `-Serial`, `-PythonPath` (Python 3), SDK/ADB | Installs only isolated artifacts; default reboots the emulator. `-Scenario doze` forces deep idle and optionally `-KillDuringIdle` terminates only the capture app's PID. Passively checks Room/WAL copies before explicit opening. Restores idle/battery/screen state for Doze and revokes listener access. `capture-reboot.py` is its standard-library helper; not routine verification. |
| `assets/export-brand-assets.ps1` | Windows/.NET System.Drawing, master logo | Regenerates the five derived web/Android PNG assets. Overwrites those assets; does not build or install. |
| `migrations/*.sql` | PostgreSQL client and explicitly confirmed target | Manual schema operations; back up real databases first. SQL regression files require empty disposable databases; `test_ingestion_provenance.sql` also checks the `fintrack_provenance_eval_` name. See domain/deployment guides. |
| `lib/common.ps1` | PowerShell | Shared exit-code and API-origin validation, not a standalone command. |

## Routine verification

For first-time local preparation:

```powershell
.\scripts\setup\initialize-local.ps1 -Plan
.\scripts\setup\initialize-local.ps1
# Optional dependency download/package lifecycle execution:
.\scripts\setup\initialize-local.ps1 -InstallUiDependencies
```

Supply configuration privately when the initializer reports missing names. It does
not generate/rotate credentials, validate OAuth or start the stack. Use `-Mode Native`
instead of the default Docker prerequisites when running the API with Maven.

```powershell
.\scripts\checks\verify-all.ps1 -Plan
.\scripts\checks\verify-all.ps1
.\scripts\checks\verify-all.ps1 -SkipAndroid
.\scripts\checks\verify-all.ps1 -Offline
.\scripts\checks\test-scripts.ps1
```

Install locked UI dependencies with `npm ci` in `ui` first. `-Offline` applies
to Maven and Gradle only; cached dependencies are required. `-SkipApi`, `-SkipUi`
and `-SkipAndroid` select modules; skipping everything fails rather than reporting
success. Full database integration remains the opt-in procedure in
[development](../docs/development/local.md#backend-verification), not part of this runner.

To reproduce all backend persistence checks without reusing any database:

```powershell
.\scripts\checks\test-persistence.ps1 -Plan
.\scripts\checks\test-persistence.ps1 -Offline
```

The persistence runner starts only a newly initialized temporary PostgreSQL process,
with separate disposable databases for provenance, transaction-metadata and operation migration
regressions, as well as the four backend suites. The metadata regression checks
repeatable column removal and preservation of money/ownership/idempotency. See
[metadata retirement](../docs/domain/transaction-metadata-retirement.md) for deployment order.
The operation regression verifies additive unknown defaults, unchanged historical
values, repeatability and the neutral-flow constraint for own-card repayments.
It never starts a Windows service or reads `.env`. It sets test URLs/credentials and
disables scheduling for its child process, then restores the previous environment.
Temporary synthetic data is removed after the process stops; if shutdown fails,
the directory is preserved and the command fails. Forcibly terminating PowerShell
can bypass cleanup. It does not install PostgreSQL or Java.

## Operational commands

Supply credentials privately as process environment variables, not literals in
shell history. Remove them from the process when finished. API tools accept
`-BaseUrl` as an HTTP(S) origin; remote origins require HTTPS. Loopback HTTP is
supported for local development. Error bodies are suppressed to avoid disclosure.

```powershell
.\scripts\checks\test-api.ps1
.\scripts\operations\sync-emails.ps1
# Only with task authorization for real ingestion:
.\scripts\operations\sync-emails.ps1 -Sync -AllowFinancialWrites
.\scripts\android\build-apk.ps1
# Only with task authorization for the named device:
.\scripts\android\build-apk.ps1 -Install -Serial DEVICE_SERIAL
# Only with task authorization for isolated emulator installation:
.\scripts\android\run-capture-tests.ps1 -Serial emulator-5554
```

The APK builder accepts `-SdkPath` and `-AdbPath`, requires an explicit serial
for installation, and fails on installation or installed-version verification
errors. Capture tests accept `-AdbPath` and `-SkipBuild`; their offline build
requires cached Gradle dependencies. Neither suite establishes current Yape
compatibility, physical-device reliability or successful backend synchronization.

When adding or moving a tool, update this catalogue, affected documentation and
the script regression suite. Keep operational commands out of ordinary tests.
