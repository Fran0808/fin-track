# API and client contracts

The API prefix is `/api/v1`. Controller annotations and backend DTOs define the
implemented contract. This document explains coordination points; it is not a
generated OpenAPI specification.

## Authentication boundaries

- Web requests use `Authorization: Bearer <jwt>`.
- Android ingestion uses `X-Device-Token: <device-token>`; the server resolves the owning user.
- Device credentials are limited to transaction sync and the app-update path allowed by `JwtAuthFilter`. They cannot access web analytics, email sync or transaction lists. Disallowed device access returns 403; invalid tokens return 401.
- Android pairs only when verification confirms `valid` and a `userId`. Sync uses a fixed server/token snapshot and requires the response's `transactionHash` to match before acknowledging a local row; the payload is unchanged.
- `GET /auth/google/url` and `GET /auth/google/callback` are public OAuth entry points.
- `GET /auth/google/url` creates a browser session and a state valid for ten minutes.
  The callback must present that state in the same session. Missing/unverified
  Google identity fails authentication. The redirect contains `#login_code=...`,
  never a JWT. `POST /auth/google/session` accepts JSON `{ "code": "..." }` and
  returns `{ "token": "..." }` once, within sixty seconds and in that same session.
  It is public only for POST; wrong/expired/reused codes return 401. Dashboard
  requests preserve the session cookie. Old `?token=...` redirects are rejected.
- CORS allows explicit configured origins; wildcard credentialed access is rejected.
- `POST /user/pairing-info/verify` is a public verification route, but needs a valid candidate token in the header or request body to return a valid result.
- CORS preflight and app-update access have special handling in [JwtAuthFilter](../../api/src/main/java/com/store/api/config/security/JwtAuthFilter.java). Do not infer general permissions from an endpoint prefix.
- [CorsConfig](../../api/src/main/java/com/store/api/config/CorsConfig.java) allows `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, and `OPTIONS`, including classification and instrument assignment updates. Actual protected requests still require authentication.

## Client endpoints

Paths below are relative to `/api/v1`.

| Method and path | Purpose | Implementation |
| --- | --- | --- |
| `POST /transactions/sync`, `POST /transactions/sync/batch` | Single or batch ingestion | [TransactionController](../../api/src/main/java/com/store/api/controller/transaction/TransactionController.java) |
| `GET /transactions` | Paginated list with dates, flow, contact/category search, product, category, channel, amount and optional review filters | [TransactionController](../../api/src/main/java/com/store/api/controller/transaction/TransactionController.java) |
| `GET /transactions/export` | Full CSV export of filtered transactions with UTF-8 BOM | [TransactionController](../../api/src/main/java/com/store/api/controller/transaction/TransactionController.java) |
| `GET /financial-instruments`, `POST /financial-instruments`, `PUT /financial-instruments/{id}` | Product registration, editing and archive/reactivation | [FinancialInstrumentController](../../api/src/main/java/com/store/api/controller/instrument/FinancialInstrumentController.java) |
| `GET /categories`, `POST /categories`, `PUT /categories/{id}`, `PATCH /categories/{id}/toggle`, `DELETE /categories/{id}` | Category catalog management, user categories, subcategories, and system category toggling | [CategoryController](../../api/src/main/java/com/store/api/controller/category/CategoryController.java) |
| `PATCH /transactions/{id}/financial-instrument`, `GET /transactions/{id}/financial-instrument-suggestions` | Manual assignment and proposals requiring confirmation | [TransactionController](../../api/src/main/java/com/store/api/controller/transaction/TransactionController.java) |
| `PATCH /transactions/{id}/classification` | Update transaction category | [TransactionController](../../api/src/main/java/com/store/api/controller/transaction/TransactionController.java) |
| `PATCH /transactions/{id}/own-card-payment` | Explicitly repair a legacy own-card repayment as an internal transfer | [TransactionController](../../api/src/main/java/com/store/api/controller/transaction/TransactionController.java) |
| `GET /transactions/pending` | All-history pending movements; reason `ALL` (default), `CATEGORY` or `INSTRUMENT`, page ≥ 0 and size 1–100 (default 10), newest date then ID first | [TransactionController](../../api/src/main/java/com/store/api/controller/transaction/TransactionController.java) |
| `GET /classification-rules`, `POST /classification-rules`, `PUT /classification-rules/{id}`, `DELETE /classification-rules/{id}` | User-owned automatic category rules | [ClassificationRuleController](../../api/src/main/java/com/store/api/controller/category/ClassificationRuleController.java) |
| `GET /analytics/summary`, `GET /analytics/period` | All-history summary or selected-period analytics | [AnalyticsController](../../api/src/main/java/com/store/api/controller/analytics/AnalyticsController.java) |
| `GET /emails/test-connection`, `POST /emails/sync` | Connection check or actual ingestion | [EmailSyncController](../../api/src/main/java/com/store/api/controller/email/EmailSyncController.java) |
| `GET /user/pairing-info`, `POST /user/pairing-info/regenerate`, `POST /user/pairing-info/verify` | Pairing information, rotation and verification | [DevicePairingController](../../api/src/main/java/com/store/api/controller/auth/DevicePairingController.java) |
| `GET /auth/google/url`, `GET /auth/google/callback`, `GET /auth/google/me`, `GET /auth/google/status`, `POST /auth/google/disconnect` | OAuth and session operations | [GoogleAuthController](../../api/src/main/java/com/store/api/controller/auth/GoogleAuthController.java) |

## Transaction ingestion payload

`operationType` is provider-independent operation evidence, separate from the
existing `flowType` and channel. Currently supported values are `UNKNOWN` (no
specific evidence) and `OWN_CARD_PAYMENT` (requires `INTERNAL_TRANSFER`). Missing
or null ingestion values become `UNKNOWN`; existing Android payloads remain valid.
Contradictory own-payment/expense-or-income tuples and unknown enum values return
400 before persistence. Responses expose the stored operation; retrying an old
payload cannot erase an already-confirmed operation. Hash inputs are unchanged.
Transaction updates write only changed fields, so a concurrent category/product
edit cannot restore a stale operation type or flow over a confirmation.

Own-card confirmation accepts no body or user identifier. It resolves the
authenticated web user's movement by ID (404 for absent/foreign IDs), accepts
`EXPENSE` or `INTERNAL_TRANSFER`, and rejects income with 400. The user explicitly
confirms ownership; neither contact text, bank, channel nor assigned product is
used to guess it. The endpoint sets `operationType=OWN_CARD_PAYMENT` and
`flowType=INTERNAL_TRANSFER`, preserves amount/date/hash/category/product, and is
idempotent. The UI offers this for `UNKNOWN` expenses/transfers, with a confirmation
and cancel step before PATCH. Missing metadata from an older API disables this
action; a successful response must confirm both operation and flow. Device
credentials cannot call this endpoint. Apply the
[operation migration](../../scripts/migrations/20261025_transaction_operation_type.sql)
before deploying the updated API to an existing schema. CSV keeps its existing
nine-column contract; flow-based filtering/export remains unchanged.

The batch endpoint receives an array of the single-request object. Both endpoints
currently return 201 with a transaction object or array, including when an existing
hash is returned instead of a new record.

| Field | Current requirement |
| --- | --- |
| `operationType` | Optional `UNKNOWN` or `OWN_CARD_PAYMENT`; absent/null means `UNKNOWN`. Own-card payments require neutral `INTERNAL_TRANSFER` flow. |
| `amount` | Required decimal number, `0.01`–`99999999.99`, at most two decimal places; direction is not encoded as a negative amount. |
| `flowType` | Required `INCOME`, `EXPENSE` or `INTERNAL_TRANSFER`. |
| `contactName` | Required nonblank string, at most 150 characters. |
| `channel` | Optional string, at most 50 characters; blank values are normalized to `UNKNOWN`. |
| `cardLast4` | Optional string, at most 10 characters; Android's current sync DTO does not send it. |
| `transactionDate` | Required ISO local date-time, without a timezone offset. |
| `transactionHash` | Required nonblank identifier, at most 64 characters; preserve it on retries. |
| `rawNotificationText` | Optional original text, at most 65536 characters; financial and personal data handling applies. |

Batches require 1–100 non-null valid entries. Listing requires page ≥ 0 and size
1–100. Invalid ingestion requests return 400 before persistence. Simultaneous
same-user/hash retries serialize in PostgreSQL and return the existing movement;
they do not append duplicate raw logs. CSV exports allow up to 10000 matching
movements and return 400 above that limit; narrow the filters to export more history.

The response adds `id`, `createdAt` and nullable `financialInstrument`, and returns the transaction fields without
the original text. It has no currency field. Backend DTOs currently use mutable
Lombok classes; immutability is not an established contract requirement.

Product currency is fixed to PEN and does not add currency to captured transactions.
See [cards and accounts](../domain/financial-instruments.md) for registration fields,
ownership checks, suggestions and schema preparation.

## Period analytics

Period analytics includes `categoryBreakdown`: every expense category in the
requested user/period, ordered by amount descending. Each item has `category`
(null for missing/blank names), decimal `amount`, rounded `percentage` of total
period expenses and `count`. Names are trimmed for grouping; historical category
names remain visible even if the catalog entry was archived. Income and internal
transfers are excluded. An empty period returns an empty array. No schema migration
or Android sync change is required.

Period analytics also includes `periodStart`/`periodEnd` local date-times describing
the actual aggregation interval, including the current-month cutoff. Merchant
drill-down uses these bounds and expense flow instead of inferring dates in the browser.

`previousPeriodStart`/`previousPeriodEnd` expose the actual previous-month interval;
current months stop at the matching day available in that month, while closed
months compare complete months. February/short-month bounds are clamped to their
last day and are displayed explicitly. `expenseDifference` is decimal current
expense minus previous expense, with no division or percentage. Future periods
return null comparison fields; a previous expense of zero remains a valid value.

`largestExpense` contains the largest positive individual expense's `merchantName`,
decimal `amount` and local `transactionDate` for the authenticated user/interval.
Amount ties use the earliest transaction date then ID. `peakExpenseDay` contains
the local `date` and decimal sum `amount` of the highest-spend day; ties use the
earliest day. Both exclude income/transfers and return null for empty/future
periods without positive expenses. They describe captured movements, not advice
or account balances. These additions require no database migration or Android change.

`GET /transactions` and `/transactions/export` accept optional `merchantName`:
an exact, case-sensitive contact name, with literal `%`/`_` and preserved whitespace.
It combines with the existing authenticated-user, date and other filters. Empty
string matches an empty stored name; omission removes this restriction. Successful
responses expose `X-Fintrack-Merchant-Filter: APPLIED` (or `NONE` when absent) through
CORS. The client requires acknowledgment for an active merchant filter so older
servers cannot silently return the full history. Listing, pagination and CSV use
the same filter. Deploy API and UI together.

## Transaction classification

`GET /transactions` preserves its existing pagination fields and adds `summary`:
decimal `totalExpense`, `totalIncome` and `internalTransfersAmount` across all
matching movements, without a page-size or CSV-export cap. All date, flow, search,
instrument, category, channel, amount, pending-review, exact-merchant and
category-group restrictions apply under the authenticated user's ownership.
Amounts are aggregated in PostgreSQL using the same specification as the page;
the dashboard listing reads both in one repeatable-read transaction. Internal
transfers, including own-card repayments, contribute only to their separate total.
No matches produce three zeros. The dashboard labels these as filter totals,
shows visible-page subtotals separately and displays only the selected flow when
one is active. Older APIs lacking `summary` show unavailable totals rather than
substituting a page total. Existing clients can ignore this additive field;
Android ingestion and the database schema are unchanged.

Category drill-down adds optional `categoryGroup` to transaction listing and CSV.
It matches the stored category after trimming, case-sensitively, exactly as the
analytics grouping does. Empty string selects null/blank categories; omission
removes the restriction. The existing `category` filter keeps its prior behavior.
Successful responses expose `X-Fintrack-Category-Filter: APPLIED` (or `NONE`);
the client checks acknowledgment to avoid unfiltered results from an older API.
User ownership, dates, expense flow, paging and CSV retain the analysis scope.

`PATCH /transactions/{id}/classification` accepts category names up to 100 characters.
New assignments must match an active category in the authenticated user's catalog
(case-insensitive). Invalid assignments return HTTP 400 with a Spanish `message`.
The transaction must also belong to that user. Existing legacy or inactive category
values can be submitted unchanged without requiring an active catalog entry.
An empty or whitespace-only category clears it; null or an omitted category leaves
it unchanged. Names are trimmed and retain their submitted casing.
Unexpected server errors return a generic Spanish message without SQL or financial details.

Existing databases need the [category schema migration](../development/local.md#transaction-category-migration).

Transaction tags and personal notes are retired: requests/responses no longer
declare `tags` or `notes`, listing/export no longer support the `tag` filter,
and text search covers only contact and category. CSV has nine columns:
`ID,Fecha,Comercio o contacto,Importe,Tipo,Categoria,Medio,Tarjeta,Hash`.
Deploy the dashboard and API together; update clients that consume those fields
or CSV positions. Android sync requests do not contain them and are unchanged.
Physical removal from existing databases is a separate, destructive migration;
see [retirement and deployment](../domain/transaction-metadata-retirement.md).

## Pending movements and rules

`GET /classification-rules/{id}/history-preview` is web-authenticated, owned-rule
only and `Cache-Control: no-store`. The rule and target category must be active.
Response: `ruleId`, `matchText`, `categoryName`, `transactions` (existing transaction
DTOs, at most 200), `limit`, `hasMore`, `expiresAt` (Unix seconds) and `previewToken`.
The scope is all-history uncategorized income/expenses for which the selected rule
is the first eligible rule by priority then ID. Previewing performs no writes.

`POST /classification-rules/{id}/history-apply` requires `transactionIds` (1–200
distinct positive IDs from that exact preview), `expiresAt` and `previewToken`
(64 lowercase hex characters). The HMAC-SHA256 signature has a dedicated history
purpose and binds owner, rule configuration/order, reviewed movement data and a
ten-minute expiration. The server revalidates it under row locks; changes to
rule/category metadata or reviewed movement data return 409 without writes.
Categories assigned since preview are preserved, and repeats skip classified rows.
Success returns `appliedCount` and `skippedCount`; only category assignments change.
Newly captured movements cannot join the signed selection. Missing/foreign rule
or movement IDs return 404, inactive rules/categories or invalid payloads return
400, expired/altered previews and concurrent database conflicts return 409.
Anonymous/device access remains 401/403. No schema or Android sync change is needed.

`GET /transactions` and `/transactions/export` accept optional `pendingReason`:
`CATEGORY` (null/blank category, excluding internal transfers), `INSTRUMENT`
(unassigned instrument, including transfers), or `ALL` (either). It combines with
all existing filters under the authenticated user's ownership predicate. Omission
preserves ordinary listing/export. Unknown values return 400. Omitting dates
searches all history; `allHistory` is dashboard state, not an API parameter.
Listing and CSV sort by transaction date then ID descending. Successful responses
include `X-Fintrack-Review-Filter` with the requested enum or `NONE`; CORS exposes
this header. The dashboard requires the matching header when review is active,
preventing silently unfiltered results during an API/dashboard version mismatch.
The legacy `/transactions/pending` endpoint remains available for compatibility.

The inbox returns the existing paginated transaction response. Rule creation and
replacement require `matchText` (nonblank, at most 150 characters), `categoryId`
(positive ID of an owned active category), `priority` (integer 0–9999) and `enabled`
(boolean). Responses add `id`, `categoryName` and `categoryActive`; lists are sorted
by priority then ID. Deletion returns 204. All endpoints require web authentication;
device credentials cannot manage rules or read the inbox. Foreign rule IDs return
404; invalid category targets return 400. An existing inactive target may be kept
only when saving the rule disabled.

See [classification behavior and schema preparation](../domain/classification.md).

## Sources to update together

- [TransactionSyncRequest](../../api/src/main/java/com/store/api/model/dto/transaction/TransactionSyncRequest.java) and [TransactionResponse](../../api/src/main/java/com/store/api/model/dto/transaction/TransactionResponse.java).
- [Android TransactionSyncDto](../../android/app/src/main/java/com/financemanager/listener/network/TransactionSyncDto.kt) and [ApiClient](../../android/app/src/main/java/com/financemanager/listener/network/ApiClient.kt).
- [Frontend types](../../ui/src/types/index.ts) and [HTTP service](../../ui/src/services/api.ts).
- Related controller, validation, authentication and client tests.

For other response schemas, use the corresponding classes in
[backend DTOs](../../api/src/main/java/com/store/api/model/dto). TypeScript interfaces
do not validate runtime responses. Test optional values, errors and authentication
when changing a contract, and record breaking changes explicitly.
