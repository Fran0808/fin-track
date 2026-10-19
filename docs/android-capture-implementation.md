# Android capture progress — FinTrack 1.3.0 (13)

Updated: October 18, 2026. This record tracks Android notification capture
implementation, validation and remaining work for FinTrack 1.3.0 (13).

## Implemented scope

Only the existing authorized Yape package is enabled. Product copy remains
provider-neutral; additional wallets require an explicitly registered parser,
authorized packages and representative positive/negative fixtures. The application
ID, registered listener component, database name, pairing preferences, historical
transaction hashes and backend contract are preserved.

| Workstream | Implemented changes | Validation still required |
|---|---|---|
| Capture durability | Room v3 inbox; non-destructive 1→2→3 migration; bounded application-owned ingress with checked admission; processing after the first durable write. | Android process-death and real storage-failure tests. |
| Identity | Separate observations from transactions; atomic processing completion and financial insertion; reused active sources with a different event identity can require review. | Actual notification-update behavior; indistinguishable payments cannot be resolved universally. |
| Listener health | Shared reconnect coordinator for disconnect, foreground and manual actions; three attempts per cycle, at least 60 seconds apart; permission/component checks; active replay only while connected. | Real NLS lifecycle, revoked access, reboot, force-stop, Doze and OEM behavior. |
| Parsing | Separate title/text/expanded/lines; explicit recognized, nonfinancial, ambiguous and unsupported results; compatible repeated title/body amounts; summaries excluded. | Current provider formats and actual grouped notification delivery. |
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
| JVM suite in the repository | 69 tests, zero failures/errors. | Synthetic fixtures and fake network outcomes; no actual Android notification delivery. |
| Application and instrumented APK builds | Successful. | Instrumented tests were compiled, not executed. |
| Migration 2→3 SQL | Columns/indexes matched generated Room schema; a synthetic existing row was preserved using desktop SQLite. | Not an Android Room runtime test. |
| Merged manifest and APK | Listener identity/permission preserved; APK inspected as `com.financemanager.listener`, FinTrack 1.3.0 (13). | No installation or upgrade was performed. |

Artifact: `android/app/build/outputs/apk/debug/app-debug.apk`.
SHA-256: `93165A9C1C5A842719DC0765B41703198A7A713B4CC84B9F63C32EFE57E1CD77`.
Validation used synthetic fixtures and fake network outcomes. No installation
or live financial ingestion was performed.

## Remaining work and acceptance criteria

| Priority | Open work | Acceptance evidence |
|---|---|---|
| P1 | Run CaptureDatabaseTest, CaptureInboxTest and PairingAddressPreferencesTest on a disposable emulator. | Successful Room migration, reopen/replay, rollback, ownership, retention and timezone assertions. |
| P1 | Implement an isolated notification emitter/test harness. | Demonstrated delivery/grouping without broadening the production allowlist or impersonating a bank package. A shell notification must remain rejected. |
| P1 | Exercise the real listener lifecycle. | Rebind attempts remain bounded; permission revocation is respected; active notifications are queried only when connected. |
| P1 | Test interruption and scheduling. | Distinguish death before/after commit, listener destruction, reboot, force-stop and Doze; committed observations and financial rows remain recoverable. Reopening a database alone is insufficient proof. |
| P1 | Validate production transport and backup policy. | HTTPS restrictions and credential-backup behavior verified separately; do not treat inbox exclusions as complete security hardening. |
| P2 | Measure bursts and device/OEM behavior. | Record callback-to-commit and processing latency, backlog, recognized/review counts and acknowledgements at documented load/hardware. No reliability percentage or SLA is established yet. |
| Deferred | Additional wallets and manual ambiguity resolution. | Explicit scope, provider fixtures, authorization and business rules before implementation. |

Current source behavior is described in [Android recovery](android-notification-recovery.md)
and [architecture](architecture.md).
