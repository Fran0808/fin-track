# Android notification capture and recovery

FinTrack 1.1.0 (version code 6) keeps the existing application ID, preferences and
`wallet_pulse_db`. Room migration 1 to 2 adds nullable ownership and notification
metadata without deleting rows or changing their transaction hashes.

## Capture and identity

Only `com.bcp.innovacxion.yapeapp` is accepted, including in debug builds. Shell
notifications are not financial fixtures. Group summaries are ignored. The
listener reads expanded text first, with text-line fallback; ambiguous bodies
containing multiple monetary amounts are rejected.

Live callbacks and active notifications recovered on connection use the same
capture path. While connected, recovery is also available under
**Ajustes > Ver diagnóstico de captura > Revisar notificaciones aún visibles**.
The transaction date uses the valid Android event timestamp (`when`), falling back
to publication time (`postTime`), in the device timezone, rather than processing
time. This is not necessarily the
bank's operation time. Deleted notifications and bank history cannot be recovered.

New hashes combine the Android notification key, event timestamp (`when`, falling
back to `postTime`) and a fingerprint of flow, normalized decimal amount, contact
and security code when present. A security code is a distinguishing hint, not a
guaranteed bank operation ID. Notification text and codes are never logged.
The durable active-source match suppresses presentation updates even if their
publication timestamp changes. Removal retires that active match; a new event
with a new timestamp can then be captured. Reconnection retires missing keys.
Room's unique event and transaction-hash indexes make repeated recovery idempotent.

Two notifications with different keys or different financial fingerprints remain
separate even within the same minute. If Yape reuses an active key with identical
payment data and no removal, the listener treats it as an update. This case is
intrinsically ambiguous without a trustworthy operation identifier and must be
validated using real Yape behavior. It is not a promise of universal deduplication.

Legacy recovery compares financial fields/text and a two-minute timestamp window,
attaches source metadata to an unambiguous old row, and preserves its old hash,
date and sync flag. Ambiguous legacy matches stop recovery for that event and
appear as a diagnostic error instead of creating another financial record.

## Account ownership and existing records

New captures belong to the server and verified user of the pairing session in
effect at capture. Existing pairings without a saved user ID use a credential
fingerprint until that credential is verified again. The token itself is not
stored in transaction rows. Reverification upgrades only that credential's scope.

Legacy rows have no proven owner, so migration leaves them unassigned. Captures
made without pairing, and notifications published or dated before the current pairing,
are also unassigned. These rows remain available through **Movimientos > Sin cuenta**
or **Inicio > Revisar movimientos**. A pending row can be assigned individually
from its detail using **Asignar a mi cuenta**
and an explicit confirmation in the app; its hash remains unchanged. Already
synced legacy rows are retained for review and cannot be reassigned this way.
No old pending row is automatically sent to a newly paired account.

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
chronological activity log, not a per-payment audit trail. Successful storage or
deduplication clears previous processing errors.

A disconnect requests a permission-checked rebind at most once per minute,
without a repeating reconnection loop. **Intentar reconectar** offers an explicit
rebind when permission is enabled but the listener is disconnected. Merely opening
the activity does not prove that the listener is connected. Capture does not require
the activity to stay open or the server to be reachable.

Capture persistence is serialized. Lock and disk I/O failures receive at most
three attempts with the original entity/hash. Other failures are reported and
do not stop later events. Destroying the listener cancels its scope; events not
yet saved can only be recovered if their notifications remain active.

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
The **Por enviar** counter includes all current-account rows. Queuing a send is
shown separately from sending and server confirmation; rejected authorization
offers **Volver a vincular cuenta**. Send state is not restored as a live task after
process restart; the durable local rows determine which movements still need sending.

## Mobile navigation

- **Inicio** shows setup steps when needed, capture status, the last successful
  save, send status, unassigned-review access and five recent account movements.
- **Movimientos** reads the complete local history for the current owner, ordered
  by transaction date. Search matches contact names without case sensitivity or
  amount text. Date filters include both endpoints; selecting only a start date
  filters that single day. Calendar values are interpreted in UTC as calendar dates
  to avoid shifting a selected day to the previous day on the device.
- Status filters are **Todos**, **Por enviar**, **Enviados** and **Sin cuenta**.
  Unassigned rows are a separate scope; **Todos** does not mix accounts or include
  unassigned rows. An empty review retains navigation and filters after assigning
  its last record. This use of **Por enviar** is distinct from classification
  pending work in the web dashboard.
- Tapping a movement opens its amount, date, origin, owner and delivery state.
  Ownership assignment still requires individual confirmation. Notification text
  and security codes are not displayed in this detail.
- **Ajustes** contains account linking, notification access, expandable diagnostic
  activity and the installed version. QR linking is primary; manual token/server
  fields are behind **Configuración manual**, with the token masked.

## Verification and device acceptance

From `android`:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest
```

Unit regressions cover strict amounts/promotions, masked names, authorized
packages, event identity, ownership scopes, HTTP acknowledgements, session changes,
offline behavior, rejected records, bounded storage retries, inclusive date filters,
movement-state filters and bounded diagnostic transitions. Instrumented
`CaptureDatabaseTest` uses disposable databases to test migration, idempotency,
ownership isolation, legacy hashes, full owner-scoped history, empty review after
assignment and pending counts beyond 50 rows. Compiling
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
