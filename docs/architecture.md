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
3. A bounded snapshot and its capture-time ownership are committed to the Room capture inbox before parsing. Notification removal/reconciliation is ordered through that inbox.
4. A bank-specific parser produces a structured result; the inbox processor atomically completes the observation and inserts a `LocalTransactionEntity` when unambiguous. Active notifications are recovered on connection and foreground entry. Reused active sources can require review; legacy rows retain their hashes and ownership requirements.
5. `TransactionSyncWorker` sends only the current scope's pending records through `ApiClient` with a fixed device token.
6. The API resolves the user, validates the payload and deduplicates by hash and user.
7. A successful response confirming the hash lets Android mark the record as synced; transient failures receive bounded retries. See [Android recovery](android-notification-recovery.md) and [capture delivery](android-capture-implementation.md).

Capture, queuing and successful synchronization are distinct states. A payment
visible on the phone is not proof that the backend received it.

`CaptureSourceRegistry` selects the runtime parser set at build time. The normal
`debug` and `release` builds accept only the authorized Yape package. The separate
`captureTest` build uses application ID `com.financemanager.listener.capturetest`,
accepts only `com.fintrack.capture.emitter`, disables backup and has no Internet
permission. It uses its own app-private database and preferences. No runtime
setting can enable synthetic sources in the normal app.

`android/emitter` is a debug-only Android application with fixed synthetic
notification scenarios and no Internet permission. A signature permission protects
its command receiver. Instrumented delivery tests post through Android's notification
manager and inspect persisted results; they do not inject callbacks into the listener.
The serial-bound test runner accepts only a disposable emulator named
`FinTrackCapture`. These tests validate the shared capture pipeline, not compatibility
with the current provider application or successful backend synchronization.

## Email ingestion

`EmailIngestionService` resolves the current user and obtains messages through
`GmailApiClient` using only that user's Google OAuth credentials. An absent or
expired authorization requires reconnecting Google; authenticated ingestion never
falls back to the globally configured IMAP mailbox.
`BankEmailParserDispatcher` selects a bank parser. A recognized financial operation
is converted to a transaction request and persisted through `TransactionService`.
Processed message keys combine the user ID and Gmail message ID. Gmail pages are
visited completely; a failed page, detail download or processing step prevents
cursor advancement. Successful movements survive a partial run and retries deduplicate.
`EmailSyncScheduler` invokes this workflow for connected users when enabled.

The current parsers check mailbox domains and confirmation patterns, and reject
ambiguous operation amounts. Surveys and rewards must not be stored as expenses.
Rejected messages can still be marked processed; supporting a new format can
require a controlled reprocessing plan.

## Dashboard and authentication

The dashboard uses Google OAuth and a JWT managed by `AuthContext` and the HTTP
service. OAuth state is bound to a temporary browser session; a single-use code in
the return fragment is exchanged for the JWT using that same session. Production
uses one HTTPS origin for the dashboard and API. Vite proxies local `/api` calls
to the backend. Analytics and transaction
lists are scoped to the current user. Pairing obtains a separate device credential
and a server address reachable from the phone. See [contracts](contracts.md).

## Compatibility and current limitations

- Existing Android application ID, database and preference names, Docker identifiers and `wp_dev_` tokens retain legacy names. Preserve them unless a migration is planned; details are in [the Docker guide](../docker/README.md#branding-and-compatibility).
- The transaction hash is source-specific; the database index only deduplicates equal hashes for a user.
- Raw notification logs and processed email markers currently lack a user foreign key, and raw logs are not directly linked to transactions. Improving diagnostic ownership and provenance requires a separate implementation.
- Full-context tests require explicit disposable database URLs and disable email scheduling. See [verification precautions](development.md#backend-verification).
