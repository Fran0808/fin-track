# Android instructions

These instructions apply to Android changes and supplement [the root agreements](../AGENTS.md).
Their sources and review triggers are in the [instruction register](../docs/development/instruction-maintenance.md#instruction-register).

## Boundaries

- Kotlin, Jetpack Compose, Room, WorkManager and Retrofit are the established stack. Versions and SDK levels live in Gradle files.
- Filter packages through `NotificationParserDispatcher` before inspecting notification contents. The currently authorized package is `com.bcp.innovacxion.yapeapp`.
- Keep notification parsing separate from Room persistence and network synchronization.
- Use decimal amounts and preserve the transaction hash across retries.
- Persist captured transactions before synchronization. Mark entries synced only after a successful server response.
- Keep server URL and pairing preferences in `PairingPreferences`; attach `X-Device-Token` through `ApiClient`.
- Preserve existing application ID, Room database and preferences names unless a migration is explicitly planned; see [architecture](../docs/architecture/overview.md).
- Review [contracts](../docs/architecture/contracts.md) when changing request fields, flow types or date serialization.

## Application version and delivery

- Each change set that changes the application must increment `versionCode` monotonically and update `versionName` in `app/build.gradle.kts` before APK delivery.
- Use semantic versions: PATCH for fixes/maintenance, MINOR for compatible features, MAJOR for breaking changes.
- Never reuse a version code for different application contents. Rebuilding unchanged sources needs no new version.
- Documentation-only changes that do not alter the application need no version increment.
- Verify the built APK's version name and code against Gradle, and report both, its path and the changes when delivering it.
- Check both installed version values before diagnosing a device; an application name does not identify the installed build.
- Installing an APK requires authorization in the task; preserving local transactions and pairing is part of the delivery plan.

## Verification

- From `android`, run `./gradlew testDebugUnitTest assembleDebug` or `./gradlew.bat testDebugUnitTest assembleDebug` on Windows for application changes.
- Parser changes need real-format, anonymized positive and negative test cases.
- An ADB shell-posted notification is not a notification from the authorized Yape package. Do not weaken the package filter to make a simulator pass.
- Distinguish a queued worker from a successful HTTP sync and persisted backend entry.
- See [development](../docs/development/local.md) for current script limitations and environment prerequisites.
