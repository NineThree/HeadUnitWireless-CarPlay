# Universal edition 0.2.7-universal.1

- Minimum Android 4.2 (API 17), version code 107.
- APK: `HeadUnitWireless-CarPlay-0.2.7-universal.1.apk`
- APK SHA-256: `9c7b484fefeaf7aa0d38643758fc0ce0029c2fbdba3705f72c328bb553db8ddd`
- Features: balanced/smooth/native display quality, up to 30 fps; optional recovery for an already enabled legacy hotspot with a missing network interface; explicit learning of Android key events.
- Local verification: 315 automated tests passed; both release lint variants reported zero errors.
- Device acceptance is limited to the Golf unit used during development. Compatibility with other units, physical key delivery, hotspot recovery, call control, and sustained smoothness remain unverified.

**Distribution note:** At the maintainer's direction, this APK is distributed as a GitHub Release asset. It contains an experimental accessory certificate/key that anyone with the APK can extract and reuse. The material came from the public Legacy v0.2.7 preview APK; it is not a newly issued Apple credential for this project and may stop working. The source repository excludes it, and `.gitignore` prevents the APK from entering the source commit.
