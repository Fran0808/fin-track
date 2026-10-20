# Android capture progress — FinTrack 1.3.3 (16)

Updated: October 19, 2026. This record tracks Android notification capture
implementation, validation and remaining work. The historical runtime evidence below
belongs to FinTrack 1.3.2 (15); the follow-up build is FinTrack 1.3.3 (16).

## Reconnect acceptance follow-up

The external lifecycle suite now includes seven scenarios. The two added cases
exercise a three-attempt exhausted reconnect cycle and a disabled Android listener
component. The exhaustion case suppresses only the platform rebind call through an
in-memory, captureTest-only fault control; production coordinator timeouts and the
60-second cooldown remain unchanged. It observes another 65 seconds after exhaustion
to reject a fourth automatic request, then releases the fault and checks explicit
foreground recovery and capture. This is controlled nonconnection, not evidence of
three real platform request failures or an OEM policy.

The component case uses Android's package manager to disable only the isolated
listener, checks absence of rebind and active-query attempts for 65 seconds, then
restores the default state and checks active recovery and idempotent replay.
Cleanup restores the component and releases fault controls. Normal builds always
allow platform rebind and contain no mutable fault setting or control command.

Both JVM variants passed 70 tests with zero failures, errors or skips. Normal and
isolated APKs and both isolated instrumentation APKs compiled successfully. APK
package metadata confirms 1.3.3 (16) and 1.3.3-capturetest (16). The two new runtime
cases remain pending execution; compilation does not close their acceptance items.
No emulator was initially connected and the usual AVD directory contained only
`Pixel_8a`. A separate disposable `FinTrackCapture` AVD was created under ignored
`android/build/capture-avd` using the installed SDK image. Two launch attempts
remained ADB-offline; the diagnostic boot still lacked the Android activity service
after 169 seconds. The emulator was stopped without installing any APK. Boot logs
remain in that ignored directory. This environment failure does not establish a
capture regression, and no new instrumented result is claimed.

Follow-up normal APK SHA-256:
`E6E9ABD97C30EBC992E4059540A35EF1D9FB545FE8C2B97F67E68FC562E26E0A`.
Follow-up isolated APK SHA-256:
`F1BAF0CC6F6D355D42567183D2B55CACEEC19EF8BD1CF44AA9CDEC36AD52F670`.

## Implemented scope

Only the existing authorized Yape package is enabled in normal builds. Product copy remains
provider-neutral; additional wallets require an explicitly registered parser,
authorized packages and representative positive/negative fixtures. The application
ID, registered listener component, database name, pairing preferences, historical
transaction hashes and backend contract are preserved.

| Workstream | Implemented changes | Validation still required |
|---|---|---|
| Capture durability | Room v3 inbox; non-destructive 1→2→3 migration; bounded application-owned ingress with checked admission; processing after the first durable write. Kernel-kill recovery is checked before/after commit. | Real storage failures and unattended restart/scheduling behavior. |
| Identity | Separate observations from transactions; atomic processing completion and financial insertion; reused active sources with a different event identity can require review. Synthetic Android updates and active replay are tested. | Current provider update behavior; indistinguishable payments cannot be resolved universally. |
| Listener health | Shared reconnect coordinator for disconnect, active-instance destruction, foreground and manual actions; three attempts per cycle, at least 60 seconds apart; permission/component checks; active replay only while connected. | Exhausted retry cycles, disabled components, reboot, force-stop, Doze and OEM behavior. |
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
| Android instrumented suite | 29 existing tests and five external lifecycle cases passed without failures on a disposable x86_64 emulator, SDK 37. | 19 Room/preferences cases, two parser platform regressions, eight OS delivery cases and five lifecycle scenarios. No physical-device validation. |
| Room runtime | Migration 1→2→3 preserves an existing row and validates the schema; reopen, rollback, replay, ownership, retention and timezone checks pass. | Reopening a database does not simulate process death. |
| Synthetic notification delivery | Receipt, active replay, presentation update, ambiguous reuse, grouped children/summary, expanded text, lines, nonfinancial and content-limit outcomes pass. Shell-origin notification is rejected. | Fixed synthetic fixtures; no claim about all current Yape formats. |
| Network isolation | Capture succeeds without pairing or Internet permission; rows remain unsynced. Existing sync assertions use fake failures/acknowledgements. | No real HTTP or backend persistence was exercised. |
| APK isolation | Normal ID and listener permission preserved; normal DEX contains no emitter package identity. Isolated app has a separate ID, no Internet permission and backup disabled. | No normal application installation or upgrade was performed. |

Normal artifact: `android/app/build/outputs/apk/debug/app-debug.apk`, FinTrack 1.3.2 (15).
SHA-256: `A14F68DCC9DA68288057A31C128AEFA13210ED5151E8EB07B1D34B8207BBABAB`.
Isolated artifact: `android/app/build/outputs/apk/captureTest/app-captureTest.apk`,
FinTrack 1.3.2-capturetest (15).
SHA-256: `FAF9BB0A240F5B16432BBD5D98EF09E472724BC844510F211F401CBF1DA8D1FB`.

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
installing only the isolated app, emitter and their two instrumented test APKs. It
revokes the isolated listener grant after the run. It does not create an AVD or
install SDK components. Instrumented delivery checks use unique fixture tags and
query the actual Room results without injecting listener callbacks or wiping shared
tables. Reported durations cover each scenario, not callback-to-commit latency or
a reliability guarantee.

The emitter now carries `CaptureLifecycleTest`, so terminating the capture app does
not terminate its test driver. A signature-protected, `captureTest`-only
`LifecycleControlReceiver` supports fixed commands and tag-scoped queries of counts,
synthetic hashes and lifecycle events. It rejects a paired target or an APK with
Internet permission. It cannot accept arbitrary notification content or SQL.
`CaptureExecutionProbe` pauses only the selected synthetic observation before its
Room insert or after commit, with processing held outside a database transaction.
The gate survives process replacement until explicitly released; a checkpoint
fails after two minutes if unreleased. Normal builds compile an inert probe and have no control receiver,
gate preferences or synthetic source identity in their DEX.

The driver verifies the original PID disappears after `run-as`/`SIGKILL`, then
reopens the actual isolated activity and checks the replacement process and Room
results. This establishes recovery on app reopening; it does not demonstrate that
Android always restarts a background process by itself. Listener setup invokes the
foreground coordinator and allows its real cooldown instead of assuming that a
permission grant immediately restores a previously unbound service. The test runner
also builds and installs the emitter's instrumented APK. The successful run uses
3,072 MiB and four virtual CPUs; an initial 2,048 MiB run exited before test startup
with Android's `LOW_MEMORY` reason.

### Platform defect corrected

The initial Android run exposed `PatternSyntaxException: Syntax error in regexp
pattern near index 3` in the Yape parser: Android's ICU regex engine rejected the
desktop modifier `(?U)`. Observations could be committed but parsing failed,
leaving them pending. The four affected guards now use explicit Unicode
letter/number/underscore boundaries. Android regressions exercise accented income,
negated operations, conflicting directions and accented word endings. The corrected
instrumented suite and both JVM suites pass; pending committed observations can be
processed by the corrected parser on the next sweep.

### Listener destruction recovery

The system-unbind regression exposed an incomplete recovery path: reconnect was
requested after `onListenerDisconnected`, but destruction of the current service
instance only cleared the connected flag. Destruction now delegates to the same
bounded coordinator. Permission and component checks still apply; no component is
toggled. A destroyed old instance cannot clear a newer instance's connected state.

On API 27 and later, access checks now use
[`NotificationManager.isNotificationListenerAccessGranted`](https://developer.android.com/reference/android/app/NotificationManager#isNotificationListenerAccessGranted(android.content.ComponentName))
for FinTrack's own component. API 26 keeps the component-specific secure-setting
fallback. The permission-return regression exposed a window in which the setting
read still reflected the previous grant state and skipped the connected active
query. The modern API removes that setting-cache dependency. Android may redeliver
an already visible notification as a live callback after access returns; the test
therefore checks one durable movement, an active query after connection, and an
explicit idempotent replay rather than requiring the first observation to carry
the internal recovery flag. The API 26 fallback was not exercised by this SDK 37 run.

### Lifecycle acceptance evidence

| Scenario | Verified result | Boundary |
|---|---|---|
| Commit completed, source removed, process killed | Reopening FinTrack processes the pending inbox into one movement. A second kill/reopen preserves its hash and does not duplicate it. | App reopening is explicit; autonomous background restart is not established. |
| Before commit, source removed, process killed | No posted inbox observation or movement remains; an independent tagged receipt confirms the replacement process is working. | This demonstrates the precommit loss window rather than eliminating it. |
| Before commit, source still visible, process killed | Android active replay recovers the receipt; repeated foreground recovery preserves a single movement and hash. | Removed notifications and bank history are not recoverable through this route. |
| Notification access revoked and restored | Repeated foreground/review actions produce no rebind or active-query attempts while access is revoked. After regrant, one movement is retained and explicit replay remains idempotent. | Access grant, connection and financial persistence are separate assertions. |
| Two real system unbinds | Destruction/disconnect recovery reconnects the listener. Repeated requests coalesce; the second request observes the real 60-second cooldown and capture works afterward. | This does not exhaust the complete three-failure cycle or simulate every OEM policy. |

Executed results are retained in the ignored build output
`android/app/build/outputs/capture-tests/instrumentation-lifecycle.txt`.
The existing suite completed in 8.651 seconds and the external lifecycle suite in
159.741 seconds. The real unbind case recorded two rebind requests after unbinding,
with a measured interval of 60,018 ms. This interval is one observed emulator result,
not a universal reconnect-time guarantee. Both JVM variants passed 70 tests each;
normal instrumented APK assembly and release Kotlin compilation also succeeded.

## Remaining work and acceptance criteria

| Priority | Open work | Acceptance evidence |
|---|---|---|
| P1 | Exercise exhausted reconnect cycles and disabled components. | Three-attempt termination and component-disabled behavior on Android; successful unbind/reconnect and cooldown are covered separately. |
| P1 | Test remaining interruption and scheduling paths. | Reboot, force-stop, Doze, unattended background restart and listener destruction during an in-flight capture. Kernel kill followed by app reopening does not establish these behaviors. |
| P1 | Validate production transport and backup policy. | HTTPS restrictions and credential-backup behavior verified separately; do not treat inbox exclusions as complete security hardening. |
| P2 | Measure bursts and device/OEM behavior. | Record callback-to-commit and processing latency, backlog, recognized/review counts and acknowledgements at documented load/hardware. No reliability percentage or SLA is established yet. |
| Deferred | Additional wallets and manual ambiguity resolution. | Explicit scope, provider fixtures, authorization and business rules before implementation. |

Current source behavior is described in [Android recovery](android-notification-recovery.md)
and [architecture](architecture.md).
