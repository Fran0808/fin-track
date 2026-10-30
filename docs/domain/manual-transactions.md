# Manual movements and explicit reconciliation

Movements → `Agregar movimiento` records a completed expense or incoming
payment omitted from automatic capture. The user supplies a positive amount in
soles (0.01–99999999.99, up to two decimal places), local operation date/time,
concept/contact and a generic payment method: cash, card, bank transfer, wallet
or other. Category is optional and must be owned and active.
The transfer payment method does not mean an internal transfer: the user must
not record transfers between their own accounts as an expense or incoming money.
This entry flow does not schedule obligations or implement general corrections.

The form shows every entered field before confirmation, preserves drafts on
failure and reuses the same request UUID when retrying. Amount text is sent as
a decimal string; Java uses `BigDecimal` and PostgreSQL `NUMERIC(10,2)`.
Type, payment method and category use the dashboard's shared `CustomSelect`
appearance and keyboard behavior.
The server creates `captureOrigin=MANUAL`, without raw capture text or an invented
receipt reference. Generic payment method and source are independent. Manual
rows have a visible badge and origin; optional classification is an explicit
choice and automatic classification rules do not run during manual creation.

## Identity and ownership

`POST /api/v1/transactions/manual` requires web authentication. The server resolves
the owner from `UserContext`; callers cannot select an owner or supply source,
transaction hash, operation evidence or reconciliation metadata. Android/device
credentials and anonymous callers are rejected. Required payload fields are
`requestId` (UUID), `amount`, `flowType` (`EXPENSE` or `INCOME`), `contactName`,
`transactionDate` and `paymentMethod` (`CASH`, `CARD`, `BANK_TRANSFER`, `WALLET`,
`OTHER`); `categoryId` may be null.

The server derives `manual:<UUID>`, takes the existing per-user advisory lock
and binds that intent to a SHA-256 fingerprint of normalized input. Concurrent
retries of an identical intent return the existing row; another payload with that
UUID returns 409, without a second movement. Different users have independent
identities. Catalog validation happens before a new write. Fingerprints are not
returned to clients. Retry after later classification/reconciliation still refers
to the original intent, rather than recreating or restoring a row.

This protects retries, not two genuinely separate requests with different UUIDs.
Do not create a new intent to work around an uncertain response: retry the same
form or inspect the existing record first. No historical source or identity is
guessed or changed.

## Reviewing a later or existing capture

From a manual movement's detail, `Buscar capturas para conciliar` loads a read-only
review. Proposals are owned `EMAIL`/`PHONE` captures with equal amount and flow,
within three calendar days before/after the manual date, and not already linked.
Names, products, categories and references do not establish equality automatically.
At most 50 candidates are returned, newest date/ID first, with an explicit `hasMore`.
No candidate means the user can retry when the receipt arrives; a hidden candidate
must never be replaced with an unrelated visible one.

The preview shows both records, their dates, methods, category/product and the
effect of keeping the capture. It carries a ten-minute purpose-specific HMAC for
each candidate, binding owner, both records, reviewed financial/classification
fields, manual revision and expiration. Confirmation supplies only the reviewed
capture ID, expiration and signature. Cancellation does not change money.

Confirmation locks both owned rows in ID order, revalidates eligibility/signature
and links the manual record to that exact capture. A capture can have only one
manual antecedent; simultaneous claims cannot silently double-reconcile it.
Foreign/missing IDs return 404, invalid selections return 400 and stale, altered,
expired or conflicting reviews return 409 with no partial changes. Confirmation
of an already-linked identical pair is idempotent and never rewrites money.

## Counting once and retaining evidence

Both rows remain stored with their original amount, date, flow, hash and origin.
The capture's category, product and reference stay unchanged; the manual record
remains visible as an antecedent in the capture detail. No category/product is
copied implicitly. Linked manual records cannot be edited through classification,
instrument assignment or own-card correction; mutation paths take row locks.

Linked manuals are excluded from the active movement list, filter totals,
recent/pending results, instrument-filtered history, all-history and period
analytics, category/merchant/channel breakdowns, daily charts, comparisons,
largest/last expense and CSV. Historical classification also excludes them.
Counts describe the active ledger, while physical rows remain available through
the owned reconciliation status endpoint. CSV retains its nine-column layout.

The capture's date determines the retained movement's reporting period. If the
manual and capture cross a month boundary, the manual month loses its manual
amount; the capture month retains its existing amount. The review states this
before confirmation. Until explicit reconciliation, both entries count: the app
does not automatically identify duplicates across manual/email/phone sources.

`Deshacer conciliación` requires a second explicit confirmation warning that the
manual will count again alongside the capture. It clears the link, preserves both
records and increments a revision. Old signed reviews cannot reapply a link after
undo; a fresh preview is required. Undo is bound to the revision displayed by
status, so an old DELETE cannot undo a newly reviewed link. Replays of an already
completed undo are safe; a stale revision against a new link returns 409.

## Endpoints and rollout

| Method and relative path under `/api/v1/transactions` | Contract |
| --- | --- |
| `POST /manual` | 201 with existing transaction response for creation or identical replay. |
| `GET /manual/{id}/reconciliation-preview` | Manual, up to 50 `{ transaction, previewToken }` candidates, `limit`, `hasMore`, `expiresAt` (epoch seconds). |
| `POST /manual/{id}/reconcile` | `{ capturedId, expiresAt, previewToken }`; returns the retained capture. |
| `GET /{id}/manual-reconciliation` | `{ manual, captured, reconciledAt, revision }`, nullable where no link exists; supports either owned record ID. |
| `DELETE /manual/{id}/reconciliation/{capturedId}?revision=<reviewed revision>` | Undo that pair; returns the capture, while the manual re-enters the ledger. |

These endpoints are web-only and `Cache-Control: no-store`. Transaction list/sync
responses add `captureOrigin` and nullable `reconciledToId`; prior fields and phone
request payloads remain compatible. Existing phone clients use Gson and ignore
additional response properties. No phone installation or credential rotation is
required. Old APIs cannot create manuals or review links; the UI retains drafts
and reports failures rather than guessing that a write succeeded.

Confirm the intended database, take a backup and apply
[20261028_manual_transactions.sql](../../scripts/migrations/20261028_manual_transactions.sql)
**before starting the updated API**. The additive migration supports `MANUAL`,
nullable fingerprint/link/timestamp, a revision counter, tenant-safe composite
foreign key, unique capture target and consistency checks. Existing financial
rows and credentials are preserved; it is repeatable and performs no reconciliation.

```powershell
psql -X -h $dbHost -p $dbPort -U $dbUser -d $dbName -v ON_ERROR_STOP=1 -f scripts/migrations/20261028_manual_transactions.sql
```

Use verified private connection values and require `COMMIT` before deployment.
The [SQL regression](../../scripts/migrations/test_manual_transactions.sql) refuses
non-disposable databases outside `fintrack_manual_eval_`. The isolated runner/CI
include it with the other migrations. Live financial writes, real receipts,
production migration and service restarts are not ordinary verification steps.
