# Architecture

Listen Service is the repository name; FinTrack is the displayed product name.
It records personal cashflow from wallet notifications and financial emails.
The financial meaning of these records is defined in [cashflow rules](domain/cashflow.md).

## Modules and sources of truth

| Module | Responsibility | Version or configuration source |
| --- | --- | --- |
| `api/` | Authentication, ingestion, persistence and analytics | [pom.xml](../api/pom.xml), [application.properties](../api/src/main/resources/application.properties) |
| `android/` | Authorized notification capture, local storage, pairing and background sync | [app Gradle file](../android/app/build.gradle.kts), [version catalog](../android/gradle/libs.versions.toml) |
| `ui/` | Authentication UI, cashflow dashboard and device pairing | [package.json](../ui/package.json), [Vite configuration](../ui/vite.config.ts) |
| `scripts/` | Developer verification, diagnostics and APK delivery | [Development guide](development.md#existing-scripts) |
| `docker/` | Container image and environment guidance | [Docker guide](../docker/README.md) |

The backend targets Java 21 and currently uses Spring Boot 4.1.1 with PostgreSQL.
Android uses Kotlin and Jetpack Compose; the frontend uses React, TypeScript,
Vite and Tailwind. Build manifests are authoritative for exact versions.

## Backend package organization

The backend keeps its layer-based structure. Controllers, services and DTOs are
grouped by feature inside those layers to make related files easier to find:

```text
com/store/api/
├── controller/{analytics,auth,category,email,instrument,transaction}/
├── service/{analytics,auth,category,email,instrument,transaction}/
├── model/dto/{analytics,auth,category,email,instrument,transaction}/
├── model/entity/
├── model/enums/
├── repository/
├── config/
└── App.java
```

Create only feature folders that contain files. Entities, enums, repositories
and configuration retain their existing packages. Services for movement
classification and product assignment belong to `service/transaction`.
Email keeps its `client`, `parser` and `scheduler` subpackages.

Tests mirror the corresponding production packages. Parser tests belong to
`service/email/parser`; the product persistence workflow test lives in
`service/instrument` and imports the transaction services explicitly.
Keep `App` at `com.store.api` so Spring can discover all child packages.
Package organization does not change HTTP routes, DTO fields or table names.

## Android ingestion

1. `YapeNotificationListenerService` receives a notification.
2. `NotificationParserDispatcher` rejects unsupported packages before parsing content.
3. A bank-specific parser extracts amount, flow, contact, date and hash.
4. A `LocalTransactionEntity` is inserted in Room.
5. `TransactionSyncWorker` sends pending records through `ApiClient` with a device token.
6. The API resolves the user, validates the payload and deduplicates by hash and user.
7. A successful response lets Android mark the local records as synced; failures are retried.

Capture, queuing and successful synchronization are distinct states. A payment
visible on the phone is not proof that the backend received it.

## Email ingestion

`EmailIngestionService` resolves the current user and obtains messages through
`GmailApiClient` when OAuth is available, or the configured IMAP fallback.
`BankEmailParserDispatcher` selects a bank parser. A recognized financial operation
is converted to a transaction request and persisted through `TransactionService`.
Processed message IDs and the Gmail cursor support incremental ingestion.
`EmailSyncScheduler` invokes this workflow for connected users when enabled.

The current parsers check mailbox domains and confirmation patterns, and reject
ambiguous operation amounts. Surveys and rewards must not be stored as expenses.
Rejected messages can still be marked processed; supporting a new format can
require a controlled reprocessing plan.

## Dashboard and authentication

The dashboard uses Google OAuth and a JWT managed by `AuthContext` and the HTTP
service. Vite proxies local `/api` calls to the backend. Analytics and transaction
lists are scoped to the current user. Pairing obtains a separate device credential
and a server address reachable from the phone. See [contracts](contracts.md).

## Compatibility and current limitations

- Existing Android application ID, database and preference names, Docker identifiers and `wp_dev_` tokens retain legacy names. Preserve them unless a migration is planned; details are in [the Docker guide](../docker/README.md#branding-and-compatibility).
- The transaction hash is source-specific; the database index only deduplicates equal hashes for a user.
- Raw notification logs and processed email markers currently lack a user foreign key, and raw logs are not directly linked to transactions. Improving diagnostic ownership and provenance requires a separate implementation.
- The existing backend context test does not select an isolated test profile. See [verification precautions](development.md#backend-verification).
