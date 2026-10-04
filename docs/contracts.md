# API and client contracts

The API prefix is `/api/v1`. Controller annotations and backend DTOs define the
implemented contract. This document explains coordination points; it is not a
generated OpenAPI specification.

## Authentication boundaries

- Web requests use `Authorization: Bearer <jwt>`.
- Android ingestion uses `X-Device-Token: <device-token>`; the server resolves the owning user.
- Device credentials are limited to transaction sync and the app-update path allowed by `JwtAuthFilter`. They cannot access web analytics, email sync or transaction lists. Disallowed device access returns 403; invalid tokens return 401.
- `GET /auth/google/url` and `GET /auth/google/callback` are public OAuth entry points.
- `POST /user/pairing-info/verify` is a public verification route, but needs a valid candidate token in the header or request body to return a valid result.
- CORS preflight and app-update access have special handling in [JwtAuthFilter](../api/src/main/java/com/store/api/config/security/JwtAuthFilter.java). Do not infer general permissions from an endpoint prefix.

## Client endpoints

Paths below are relative to `/api/v1`.

| Method and path | Purpose | Implementation |
| --- | --- | --- |
| `POST /transactions/sync`, `POST /transactions/sync/batch` | Single or batch ingestion | [TransactionController](../api/src/main/java/com/store/api/controller/TransactionController.java) |
| `GET /transactions` | Paginated list with dates, flow, search and direct product filters | [TransactionController](../api/src/main/java/com/store/api/controller/TransactionController.java) |
| `GET /financial-instruments`, `POST /financial-instruments`, `PUT /financial-instruments/{id}` | Product registration, editing and archive/reactivation | [FinancialInstrumentController](../api/src/main/java/com/store/api/controller/FinancialInstrumentController.java) |
| `PATCH /transactions/{id}/financial-instrument`, `GET /transactions/{id}/financial-instrument-suggestions` | Manual assignment and proposals requiring confirmation | [TransactionController](../api/src/main/java/com/store/api/controller/TransactionController.java) |
| `GET /analytics/summary`, `GET /analytics/period` | All-history summary or selected-period analytics | [AnalyticsController](../api/src/main/java/com/store/api/controller/AnalyticsController.java) |
| `GET /emails/test-connection`, `POST /emails/sync` | Connection check or actual ingestion | [EmailSyncController](../api/src/main/java/com/store/api/controller/EmailSyncController.java) |
| `GET /user/pairing-info`, `POST /user/pairing-info/regenerate`, `POST /user/pairing-info/verify` | Pairing information, rotation and verification | [DevicePairingController](../api/src/main/java/com/store/api/controller/DevicePairingController.java) |
| `GET /auth/google/url`, `GET /auth/google/callback`, `GET /auth/google/me`, `GET /auth/google/status`, `POST /auth/google/disconnect` | OAuth and session operations | [GoogleAuthController](../api/src/main/java/com/store/api/controller/GoogleAuthController.java) |

## Transaction ingestion payload

The batch endpoint receives an array of the single-request object. Both endpoints
currently return 201 with a transaction object or array, including when an existing
hash is returned instead of a new record.

| Field | Current requirement |
| --- | --- |
| `amount` | Required decimal number, at least `0.01`; direction is not encoded as a negative amount. |
| `flowType` | Required `INCOME`, `EXPENSE` or `INTERNAL_TRANSFER`. |
| `contactName` | Required nonblank string. |
| `channel` | Optional string; blank values are normalized to `UNKNOWN`. |
| `cardLast4` | Optional string; Android's current sync DTO does not send it. |
| `transactionDate` | Required ISO local date-time, without a timezone offset. |
| `transactionHash` | Required nonblank identifier; preserve it on retries. |
| `rawNotificationText` | Optional original text; financial and personal data handling applies. |

The response adds `id`, `createdAt` and nullable `financialInstrument`, and returns the transaction fields without
the original text. It has no currency field. Backend DTOs currently use mutable
Lombok classes; immutability is not an established contract requirement.

Product currency is fixed to PEN and does not add currency to captured transactions.
See [cards and accounts](domain/financial-instruments.md) for registration fields,
ownership checks, suggestions and schema preparation.

## Sources to update together

- [TransactionSyncRequest](../api/src/main/java/com/store/api/model/dto/TransactionSyncRequest.java) and [TransactionResponse](../api/src/main/java/com/store/api/model/dto/TransactionResponse.java).
- [Android TransactionSyncDto](../android/app/src/main/java/com/financemanager/listener/network/TransactionSyncDto.kt) and [ApiClient](../android/app/src/main/java/com/financemanager/listener/network/ApiClient.kt).
- [Frontend types](../ui/src/types/index.ts) and [HTTP service](../ui/src/services/api.ts).
- Related controller, validation, authentication and client tests.

For other response schemas, use the corresponding classes in
[backend DTOs](../api/src/main/java/com/store/api/model/dto). TypeScript interfaces
do not validate runtime responses. Test optional values, errors and authentication
when changing a contract, and record breaking changes explicitly.
