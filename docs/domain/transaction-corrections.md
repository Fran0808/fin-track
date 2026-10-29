# Reviewed movement corrections

The pencil icon in movement detail edits amount, local operation date/time and
financial flow (`EXPENSE`, `INCOME` or `INTERNAL_TRANSFER`). Its accessible name
and tooltip are `Corregir movimiento`. The history icon opens the audit trail.
These are views inside the existing native detail dialog.

The form reads current owned values before editing, requires a reason (up to 300
characters), accepts positive soles up to 99999999.99 with two decimal places,
and reviews old/new values and period contributions before explicit confirmation.
Unchanged submissions are rejected. A date change affects ordering, filters and
the period receiving the movement; it does not change the registration timestamp.
Internal transfers contribute to neither expenses nor income/net flow.

Each correction atomically stores its before/after amount, date, flow and operation
interpretation, reason and correction time, then changes the effective transaction
fields. All existing listings, CSV and analytics use those effective fields.
There is no second movement. Identity/hash, owner, capture origin, reference,
channel, contact and category are preserved. Changing flow clears an incompatible
own-card-payment interpretation to `UNKNOWN`; its original value remains in the
audit. Amount/date-only edits retain the operation interpretation.

The earliest audit entry preserves the originally registered financial values;
subsequent entries preserve each transition. History is paginated at 20 entries,
newest first. No edit/delete audit endpoint is exposed. This records the original
registered interpretation, not a copy of the raw email or notification.
Reverting a value means making another reviewed correction with its own reason.

## API and concurrency

- `GET /api/v1/transactions/{id}/corrections?page=0` returns current transaction,
  latest audit ID as `revision` (zero initially), `editable`, entries and `totalPages`.
- `POST /api/v1/transactions/{id}/corrections` takes a UUID `requestId`,
  `expectedRevision`, decimal-string-compatible `amount`, `transactionDate`,
  `flowType` and `reason`. It returns the current transaction.

Both require web authentication and return `Cache-Control: no-store`. Anonymous
requests receive 401 and device credentials 403. Reads and locked writes use the
authenticated owner; missing and foreign records return 404. Invalid fields or
unchanged values return 400. A stale revision or reused UUID with changed payload
returns 409. Identical retries reuse the audit entry and return the latest current
transaction, including any intervening correction, without another write.

The transaction row lock serializes concurrent corrections and reconciliation.
Both sides of an active reconciliation reject correction: explicitly undo that
link first. Corrections also invalidate prior reconciliation reviews, including
when later edits restore the same financial values. Ingestion hashes and manual
creation fingerprints remain unchanged, so source retries cannot create a second
movement or replace corrected values.

## Storage and rollout

[Incremental migration](../../scripts/migrations/20261029_transaction_corrections.sql)
only adds the audit table/indexes. It does not edit existing financial rows and
can be repeated. The table has an owner-scoped composite foreign key and a unique
transaction/request key. The empty-database initializer includes the same schema.
Apply after verifying the target and a backup, before starting the updated API.
The [SQL regression](../../scripts/migrations/test_transaction_corrections.sql)
requires a fresh `fintrack_corrections_eval_*` database and is included in the
isolated persistence runner and CI. No Android request or response changes.
