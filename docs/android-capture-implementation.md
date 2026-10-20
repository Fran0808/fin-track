# Android capture progress — FinTrack 1.3.1 (14)

Updated: October 18, 2026. This record tracks Android notification capture
implementation, validation and remaining work for FinTrack 1.3.1 (14).

## Implemented scope

Only the existing authorized Yape package is enabled in normal builds. Product copy remains
provider-neutral; additional wallets require an explicitly registered parser,
authorized packages and representative positive/negative fixtures. The application
ID, registered listener component, database name, pairing preferences, historical
transaction hashes and backend contract are preserved.

| Workstream | Implemented changes | Validation still required |
|---|---|---|
| Capture durability | Room v3 inbox; non-destructive 1→2→3 migration; bounded application-owned ingress with checked admission; processing after the first durable write. | Android process-death and real storage-failure tests. |
| Identity | Separate observations from transactions; atomic processing completion and financial insertion; reused active sources with a different event identity can require review. Synthetic Android updates and active replay are tested. | Current provider update behavior; indistinguishable payments cannot be resolved universally. |
| Listener health | Shared reconnect coordinator for disconnect, foreground and manual actions; three attempts per cycle, at least 60 seconds apart; permission/component checks; active replay only while connected. | Real NLS lifecycle, revoked access, reboot, force-stop, Doze and OEM behavior. |
| Parsing | Separate title/text/expanded/lines; explicit recognized, nonfinancial, ambiguous and unsupported results; compatible repeated title/body amounts; summaries excluded. Portable Unicode regex guards execute on Android. | Current provider formats; grouped synthetic delivery is verified only on the tested emulator. |
| Recovery and sync | Resume inbox processing and existing pending uploads on application startup, listener connection and successful pairing. | Scheduling behavior under device restrictions. |
| UI | Separate durable capture, saved movement, processing backlog, upload backlog and local review evidence. | Device UI validation; manual payment approval is not implemented. |

## Decisions and compatibility

The first successful inbox commit is the durability boundary. The precommit queue
has 256 entries; saturation and write failures are reported. Listener destruction
does not cancel application-owned ingress. Process death before the first commit
can still lose an observation if the original notification is no longer available.

Snapshots are limited to 32,768 characters total, 8,192 per field and 20 lines.
Clipped payloads require review instead of automatic financial interpretation.
Removal and reconciliation controls are processed in persistence order. Completing
an observation and inserting its financial movement share one Room transaction;
a failed transaction leaves the observation pending.

Capture freezes eligible ownership and timezone. Processing does not assign an
older event to whichever account happens to be paired later. The transaction date
uses `postTime` in the captured timezone; valid `when` participates in the existing
event hash. Repeated identities remain idempotent. A security code is only a
distinguishing hint, not a guaranteed bank operation ID.

The inbox contains no credentials or server URLs. Raw fields are cleared after
completion except for review evidence. Review content is redacted after seven
days during the next processing sweep, retaining identity/status. Pending
observations are not purged. Duplicate, ignored and control metadata is pruned
after 30 days. These are sweep-based rules, not exact deletion timers.

Storage remains app-private without an additional application encryption layer.
Because the inbox shares the Room file, **the entire `wallet_pulse_db` and its
sidecars are excluded from cloud backup and device transfer**. Existing local
transactions are not deleted, but those mechanisms will not restore the database.
General HTTPS and pairing-credential backup hardening remain separate work.

Capture uses the system notification listener and scheduled processing. No periodic
health watchdog is configured. Review evidence is informative: it does not approve, edit or automatically send a
second payment. Network failure remains distinct from capture failure.

## Verification record

| Check | Result | Limit |
|---|---|---|
| JVM suites | 70 tests each for `debug` and `captureTest`, zero failures/errors/skips. | The shared 69 cases execute in both suites; these are not 140 distinct scenarios. |
| Android instrumented suite | 29 tests passed on a disposable x86_64 emulator, SDK 37, in 8.067 seconds. | 19 Room/preferences cases, two parser platform regressions and eight OS notification-delivery cases. No physical-device validation. |
| Room runtime | Migration 1→2→3 preserves an existing row and validates the schema; reopen, rollback, replay, ownership, retention and timezone checks pass. | Reopening a database does not simulate process death. |
| Synthetic notification delivery | Receipt, active replay, presentation update, ambiguous reuse, grouped children/summary, expanded text, lines, nonfinancial and content-limit outcomes pass. Shell-origin notification is rejected. | Fixed synthetic fixtures; no claim about all current Yape formats. |
| Network isolation | Capture succeeds without pairing or Internet permission; rows remain unsynced. Existing sync assertions use fake failures/acknowledgements. | No real HTTP or backend persistence was exercised. |
| APK isolation | Normal ID and listener permission preserved; normal DEX contains no emitter package identity. Isolated app has a separate ID, no Internet permission and backup disabled. | No normal application installation or upgrade was performed. |

Normal artifact: `android/app/build/outputs/apk/debug/app-debug.apk`, FinTrack 1.3.1 (14).
SHA-256: `3909250E621BF7BFF02BC28B37EF376B493A1419D231E8ACEB314B6B07BBD773`.
Isolated artifact: `android/app/build/outputs/apk/captureTest/app-captureTest.apk`,
FinTrack 1.3.1-capturetest (14).
SHA-256: `FC74D7F4D62155EAD4195F78E8CC5953BF5A629A0BDB7F30343A284B3BD00079`.

Only isolated test APKs were installed on the disposable emulator. Validation used
fabricated fixtures; no bank service, live financial data or configured backend was used.

### Isolated test environment

`captureTest` has application ID `com.financemanager.listener.capturetest`, its own
app-private storage, disabled backup and no Internet permission. Its build-specific
`CaptureSourceRegistry` accepts only `com.fintrack.capture.emitter`; normal debug
and release builds accept only Yape. The debug-only `android/emitter` application
publishes fixed fixtures and has no Internet permission. Its explicit command
receiver requires a signature permission shared with the isolated app.

`android/run-capture-tests.ps1` requires an explicit `emulator-*` serial and verifies
the booted AVD name is `FinTrackCapture`. It validates artifact identities before
installing only the isolated app, its instrumented test APK and the emitter. It
revokes the isolated listener grant after the run. It does not create an AVD or
install SDK components. Instrumented delivery checks use unique fixture tags and
query the actual Room results without injecting listener callbacks or wiping shared
tables. Reported durations cover each scenario, not callback-to-commit latency or
a reliability guarantee.

### Platform defect corrected

The initial Android run exposed `PatternSyntaxException: Syntax error in regexp
pattern near index 3` in the Yape parser: Android's ICU regex engine rejected the
desktop modifier `(?U)`. Observations could be committed but parsing failed,
leaving them pending. The four affected guards now use explicit Unicode
letter/number/underscore boundaries. Android regressions exercise accented income,
negated operations, conflicting directions and accented word endings. The corrected
instrumented suite and both JVM suites pass; pending committed observations can be
processed by the corrected parser on the next sweep.

## Remaining work and acceptance criteria

| Priority | Open work | Acceptance evidence |
|---|---|---|
| P1 | Exercise listener disconnect/rebind and access revocation. | Bounded rebind attempts and respected permission revocation on Android. Initial connection and connected active replay are verified; disconnected lifecycle paths remain open. |
| P1 | Test interruption and scheduling. | Distinguish death before/after commit, listener destruction, reboot, force-stop and Doze; committed observations and financial rows remain recoverable. Reopening a database alone is insufficient proof. |
| P1 | Validate production transport and backup policy. | HTTPS restrictions and credential-backup behavior verified separately; do not treat inbox exclusions as complete security hardening. |
| P2 | Measure bursts and device/OEM behavior. | Record callback-to-commit and processing latency, backlog, recognized/review counts and acknowledgements at documented load/hardware. No reliability percentage or SLA is established yet. |
| Deferred | Additional wallets and manual ambiguity resolution. | Explicit scope, provider fixtures, authorization and business rules before implementation. |

Current source behavior is described in [Android recovery](android-notification-recovery.md)
and [architecture](architecture.md).
