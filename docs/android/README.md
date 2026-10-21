# Android guide

Read [Android instructions](../../android/AGENTS.md) before editing.

| Work | Reference |
| --- | --- |
| Capture, ownership, recovery and synchronization | [Notification recovery](notification-recovery.md) |
| Implemented scope, tests and remaining acceptance | [Capture progress](capture-progress.md) |
| Ingestion and isolated test variants | [Architecture](../architecture/overview.md#android-ingestion) |
| Pairing and payload compatibility | [Contracts](../architecture/contracts.md) |
| SDK/build prerequisites and verification | [Development](../development/local.md#dashboard-and-android-verification) |
| APK delivery and capture-test commands | [Script catalogue](../../scripts/README.md) |

The capture runner lives only in
[scripts/android/run-capture-tests.ps1](../../scripts/android/run-capture-tests.ps1).
The [Gradle wrapper](../../android/gradlew.bat) remains next to its build files.
No installation is part of ordinary JVM verification.
