# Capture source diagnostics

Settings reports `Correo` (Gmail) and `Teléfono` separately. Source state never
certifies that every real expense was captured, and is unrelated to bank balances.

## Email

The existing owned `GET /api/v1/auth/google/status` response adds optional
`lastSyncAttemptAt` (UTC instant at completion) and `lastSyncFailureCode`.
`lastSuccessfulSyncAt` remains the last fully successful scan, including empty
scans. Failures and partial scans preserve it. Success clears the failure code.
Concurrent results cannot move completion/success times backward or replace a
newer result with an older result. The endpoint is `Cache-Control: no-store` and
does not contact Google or refresh credentials just to render Settings.

Only fixed codes are stored, never provider bodies, tokens or error text:

| Code | Evidence and next action |
| --- | --- |
| `AUTH_REQUIRED` | Missing usable authorization, HTTP 401 or explicit OAuth `invalid_grant`; offer the existing Google consent flow again. |
| `ACCESS_DENIED` | HTTP 403; review Gmail permissions and Google access configuration. A 403 alone does not establish that consent was revoked. |
| `RATE_LIMITED` | HTTP 429; wait before trying again. |
| `TEMPORARY` | Transport failure or HTTP 5xx; check server connectivity and retry later. |
| `PROCESSING` | One or more messages failed to process; already saved movements remain, and the cursor does not advance. Retry, then investigate processing if persistent. |
| `UNKNOWN` / absent / future code | Cause unavailable; retry and request a connection review if persistent. Do not guess an authentication failure. |

Scheduled and explicit scans share the same diagnostics path. Partial ingestion
is shown as incomplete with the saved count, rather than complete. Failure to
read status hides stale success/failure advice and permits refreshing status
without starting a scan. The header label explicitly refers to email only.
This feature does not count pending messages or alter hashes/parsing semantics.

## Phone

`GET /api/v1/user/phone-status` returns only `lastReceivedAt` (UTC instant or null),
requires a web session and is `Cache-Control: no-store`. Anonymous and device
credentials cannot read it. The server selects the authenticated user's metadata;
the endpoint accepts no account selector and returns no device token or identity.

Single and batch transaction sync record server receipt time after accepting
movements, including valid deduplicated retries. The timestamp commits in the
same transaction: rejected or rolled-back batches do not advance it. Email
ingestion never advances phone reception, even when retrying the same movement.
Reception metadata is separate from immutable first-capture origin. Existing
phone payloads and responses remain compatible; no APK update is needed.

This is per-account receipt evidence across devices, not a heartbeat, a named
device inventory or knowledge of the phone's local queue. Pairing verification
and code rotation do not record reception. A timestamp remains historical after
rotation. Missing history is displayed as no receptions *recorded since tracking
was enabled*, with steps to check pairing, notification access and connectivity.
The panel has independent loading/failure/retry and manual status refresh.

## Schema rollout

Before deploying/restarting the updated API against an existing database, confirm
the target and take a backup, then apply the additive
[source diagnostics migration](../../scripts/migrations/20261027_source_diagnostics.sql):

```powershell
psql -X -h $dbHost -p $dbPort -U $dbUser -d $dbName -v ON_ERROR_STOP=1 -f scripts/migrations/20261027_source_diagnostics.sql
```

Use the verified connection values of the intended installation; templates do
not confirm a target. Require `COMMIT` before starting the new API. The migration
adds nullable columns to `users` and `google_oauth_tokens` with bounded locks and
a fixed-code check. It does not rewrite financial entries or credentials, invent
historical receipts, or backfill old failure causes. It can be repeated safely.
The empty-database bootstrap includes the same fields. Existing older APIs show
phone status unavailable rather than fabricating a receipt.

The [SQL regression](../../scripts/migrations/test_source_diagnostics.sql) refuses
databases outside the disposable `fintrack_sources_eval_` prefix. The isolated
persistence runner and CI include it alongside the other migrations. Live Google
consent, provider access, phone installation and deployment remain separate
acceptance operations.
