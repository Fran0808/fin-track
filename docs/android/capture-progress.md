# Android capture progress — FinTrack 1.3.6 (19)

Updated: October 22, 2026. This record tracks Android notification capture
implementation, validation and remaining work. The historical runtime evidence below
belongs to FinTrack 1.3.2 (15); the current build is FinTrack 1.3.6 (19).

## Deep Doze acceptance — October 22

Two host-driven scenarios passed on the disposable x86_64 SDK 37 emulator: retaining
the live app process and terminating its original PID with real `run-as`/`SIGKILL`
during deep Doze. In both, one synthetic observation was committed and pending,
its source removed, before Android reported `IDLE`. The target was not permanently
allowlisted. The host kept verifying deep idle for 45 seconds while inspecting
passive Room/WAL copies, without app control broadcasts or activity opening.

The captureTest-only `arm_doze` gate makes processing eligible 30 seconds after
arming, independent of Doze exit. It is not a simulation of OS scheduling: it holds
the fixture only long enough to establish the pending-work precondition. Processing
then uses the existing application/worker paths. Both runs processed the movement
while deep idle was still confirmed. After unforcing idle, restoring battery state
and waking the screen, passive inspection and explicit activity opening preserved
one movement and its hash, captured identity/timezone/timestamps and unassigned
ownership. No backend acknowledgement or Internet access was involved.

This establishes conservation and processing in these controlled emulator cases,
including replacement of a dead process. It does not prove that Doze always delays
processing, that WorkManager ran independently of the in-process processor, or that
untethered physical devices behave the same. ADB diagnostics wake the host/guest for
inspection; forced deep idle is not a battery-life or natural-sleep measurement.
Temporary platform allowances and OEM policies are not universally excluded.

The existing reboot runner now accepts `-Scenario doze` and optional
`-KillDuringIdle`; the default remains the original reboot scenario. It restores
forced-idle, simulated battery and initial screen state and revokes test listener
access on exit. Preparation, verification and cleanup each passed instrumented
checks. Reports are retained in ignored
`android/app/build/outputs/capture-tests/doze-*.txt`, `doze-result.json` and
`doze-kill-result.json`. Six host safety tests and 70 JVM tests per variant passed.
Normal and isolated APK/test assembly succeeded; the nine-case lifecycle suite
was not rerun in full. The real reboot scenario was not repeated for this follow-up.
The existing SIGKILL/force-stop regressions passed together in 65.44 seconds on the
new artifact. Cleanup was checked: deep state `ACTIVE`, force flag false, charging
restored, and isolated listener access revoked.

Current normal APK: `android/app/build/outputs/apk/debug/app-debug.apk`, 1.3.6 (19).
SHA-256: `DBEBA93A99EBA44AE6104AA716FE418028A4B57DA8C488D990ADEC6FA24CD450`.
Current isolated APK: `android/app/build/outputs/apk/captureTest/app-captureTest.apk`,
1.3.6-capturetest (19).
SHA-256: `A91FAB770B0B89FDDE01CF0DD5BBFD8F47D269FDB0C642DAF4E592480E1B6EA3`.

## Device reboot acceptance — October 21

The separate host-driven reboot scenario passed on the disposable x86_64 SDK 37
emulator. The host verified a changed Linux boot ID, rather than inferring reboot
from a new app PID. Before reboot, one tagged synthetic observation was committed
and pending, its source notification removed, and no financial movement existed.
After boot, passive copies of the actual Room database and WAL showed one saved
movement before any app activity, control broadcast or instrumentation was started.
Explicit activity opening and repeated recovery retained that movement and hash.
Observation identity, captured owner/timezone/timestamps and unassigned movement
ownership were preserved; the movement remained unsynced. No backend was used.

The observation window is up to 30 seconds after Android reports boot completion;
this is one emulator result, not a startup SLA or proof for every OEM. Passive
inspection uses `run-as` file reads, not Application startup. Two consecutive equal
database/WAL reads are required before querying temporary host copies, which are
closed and removed afterward. Only tagged fixture results are queried or reported.

`scripts/android/test-capture-reboot.ps1` requires an explicit emulator serial,
Python 3 and ADB, rejects AVDs other than `FinTrackCapture`, validates all artifact
identities before installation and installs only isolated APKs. Its Python helper
uses only the standard library. Preparation, explicit-launch verification and cleanup
run as separate `CaptureRebootTest` instrumentation phases because reboot terminates
the driver too. The runner revokes the isolated listener grant on exit.

The captureTest-only `arm_reboot` command holds processing after the first commit
and releases the gate on a changed Android boot count. Ordinary gates retain their
existing behavior. Normal builds have neither the command nor its persistent flag.
This is controlled pending-work recovery, not proof of notification delivery while
the phone is powered off. The fixture is unpaired; cross-account cases remain
covered by the existing Room tests, not this reboot scenario.

Both JVM variants passed 70 tests. The host guard tests passed three cases: physical
serial rejection, wrong AVD rejection and rejection of a normal APK before any
installation. The first host attempt found a Windows temporary-copy cleanup defect;
closing the SQLite connection explicitly fixed it before the successful reboot run.
The complete nine-case lifecycle suite was not rerun. The existing committed-capture
SIGKILL and force-stop scenarios passed together on the new artifact in 16.981
seconds. Reports are in ignored
`android/app/build/outputs/capture-tests/reboot-*.txt` and `reboot-result.json`.

Reboot follow-up normal APK: `android/app/build/outputs/apk/debug/app-debug.apk`, 1.3.5 (18).
SHA-256: `1BEEB4BCE01016DD6A992E425B9D066650D849F2B4C011CACD01F49B86E36BEF`.
Reboot follow-up isolated APK: `android/app/build/outputs/apk/captureTest/app-captureTest.apk`,
1.3.5-capturetest (18).
SHA-256: `EBBD8B9B896175E8036EBB239BD3D8B14A01EA28D6E40A039C9C387106119C8E`.

## Force-stop acceptance follow-up — October 21

The October 21 force-stop follow-up contained nine scenarios. Only the emitter's
instrumented test driver and runner changed; FinTrack remains 1.3.4 (17), with the
same application APKs and hashes recorded below.

The new scenario pauses after the first inbox commit, removes the synthetic source
notification, and invokes Android's real `am force-stop --user 0` for only the
isolated capture app. It verifies that the original PID disappears. For three
seconds it queries only the package manager and process list, without sending a
control broadcast or opening the app. It then explicitly reopens the real activity,
releases the gate and checks one movement, one posted observation, no pending
observation and no synchronization. A second force-stop/reopen preserves the single
movement and its transaction hash. Captures are unpaired synthetic fixtures; no
backend or real financial data is involved.

The focused execution passed this scenario and the existing committed-capture
SIGKILL regression: two tests, zero failures, 13.215 seconds on the disposable
x86_64 SDK 37 emulator. The updated driver compiled successfully. The complete
nine-case suite was not rerun for this test-driver-only change. Output is retained
in ignored `android/app/build/outputs/capture-tests/force-stop.txt`.

The first attempt incorrectly required the package to stay stopped for the whole
observation period. Android instead restarted the process for the bound notification
listener. The test now records this outcome rather than assuming persistent stopped
state. In the passing execution, process restart was observed without an explicit
launch, while `stopped=true` was not sampled. The first failed attempt remains in
`force-stop-initial.txt` beside the final output. This establishes preservation and
recovery after explicit reopening; the processing gate prevents claiming autonomous
financial processing. A three-second observation on one emulator does not establish
restart behavior on every Android/OEM version, reboot or Doze.

## Reconnect acceptance follow-up — October 19

The October 19 external lifecycle suite included eight scenarios. Three added cases
exercise a three-attempt exhausted reconnect cycle, a disabled Android listener
component and listener destruction during an admitted precommit capture.
The exhaustion case suppresses only the platform rebind call through an
in-memory, captureTest-only fault control; production coordinator timeouts and the
60-second cooldown remain unchanged. It observes another 65 seconds after exhaustion
to reject a fourth automatic request, then releases the fault and checks explicit
foreground recovery and capture. This is controlled nonconnection, not evidence of
three real platform request failures or an OEM policy.

The component case uses a signature-protected fixed command in the isolated app
to disable only its own listener through PackageManager with `DONT_KILL_APP`.
It verifies the actual component state, performs a real unbind if still connected,
and keeps notification access granted to isolate the component guard. It checks
absence of rebind and active-query attempts for 65 seconds, then restores the
default state and checks active recovery and idempotent replay.
Cleanup restores the component and releases fault controls. Normal builds always
allow platform rebind and contain no mutable fault setting or control command.

The in-flight case pauses an admitted observation before its first Room write,
requests a real system unbind and waits for listener destruction in the same
application process. It removes the source notification before releasing ingress,
then verifies one durable movement, no pending observation and an unchanged hash
after reconnection. This checks service destruction without application process
death; it does not remove the precommit process-death loss window.

Both JVM variants passed 70 tests with zero failures, errors or skips. Normal and
isolated APKs and both isolated instrumentation APKs compiled successfully. APK
package metadata confirms 1.3.4 (17) and 1.3.4-capturetest (17). The corrected
component case and the new in-flight case passed together in 131.174 seconds.
The complete final run passed all 29 existing instrumented tests and all eight
external lifecycle scenarios without failures. The lifecycle suite took 575.488
seconds, including real cooldown and quiet-observation periods. The runner restored
the component and revoked notification access after the run; the disposable
emulator was stopped. Final output is retained in ignored
`android/app/build/outputs/capture-tests/reconnect-final.txt`.

| Follow-up acceptance | Executed result | Boundary |
|---|---|---|
| Exhausted reconnection | Exactly three attempts, at least 60 seconds apart; no fourth attempt during another 65 seconds; explicit retry restores connection and capture. | Platform rebind is suppressed by a captureTest-only fault; real unbind/rebind is covered separately. |
| Disabled component | With notification access retained, the disabled listener makes no rebind/active-query attempt or capture for 65 seconds; restoring it recovers the visible receipt with idempotent replay. | Actual PackageManager state under the isolated app UID, not a shell-command assumption or a full application disable. |
| In-flight listener destruction | The same process commits and processes one admitted precommit capture after actual listener destruction and source removal; reconnection preserves its hash. | Service destruction, not process death, reboot, force-stop or Doze. |

The first seven-case execution passed the exhausted-cycle scenario but failed
the component scenario: Android rejected shell `pm disable` with a
`SecurityException`, and the test had not validated its command output. The fixed
in-app command changes the real component state under the app's own UID and is
absent from normal builds. No production capture defect was established by that
initial test failure. Its output remains in ignored
`android/app/build/outputs/capture-tests/reconnect-acceptance.txt`.

No emulator was initially connected and the usual AVD directory contained only
`Pixel_8a`. A separate disposable `FinTrackCapture` AVD was created under ignored
`android/build/capture-avd` using the installed SDK image. Two launch attempts
remained ADB-offline; the diagnostic boot still lacked the Android activity service
after 169 seconds. A subsequent diagnostic launch completed boot with 4,096 MiB,
four virtual CPUs, host graphics and `-shell`. The precise cause of the earlier
boot delay was not isolated. Only isolated APKs were installed. Boot logs remain
in that ignored directory; no normal app or physical device was installed or used.

Follow-up normal APK SHA-256:
`AE934A90542DDED1E5217A9C284A8A3A521A3CDDD943F01DCCFB078736DC5F9E`.
Follow-up isolated APK SHA-256:
`7472A4D5F3123A69193419F88DC65313B75E8CD4A28FD974B23485D94616B2DB`.

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
| Listener health | Shared reconnect coordinator for disconnect, active-instance destruction, foreground and manual actions; three attempts per cycle, at least 60 seconds apart; permission/component checks; active replay only while connected. Exhaustion, disabled-component recovery, in-flight destruction, isolated reboot and forced deep Doze have Android coverage. | Natural sleep, unattended restart under other restrictions and physical-device/OEM behavior. |
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

## Historical verification record — FinTrack 1.3.2 (15)

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

`scripts/android/run-capture-tests.ps1` requires an explicit `emulator-*` serial and verifies
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
| P1 | Test remaining interruption and scheduling paths. | Natural untethered sleep, isolated worker scheduling and physical/OEM policies. Forced deep Doze passed with a live process and after SIGKILL; isolated reboot recovered automatically and force-stop recovery after opening is covered. These bounded observations do not establish universal autonomous recovery. |
| P1 | Validate production transport and backup policy. | HTTPS restrictions and credential-backup behavior verified separately; do not treat inbox exclusions as complete security hardening. |
| P2 | Measure bursts and device/OEM behavior. | Record callback-to-commit and processing latency, backlog, recognized/review counts and acknowledgements at documented load/hardware. No reliability percentage or SLA is established yet. |
| Deferred | Additional wallets and manual ambiguity resolution. | Explicit scope, provider fixtures, authorization and business rules before implementation. |

Current source behavior is described in [Android recovery](notification-recovery.md)
and [architecture](../architecture/overview.md).
