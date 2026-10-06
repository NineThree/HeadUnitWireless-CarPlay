# Golf wireless preview

Base: DiPlay-Legacy-Android v0.2.7, commit c8884adcc75bfda3c134db63877bd6c6f83beb74.

The user changed the connection goal from wired USB to wireless and confirmed that the head unit can provide a Wi-Fi hotspot. The target remains the measured Android 4.2.2 / API 17 head unit (ATC AC822X, ARMv7, 1024x600, 873984000 bytes system RAM). The 4.4-compatible source is the starting point; it does not upgrade the head unit's operating system.

## Scope

Use the existing local authentication, Bluetooth iAP2 bootstrap and manual-hotspot AirPlay transport. Provide a small Chinese launch screen for hotspot credentials, paired iPhone selection, connect/disconnect and local diagnostics. Use H.264 at 30 fps and a reduced display size initially. Keep touch and basic playback. Microphone/Siri and automatic startup are disabled. All vendor-specific HUD, instrument-cluster, battery, ADB, icon, permission and debug-activity integration is deleted at the user's request.

The original reports show normal audio and five successful touch targets. The user confirms some video stages were normal and some corrupted. Hardware identity and frame counters alone cannot choose a good decoder/display path. Keep the initial display path reviewable and record decoder identity without calling APIs introduced after 17. Do not claim the corruption is fixed without a new on-car test.

## Implementation and checks

- [x] Build an independent source fork with API17 installation metadata and a separate package/signature.
- [x] Select AndroidX versions compatible with older Android and remove native-only I2C/hotspot readers from this wireless build.
- [x] Correct API18/19 accesses in the wireless/video path: Bluetooth adapter, charset constants, interface index and decoder name.
- [x] Add a compact launcher and fixed wireless/manual-hotspot profile; retain upstream protocol/session/reconnect code.
- [x] Build, run relevant protocol/profile tests, check legacy API lint and inspect v1 signature, DEX version, package and source archive (see VALIDATION.md and the delivery verification JSON).
- [x] Have the changed code reviewed independently and resolve material findings.

## Real-device acceptance

The release candidate is experimental until tested on this head unit and an iPhone. Confirm Android exposes its Bluetooth adapter and paired phone, hotspot interface/address detection, iAP2 authentication, iPhone Wi-Fi handoff, actual visible video, basic audio, touch, reconnect and longer operation. A hotspot toggle in the system UI alone does not establish Bluetooth RFCOMM access or successful CarPlay.

No report, hotspot password or phone data is uploaded. Local diagnostic exports must redact credentials. The source archive must exclude runtime authentication material and Android signing files.
