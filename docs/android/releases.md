# Android releases

[Android Release](../../.github/workflows/android-release.yml) runs for Android
changes pushed to `main` and can also be run manually on `main` from **Actions**.
It runs the release unit tests and builds the APK before signing. A new version
gets a release named `android-v<versionName>` with the file `fintrack.apk`.
Published versions are skipped on subsequent runs. Increase both `versionName`
and `versionCode` in [app/build.gradle.kts](../../android/app/build.gradle.kts)
when changing the app for a new release.

## Set up signing once

Use the existing Android release keystore if you have one. For a first release,
create and back up a release keystore outside the repository. Android Studio's
**Generate Signed App Bundle or APK** action can create it. With JDK 21 on
Windows, the equivalent command prompts for the store password and certificate
details interactively.

```powershell
keytool -genkeypair -storetype PKCS12 -keystore "$env:USERPROFILE\fintrack-release.p12" -alias fintrack -keyalg RSA -keysize 3072 -validity 10000
```

In the GitHub repository, open **Settings → Secrets and variables → Actions**.
Create these repository secrets and keep the keystore backup and passwords in
your own secure storage. The workflow warns and skips publication when a secret
is missing. For PKCS12, the store and key passwords are usually the same.

| Secret | Value |
| --- | --- |
| `ANDROID_RELEASE_KEYSTORE_BASE64` | Base64 of the release keystore file |
| `ANDROID_RELEASE_STORE_PASSWORD` | Keystore password |
| `ANDROID_RELEASE_KEY_ALIAS` | Alias of the signing key, such as `fintrack` |
| `ANDROID_RELEASE_KEY_PASSWORD` | Signing key password |

With an authenticated [GitHub CLI](https://cli.github.com/), send the binary file
as Base64 from PowerShell without printing it or creating a text copy. The other
secrets can be entered in the GitHub settings or via interactive `gh secret set`.

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE\fintrack-release.p12")) |
  gh secret set ANDROID_RELEASE_KEYSTORE_BASE64 --repo Fran0808/fin-track
```

When the secrets are ready and the workflow is on `main`, run **Android Release**
from **Actions** to publish the current version. The workflow verifies the signed
APK and compares its certificate and version code against the last Android
release. If publication fails after creating a draft, rerun the failed workflow
for the same commit to finish it.

Android needs the same signing certificate for in-place updates. An installed
debug APK signed with a different certificate needs a fresh installation to
switch to the release APK. Sync any pending movements before removing the old
installation.

## Link the installer in FinTrack

After the first release, set the dashboard's `VITE_ANDROID_APK_URL` to
`https://github.com/Fran0808/fin-track/releases/latest/download/fintrack.apk`.
Rebuild the dashboard once. Future releases published as latest update the target
of that URL automatically. See [deployment](../development/deployment.md#android-installer-download).
