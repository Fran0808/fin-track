#!/usr/bin/env bash
set -euo pipefail

build_tools="$ANDROID_HOME/build-tools/36.0.0"
unsigned_apk=android/app/build/outputs/apk/release/app-release-unsigned.apk
asset=fintrack.apk

read_apk_package() {
    local badging
    badging=$("$build_tools/aapt" dump badging "$1")
    printf '%s\n' "${badging%%$'\n'*}"
}

read_version_code() {
    sed -n "s/.*versionCode='\([0-9][0-9]*\)'.*/\1/p" <<< "$1"
}

read_certificate() {
    "$build_tools/apksigner" verify --print-certs "$1" |
        sed -n 's/^Signer #1 certificate SHA-256 digest: //p'
}

package=$(read_apk_package "$unsigned_apk")
version=$(sed -n "s/.*versionName='\([^']*\)'.*/\1/p" <<< "$package")
version_code=$(read_version_code "$package")
if [[ "$package" != "package: name='com.financemanager.listener' "* ||
      ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ || ! "$version_code" =~ ^[1-9][0-9]*$ ]]; then
    echo '::error::Unexpected package name, versionName or versionCode in the release APK.'
    exit 1
fi
tag="android-v$version"

releases=$(gh api --paginate --slurp "repos/$GITHUB_REPOSITORY/releases?per_page=100")
release=$(jq -c --arg tag "$tag" '[.[][] | select(.tag_name == $tag)][0] // null' <<< "$releases")
if [[ "$release" != null ]] && jq -e '.draft == false' <<< "$release" >/dev/null; then
    if jq -e --arg asset "$asset" 'any(.assets[]; .name == $asset and .state == "uploaded")' <<< "$release" >/dev/null; then
        printf 'Release %s already contains %s.\n' "$tag" "$asset"
        exit 0
    fi
    echo '::error::This version already has a published release without its APK.'
    exit 1
fi

if [[ -z "${ANDROID_RELEASE_KEYSTORE_BASE64:-}" || -z "${ANDROID_RELEASE_STORE_PASSWORD:-}" ||
      -z "${ANDROID_RELEASE_KEY_ALIAS:-}" || -z "${ANDROID_RELEASE_KEY_PASSWORD:-}" ]]; then
    echo '::warning::Configure the four Android release signing secrets to publish the APK.'
    exit 0
fi

work=$(mktemp -d "$RUNNER_TEMP/fintrack-android-release.XXXXXX")
trap 'rm -f -- "$work/release.keystore" "$work/aligned.apk" "$work/fintrack.apk" "$work/version.json" "$work/previous.apk"; rmdir -- "$work"' EXIT
umask 077
printf '%s' "$ANDROID_RELEASE_KEYSTORE_BASE64" | base64 --decode > "$work/release.keystore"
"$build_tools/zipalign" -f -P 16 4 "$unsigned_apk" "$work/aligned.apk"
"$build_tools/apksigner" sign --ks "$work/release.keystore" --ks-key-alias "$ANDROID_RELEASE_KEY_ALIAS" \
    --ks-pass env:ANDROID_RELEASE_STORE_PASSWORD --key-pass env:ANDROID_RELEASE_KEY_PASSWORD \
    --v4-signing-enabled false --out "$work/$asset" "$work/aligned.apk"
certificate=$(read_certificate "$work/$asset")
if [[ ! "$certificate" =~ ^[a-fA-F0-9]{64}$ || "$(read_apk_package "$work/$asset")" != "$package" ]]; then
    echo '::error::The signed APK could not be verified against the built APK.'
    exit 1
fi

previous_tag=$(jq -r '.[][] | select(.draft == false and .prerelease == false)
    | .tag_name | select(test("^android-v[0-9]+\\.[0-9]+\\.[0-9]+$"))' <<< "$releases" | sort -V | tail -1)
if [[ -n "$previous_tag" ]]; then
    if [[ "$(printf '%s\n' "$previous_tag" "$tag" | sort -V | tail -1)" != "$tag" ]]; then
        echo '::error::Increase versionName for the next Android release.'
        exit 1
    fi
    gh release download "$previous_tag" --repo "$GITHUB_REPOSITORY" --pattern "$asset" --output "$work/previous.apk"
    previous_code=$(read_version_code "$(read_apk_package "$work/previous.apk")")
    if [[ ! "$previous_code" =~ ^[1-9][0-9]*$ ]] || (( version_code <= previous_code )); then
        echo '::error::Increase versionCode for the next Android release.'
        exit 1
    fi
    if [[ "$certificate" != "$(read_certificate "$work/previous.apk")" ]]; then
        echo '::error::The APK must use the same signing certificate as the previous Android release.'
        exit 1
    fi
fi

if [[ "$release" == null ]]; then
    existing_ref=$(gh api "repos/$GITHUB_REPOSITORY/git/matching-refs/tags/$tag" |
        jq -r --arg ref "refs/tags/$tag" '.[] | select(.ref == $ref) | .object.sha')
    if [[ -n "$existing_ref" && "$existing_ref" != "$GITHUB_SHA" ]]; then
        echo '::error::The existing Android release tag points to a different commit.'
        exit 1
    fi
    gh release create "$tag" --repo "$GITHUB_REPOSITORY" --target "$GITHUB_SHA" --draft \
        --title "FinTrack Android" --notes "APK FinTrack Android $version (código $version_code)."
elif [[ "$(jq -r '.target_commitish' <<< "$release")" != "$GITHUB_SHA" ]]; then
    echo '::error::Retry the workflow run that created the draft for this Android version.'
    exit 1
fi

apk_sha256=$(sha256sum "$work/$asset" | awk '{print $1}')
jq -n \
    --arg versionName "$version" \
    --argjson versionCode "$version_code" \
    --arg downloadUrl "https://github.com/$GITHUB_REPOSITORY/releases/latest/download/$asset" \
    --arg sha256 "$apk_sha256" \
    '{versionName: $versionName, versionCode: $versionCode, downloadUrl: $downloadUrl, sha256: $sha256}' \
    > "$work/version.json"

gh release upload "$tag" "$work/$asset" "$work/version.json" --repo "$GITHUB_REPOSITORY" --clobber
gh release edit "$tag" --repo "$GITHUB_REPOSITORY" --draft=false --latest
