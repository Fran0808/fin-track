# Android notification capture and recovery

FinTrack 1.3.5 (version code 18) keeps the existing application ID, preferences and
`wallet_pulse_db`. Room migration 1 to 2 adds nullable ownership and notification
metadata without deleting rows or changing their transaction hashes. Migration 2 to 3
adds the capture inbox in the same database. Existing transactions and pairing
preferences retain their identities. See [capture delivery](capture-progress.md).

## Capture and identity

Only `com.bcp.innovacxion.yapeapp` is accepted in normal debug and release builds. Shell
notifications are not financial fixtures. Group summaries are ignored. The
listener reads nonblank expanded text first, with text-line fallback even if the
expanded field exists but is blank; ambiguous bodies
containing multiple monetary amounts are rejected.

The separate `captureTest` build accepts only the synthetic notification emitter,
uses its own application ID and app-private storage, and has no Internet permission.
It cannot capture the production provider. Its debug-only emitter publishes fixed
fixtures through Android; the normal app never authorizes that emitter. See the
[capture verification record](capture-progress.md) for executed
checks and remaining lifecycle validation.

Live callbacks and active notifications recovered on connection use the same
capture path. While connected, recovery is also available under
**Ajustes > Ver diagnóstico de captura > Revisar notificaciones aún visibles**.
The transaction date uses Android publication time (`postTime`) in the timezone
saved at capture, rather than processing time. The valid `when` timestamp is used
in event identity, not as the transaction date. This is not necessarily the
bank's operation time. Deleted notifications and bank history cannot be recovered.

New hashes combine the Android notification key, event timestamp (`when`, falling
back to `postTime`) and a fingerprint of flow, normalized decimal amount, contact
and security code when present. A security code is a distinguishing hint, not a
guaranteed bank operation ID. Notification text and codes are never logged.
An identical event identity is deduplicated. A different event identity with the
same active source and financial fingerprint is retained for review rather than
silently suppressed or automatically counted again. Removal retires that active match; a new event
with a new timestamp can then be captured. Reconnection retires missing keys.
Room's unique event and transaction-hash indexes make repeated recovery idempotent.

Two notifications with different keys or different financial fingerprints remain
separate even within the same minute. If Yape reuses an active key with identical
payment data and no removal, the inbox preserves the new observation as
`REUSED_ACTIVE_SOURCE` for review. This case is intrinsically ambiguous without
a trustworthy operation identifier; no second transaction is sent automatically. It is not a promise of universal deduplication.

Legacy recovery compares financial fields/text and a two-minute timestamp window,
attaches source metadata to an unambiguous old row, and preserves its old hash,
date and sync flag. Ambiguous legacy matches stop recovery for that event and
remain in the inbox for review instead of creating another financial record.

## Account ownership and existing records

New captures belong to the server and verified user of the pairing session in
effect at capture. Existing pairings without a saved user ID use a credential
fingerprint until that credential is verified again. The token itself is not
stored in transaction rows. Reverification upgrades only that credential's scope.

Legacy rows have no proven owner, so migration leaves them unassigned. Captures
made without pairing, and notifications published or dated before the current pairing,
are also unassigned. These rows remain available through **Movimientos > Confirmar destino**
or **Inicio > Revisar movimientos**. A pending row can be assigned individually
from its detail using **Confirmar destino de envío**
and an explicit confirmation in the app; its hash remains unchanged. Already
synced legacy rows are retained for review and cannot be reassigned this way.
No old pending row is automatically sent to a newly paired account.

When the address of the same backend changes, use **Ajustes > Conexión con la web >
Cambiar dirección del servidor**. Enter its full `http://` or `https://` address and
select **Verificar y guardar**. The app verifies the existing device token at the
new address, requires the same saved user ID when available, and rejects a response
if pairing changed while verification was in progress. The new address must belong
to the same trusted backend; this is an explicit correction, not server discovery.
It preserves the token, pairing start time and local ownership scope without
rewriting financial rows or hashes. After saving, use **Inicio > Enviar ahora**.

Address corrections store a hashed owner alias for the verified server/user in the
existing pairing preferences. The alias survives unpairing so linking the same
user at the corrected address, including after credential rotation, still finds
the existing movements. Unpairing removes active credentials and personal pairing
details. Aliases contain no tokens, emails or financial values, and are never
applied to another user or server. Ordinary QR linking does not infer that two
server addresses represent the same backend.

Unpairing and then linking a different address without first recording a verified
address correction creates a different ownership scope, even if the user ID is the
same. A payment can disappear from the current view while its unchanged pending
row remains in Room under the previous scope. Regenerating the web token does not
delete the local row, but revokes the original credential needed to verify that
old pairing. Such rows are not automatically reassigned to the new destination.

The normal history and pending counter query only the current ownership scope.
Switching accounts retains the other account's rows but hides and excludes them
from synchronization. Credential rotation for a verified user keeps the same
ownership scope. An old credential-only scope cannot be reassigned to a different
credential without first proving its owner; relink with the original valid
credential before rotating it if such pending rows exist.

## Errors, connection and synchronization

Live connection and send status remain in process memory: reopening the process
does not falsely restore a connected listener. Last connection, supported
notification and successful-save timestamps are persisted, together with a bounded
history of 30 diagnostic events. This separate local preference store contains only
timestamps and fixed event types, never notification text, names, amounts or codes.
Events distinguish receipt, recognition, rejection, group summaries, successful
storage, deduplication/recovery and a server-confirmed upload. The history is a
chronological activity log, not a complete per-payment history. Successful storage or
deduplication clears previous processing errors.

A disconnect, destruction of the active listener instance, foreground entry or **Intentar reconectar** triggers the same
coordinator: at most three attempts per cycle, at least one minute apart, with
permission and component-state checks before each attempt. A successful connection
cancels the remaining attempts. No component is toggled and no periodic watchdog
is installed. Merely opening
the activity does not prove that the listener is connected. Capture does not require
the activity to stay open or the server to be reachable.

Capture ingress is serialized in application scope, with a 256-entry precommit
queue and checked admission. The snapshot is bounded to 32,768 characters total,
8,192 per field and 20 lines. Clipped payloads require review, never automatic
financial interpretation. Lock and disk I/O failures receive three write attempts;
failed writes or queue saturation produce an explicit error. This precommit window
cannot guarantee recovery if the process dies and the original notice disappears.

The listener's destruction does not cancel application ingress. Once an observation
is committed to Room, process death does not discard it. A no-network WorkManager
job and an in-process processor resume pending observations. Completion and any
financial insert share one Room transaction. Removal/reconciliation controls pass
through the same ordered inbox. Opening the app, connecting the listener or
successful pairing also resumes processing and pending synchronization.

Raw inbox fields are cleared immediately after recognized, duplicate, control or
nonfinancial completion. Review evidence is redacted after seven days on the next
processing sweep, retaining identity/status. Pending observations are not purged.
Completed duplicate/ignored/control metadata is pruned after 30 days. The database
and sidecars are excluded from cloud backup and device transfer; existing local
transactions remain on the phone. This is app-private storage, not a new layer of
application-level encryption. General pairing-backup and transport hardening remain
a separate delivery.

**Ver capturas por revisar** displays up to 100 current-scope/unassigned observations
and fixed reasons, outside the sanitized diagnostic log. It does not approve a
payment, assign it to another user, or send ambiguous evidence to the server.

Synchronization is queued after saving, requires network connectivity and uses
`APPEND_OR_REPLACE` so a new capture does not cancel a running upload. Each run
holds a fixed server/token/session and selects only that owner's pending rows.
It stops if the session changes. Unpairing also cancels queued work; a request
already accepted by the old server cannot be undone by unpairing.

Each request sends one record through the existing batch API, isolating invalid
records. Only a successful response containing that record's hash marks it synced.
Authentication errors require pairing; other permanent HTTP errors preserve the
record for review while allowing later records to proceed. Network/server failures
retry up to five attempts per work request; manual synchronization can try again.
**Enviar ahora** starts a coroutine directly on the I/O dispatcher in the application
process. It contacts the configured server without waiting for WorkManager scheduling,
automatic backoff or Android's internet-validation constraint, which can delay a
request even when a local server is reachable. Repeated taps do not accumulate
manual attempts. Both paths share a process-local mutex around reading and sending
pending rows: a manual attempt waits for an in-flight automatic request and reads
the pending list only after acquiring the lock. A manual failure reports the cause
and allows another tap immediately; scheduled automatic retries remain available.
Navigation does not cancel the manual attempt, but process termination can interrupt
it; unconfirmed rows remain pending and automatic work stays durable. Unpairing
cancels the manual coroutine and both unique work chains, including manual requests
persisted by older versions. The existing worker still recognizes those requests.

Failure messages distinguish connection refusal/unreachability, host lookup,
timeout, TLS, HTTP status and a missing acknowledgement. They never include raw
exception messages, response bodies, notification contents or credentials. A failed
send adds a fixed diagnostic event. **Ajustes > Conexión con la web** displays the
configured server address to help verify the destination. A queued request does
not overwrite the display of an already running upload.
The **Por enviar** counter includes all current-account rows. Queuing a send is
shown separately from sending and server confirmation; rejected authorization
offers **Conectar de nuevo con la web**. Send state is not restored as a live task after
process restart; the durable local rows determine which movements still need sending.

The isolated lifecycle suite uses a separate emitter process and real `SIGKILL`
termination of only the capture-test app. It distinguishes a committed observation
whose source notification has disappeared from an uncommitted snapshot, and reopens
the actual app to resume processing. Keeping the source visible enables active
notification recovery after a precommit kill; removing it does not make the snapshot
durable. The tests also revoke/regrant notification access and call the real system
unbind API. Returning to the app invokes the same foreground coordinator used by
the test control. A separate real force-stop case verifies committed-inbox recovery
after explicit activity reopening and hash preservation across a second stop/reopen.
During a three-second observation without control broadcasts, the tested emulator
restarted the process for the bound listener. This is not a guarantee of unattended
processing or recovery on every device: the processing gate remained active until
explicit reopening. A separate real-reboot scenario verified committed pending
work through passive Room/WAL copies before starting instrumentation or opening
the activity: this emulator processed one movement automatically after boot, and
explicit reopening preserved its hash and owner. Its test-only gate releases on
a changed boot count. This does not recover notifications missed while powered off
or establish reboot behavior on every device. Doze and OEM restrictions remain
unverified. See the
[current verification record](capture-progress.md).

## Mobile navigation

- **Inicio** shows setup steps when needed, capture status, the last successful
  save, send status, destination-review access and five recent local movements.
- **Movimientos del teléfono** combines the complete local history for the current
  owner with unassigned local rows, ordered
  by transaction date. Search matches contact names without case sensitivity or
  amount text. Date filters include both endpoints; selecting only a start date
  filters that single day. Calendar values are interpreted in UTC as calendar dates
  to avoid shifting a selected day to the previous day on the device.
- Status filters are **Todos**, **Por enviar**, **Enviados** and **Confirmar destino**.
  **Todos** includes unassigned rows without including rows assigned to other users
  or servers. **Confirmar destino** includes only unsynced unassigned rows; already
  synced legacy rows remain visible as **Enviado** and cannot be reassigned or sent
  again. An empty review retains navigation and filters after assigning
  its last record. This use of **Por enviar** is distinct from classification
  pending work in the web dashboard.
- Tapping a movement opens its amount, date, origin, destination and delivery state.
  Ownership assignment still requires individual confirmation. Notification text
  and security codes are not displayed in this detail.
- **Ajustes > Conexión con la web** explains that the QR/token configures the send
  destination independently of local notification capture. Settings also contains
  notification access, expandable diagnostic
  activity and the installed version. QR linking is primary; manual token/server
  fields are behind **Configuración manual**, with the token masked.

Feature headings use **Tus movimientos** and **Captura de movimientos** so the
interface is not named after a single provider. Only authorized supported packages
are processed; currently that provider remains Yape. A movement's actual source
is still displayed in its detail and row. Generic feature copy does not enable
capture from additional packages.

## Verification and device acceptance

For local connection failures, compare the address shown in Android settings with
the PC's current LAN address. Test that address from the phone's browser on the
same network; even an authentication error proves that the HTTP server responded.
The web dashboard working at `localhost` does not verify access from the phone.
If the PC address changed, correct it using the verified address-change dialog
before sending pending movements. The QR's default address comes from
`APP_SERVER_PUBLIC_URL`; its field can also be edited in the web pairing dialog.

From `android`:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest
```

Unit regressions cover strict amounts/promotions, masked names, authorized
packages, event identity, ownership scopes, HTTP acknowledgements, session changes,
offline behavior, rejected records, bounded storage retries, inclusive date filters,
movement-state filters, bounded diagnostic transitions, sanitized connection/HTTP
failure reporting, the anonymized screenshot receipt format, expanded-text fallback,
direct manual execution, repeated taps, cancellation while waiting for an upload,
retrying a failed manual attempt with the same hash and serial execution of
simultaneous manual/automatic attempts, verified address changes, mismatched users,
stale verification and invalid addresses. Instrumented `PairingAddressPreferencesTest`
uses isolated preferences and an in-memory database to check pending-row preservation,
address aliases, unpairing, credential rotation and user/server isolation.
They also cover relinking a new address after unpairing: old pending rows remain
stored and are excluded from the new ownership scope. These tests require an
authorized test device/emulator and are not executed by APK
assembly. Instrumented
`CaptureDatabaseTest` uses disposable databases to test migration, idempotency,
ownership isolation, legacy hashes, full owner-scoped history, empty review after
assignment, pending counts beyond 50 rows and an authorized synthetic receipt through
parsing, idempotent storage, offline retry and explicit server acknowledgement. Compiling
the instrumented test APK does not execute these database tests.

Installation requires task authorization. Before an authorized upgrade, compare
the installed version code/name and signing certificate with the new APK. Do not
uninstall or clear data to bypass a signature mismatch. Then run instrumented
tests on a test device/emulator and verify real notifications: disconnect/reconnect,
offline capture, repeated recovery, notification updates, equal payments,
group summaries and changing accounts. Synthetic events do not replace Yape
validation. Never use live financial ingestion scripts as unit tests.

Platform references: [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
and [WorkManager existing-work policies](https://developer.android.com/reference/androidx/work/ExistingWorkPolicy).
