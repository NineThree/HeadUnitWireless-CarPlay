# Building Golf and universal editions

Requirements: JDK 25 (the included Gradle daemon criteria request it), Android SDK platform 37.0, build-tools 36.0.0, the included Gradle 9.5.0 wrapper. No NDK is needed for this manual-hotspot/local-authentication build. Installation metadata is minSdk 17 and targetSdk 22; this locally installed head-unit preview is not prepared for Google Play publication.

The local legacy-mdns Java module replaces the org.jmdns:jmdns binary with the same 3.6.3 sources and an Android 4.2 multicast socket compatibility patch. Keep this module in the source distribution; see legacy-mdns/README.md. ASM is used only by the regression tests to reproduce the old constructor ordering and is not an application dependency.

## Verification and source-only build

```sh
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :mobile:lintGolfDebug :mobile:lintUniversalDebug :mobile:assembleDebug
```

The source-only APK contains no accessory identity unless an explicit external input is set. It can show the launcher, but its setup error is intentional without authentication provisioning. All legacy API lint checks remain enabled and include library sources. Only ExpiredTargetSdkVersion (the store publication policy) is excluded for this local-car package.

## Standalone car-test package

Set DIPLAY_AUTH_ASSETS_DIR to an external directory containing offline-mfi/identity.pk8 and offline-mfi/certificate.p7b. These are runtime inputs, not source code, and must not be committed or included in source ZIPs. The standalone build tasks reject missing/empty files. The ordinary credential guard rejects all other credential containers in APK assets. See THIRD_PARTY_NOTICES.md for the experimental identity's origin and limitations.

```sh
DIPLAY_AUTH_ASSETS_DIR=/absolute/path/to/runtime-assets ./gradlew :mobile:assembleStandaloneDebug
```

For a signed release, set ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS and ANDROID_KEY_PASSWORD locally for your protected APK signing key, then run:

```sh
./gradlew :mobile:assembleStandaloneRelease :mobile:lintGolfRelease :mobile:lintUniversalRelease
```

Outputs: mobile/build/outputs/apk/golf/release/mobile-golf-release.apk (com.legacy.golfwireless,0.2.7-golf.7/107) and mobile/build/outputs/apk/universal/release/mobile-universal-release.apk (com.legacy.headunitwireless,0.2.7-universal.1/107). Debug builds add .preview to each applicationId. Flavor resources select name/OEM label; the same common/shared sources implement both. Each package has its own app data and generated AirPlay identity. On one head unit, use only one active CarPlay session. Keep the same signing key for later updates. The selected experimental authentication files are extractable from the standalone APK; Android signing keys remain outside it.

Before delivery verify APK v1 signature at min SDK17, DEX035, install metadata, local runtime asset equality, absent vendor classes/assets/permissions and source archive exclusions. The removed automotive, vendor UI/debug and hotspot-backend modules are not needed to build the preview.
