# Transaction capture origin

The movement detail displays the origin of its first successful capture as
`Correo`, `Teléfono` or `Manual`, plus `Registrado en FinTrack` and an optional
`Referencia de operación`. The registration timestamp is the transaction's
existing `createdAt`, not the operation date, email sent time, or phone observation
time. No phone identity or package name is inferred or displayed.

Manual creation uses a separate web-only endpoint with explicit review and no
invented receipt. See [manual entry and reconciliation](manual-transactions.md).
Reconciliation preserves first-origin evidence and retains the manual antecedent
while only the capture contributes to financial totals.

## Evidence and ingestion

The phone sync entry point records `captureOrigin=PHONE`; the internal email
ingestion entry point records `EMAIL`. The external sync request does not accept
origin or receipt-reference fields. Provider parsers normalize explicit receipt
evidence into `ParsedEmailTransaction.receiptReference`; ingestion passes that
optional value without provider checks. Values must fit 100 characters and the
bounded letters/digits/period/underscore/slash/hyphen format; absent or invalid
optional references remain null without discarding a valid movement.

Legacy `operationNumber` parsing/hash inputs remain unchanged. In particular,
ambiguous email words and phone security codes do not become receipt references.
The detail API exposes no raw notification/email body, sender address, security
code, device credential or mailbox link. This feature does not determine whether
similar transactions are duplicates and does not automatically decide that records are duplicates. Manual/capture
reconciliation is a separate explicit review flow.

Per-user hash retries return the existing transaction without rewriting first
capture metadata, creating extra movements or appending diagnostic evidence.
Category/product edits, own-card confirmation and historical classification keep
origin/reference/registration time intact. Financial fields and existing hashes remain unchanged; CSV keeps its layout. Different source hashes are still distinct transactions; no
automatic email/phone deduplication is introduced. Manual/capture links require
the separate explicit reconciliation review.

Historical origin is `UNKNOWN`. Existing diagnostic `source_package` contains
channel metadata and cannot establish email versus phone. The UI says
`Origen no disponible`, retains the known registration time and explains the
missing evidence. Replaying an old capture does not backfill or guess its origin.

## Read contract

`GET /api/v1/transactions/{id}/origin` is web-authenticated, scoped to the current
user and `Cache-Control: no-store`. It returns only `origin` (`EMAIL`, `PHONE`,
`UNKNOWN`), `registeredAt` (existing local ISO date-time) and nullable
`operationReference`. Unknown/phone origins expose no reference. Missing and
foreign movement IDs return the same 404; anonymous and device requests remain
401 and 403. The ingestion request is unchanged. Transaction responses now add compatible
`captureOrigin` and nullable `reconciledToId` for manual entry/reconciliation;
existing phone clients ignore extra properties and need no update.

The UI queries origin when a detail opens, independently of category/product
editing. Loading, unavailable/retry and legacy-unknown states are separate. A
delayed response for a previously opened movement cannot replace current evidence.
An older API or malformed response produces an unavailable state, not a guessed
source. The shared HTTP client retains its existing session/401 protections.

## Deployment and verification

This feature requires the additive
[capture-origin migration](../../scripts/migrations/20261025_transaction_capture_origin.sql)
for existing databases. It adds `capture_origin` (non-null, default `UNKNOWN`) and
nullable `operation_reference`, with bounded evidence constraints. It preserves
all financial records and metadata; reruns preserve explicitly recorded origins.
The empty-database bootstrap schema contains the same columns/constraints.

Back up and confirm the intended database before applying the migration. With its
connection configured privately, the user runs from the repository root:

```powershell
psql -X -v ON_ERROR_STOP=1 -d YOUR_CONFIRMED_DATABASE -f scripts/migrations/20261025_transaction_capture_origin.sql
```

Apply it before deploying/restarting the new API, then reload the UI. Source
changes alone do not update a running API. No live migration, ingestion or restart
is part of ordinary verification. Historical origin remains unknown after migration.

The [SQL regression](../../scripts/migrations/test_transaction_capture_origin.sql)
requires a fresh `fintrack_capture_origin_eval_` database. The isolated
[persistence runner](../../scripts/checks/test-persistence.ps1) provisions it
alongside the four JVM suites and three existing SQL fixtures, disables email
scheduling and removes only its temporary cluster. Tests cover evidence,
normalization, hash preservation, per-user retries, tenant isolation and migration
idempotency. See [local verification](../development/local.md#backend-verification).
