# Developer scripts

Run commands from the repository root in Windows PowerShell 5.1 or PowerShell 7
on Windows. Implementations live here; build-system wrappers remain in their
modules. Commands resolve repository paths from their script location.
The Android-module compatibility entry point and the retired shell notification
simulator have been removed. Use only the paths in this catalogue.

| Folder | Responsibility |
| --- | --- |
| `checks/` | Routine verification, script regressions and non-writing API probes |
| `android/` | APK delivery and isolated device capture tests |
| `operations/` | Explicit operations that can access real user services/data |
| `assets/` | Derived branding asset generation |
| `migrations/` | Manual SQL migrations and their isolated regression fixture |
| `lib/` | Shared internal helpers |

PowerShell is the task runner. A Makefile would add another prerequisite and a
second command interface without adding isolation or verification guarantees.

| Entry point | Prerequisites | Behavior and effects |
| --- | --- | --- |
| `checks/verify-all.ps1` | Java 21, Node 24, installed UI dependencies, Android SDK; wrapper dependencies available | Runs database-free backend tests, UI tests/lint/build, Android JVM tests and debug APK assembly. No installation or live ingestion. Nonzero on any selected failure. |
| `checks/test-scripts.ps1` | PowerShell | Parses scripts and exercises failure handling, command selection and mocked HTTP/command operations; exports branding only in a temporary repository. No real API, database or device. |
| `checks/test-docs.ps1` | PowerShell, Git | Checks local Markdown file links, rooted script references and `docs/*.md` paths in `.env.example`, including untracked files; does not check web availability or heading fragments. |
| `checks/test-api.ps1` | Running API; optional `FINTRACK_DEVICE_TOKEN` for the intended user | Checks anonymous rejection, invalid device rejection and optionally the supplied device credential. No database queries, financial writes or identity output. Does not prove ingestion works. |
| `operations/sync-emails.ps1` | Running API, intended user's `FINTRACK_WEB_JWT`, connected Google account | Default checks mailbox connection only. `-Sync -AllowFinancialWrites` imports real email and can write financial records; requires task authorization. Prints status/counts, not message samples or transactions. |
| `android/build-apk.ps1` | Java/SDK, SDK `aapt2`, Gradle dependencies | Builds debug APK; checks actual package/version against Gradle; copies to `android/build/deliveries/FinTrack.apk` by default and reports SHA-256. `-OutputPath` overrides destination. |
| `android/run-capture-tests.ps1` | Disposable booted AVD named `FinTrackCapture`, explicit `-Serial emulator-N`, SDK/ADB | Builds and installs isolated test APKs and executes persisted capture/lifecycle tests. Revokes test listener access on exit. Requires task authorization for installation; never part of default verification. |
| `assets/export-brand-assets.ps1` | Windows/.NET System.Drawing, master logo | Regenerates the five derived web/Android PNG assets. Overwrites those assets; does not build or install. |
| `migrations/*.sql` | PostgreSQL client and explicitly confirmed target | Manual schema operations; back up real databases first. `test_transaction_categories.sql` requires an empty disposable database. See domain/deployment guides. |
| `lib/common.ps1` | PowerShell | Shared exit-code and API-origin validation, not a standalone command. |

## Routine verification

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
