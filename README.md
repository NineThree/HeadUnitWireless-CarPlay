# HeadUnit Wireless CarPlay (Android 4.2+)

An experimental wireless CarPlay receiver fork based on DiPlay-Legacy-Android v0.2.7, adapted for older Android head units. The universal build starts at Android 4.2 (API 17), scales display quality to the active screen, and requests H.264 at 30 fps.

**Release:** `0.2.7-universal.1` (version code 107). This repository contains the full source, GPLv3 license, third-party notices, setup/build instructions, GitHub issue form, and source-check workflow. The APK is published as a separate [GitHub Release asset](https://github.com/NineThree/HeadUnitWireless-CarPlay/releases/tag/0.2.7-universal.1); it is excluded from Git source history. The APK contains extractable experimental accessory-authentication material; see `docs/THIRD_PARTY_NOTICES.md`.

## Features and limits

- Uses the head unit’s own Wi-Fi hotspot and Bluetooth for wireless connection.
- Offers balanced (80%), smooth (60%), and native (100%) display quality, up to 30 fps.
- Can attempt one recovery when an already-enabled Android 4.2–5.1 hotspot never exposes its network interface.
- Can learn steering-wheel key signals when Android delivers those events to the app.
- Retains the existing optional Siri/microphone path.

Android 4.2+ alone does not guarantee compatibility: firmware, hotspot behavior, Bluetooth RFCOMM, and video decoding vary by device. The universal edition has not been validated across other head units. Key learning cannot help when firmware does not deliver the physical-key event to normal Android apps.

## Install

Download the APK from [Releases](https://github.com/NineThree/HeadUnitWireless-CarPlay/releases/tag/0.2.7-universal.1), then follow [installation instructions](安装说明.md). Enable the head unit’s Wi-Fi hotspot, pair the iPhone in Android Bluetooth settings, then enter the hotspot name/password and select the phone in the app. Keep Wi-Fi and Bluetooth enabled on the iPhone.

See [installation instructions](docs/INSTALL.md) and [feature/compatibility notes](docs/RELIABILITY_PERFORMANCE.md).

## Build from source

Requirements: JDK 25, Android SDK platform 37, Build Tools 36.0.0. The Gradle 9.5.0 wrapper is included.

```sh
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest \
  :mobile:lintGolfDebug :mobile:lintUniversalDebug \
  :mobile:assembleGolfDebug :mobile:assembleUniversalDebug
```

Source/debug builds do not include the external accessory-authentication files used for the supplied standalone APK. Instructions for a separately provisioned standalone build are in [`docs/BUILD.md`](docs/BUILD.md). Those files are not in this repository. Never copy accessory credentials or Android signing files into source control.

## License and status

The project retains the upstream GNU GPLv3 license; see [`LICENSE`](LICENSE) and [`docs/THIRD_PARTY_NOTICES.md`](docs/THIRD_PARTY_NOTICES.md). This is an independent community adaptation, not an Apple product, and it does not claim Apple certification.

315 automated tests and release checks passed locally for this version. Physical hotspot recovery, key delivery, call control, sustained smoothness, and compatibility with other head units remain to be tested on those devices. See [`docs/VALIDATION.md`](docs/VALIDATION.md).

[中文说明](README.zh-CN.md) · [Build guide](docs/BUILD.md) · [Installation](docs/INSTALL.md) · [Security policy](SECURITY.md)
