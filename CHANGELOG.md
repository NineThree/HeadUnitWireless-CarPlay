# Golf Wireless changes

## 0.2.7-golf.7 / 0.2.7-universal.1 — 2026-10-06

- Recover an already-enabled API17–22 hotspot once when its matching saved AP lacks an interface for5seconds. Preserve system configuration, respect owner-off/cancel/transitions/deadline, share retry budget and report requested versus observed recovery. Add an independent default-on recovery option.
- Add explicit OEM code/scan learning for previous/next/phone/voice, without guessed numeric mappings. Require Save, protect navigation/volume/printing keys, reject duplicate assignments, keep diagnostics control-free and refresh cached mappings.
- Replace video queue scans/nodes with counters and an array deque, reuse video output metadata and legacy input-buffer arrays. Decode short bursts through the reference chain; suppress stale outputs only when a newer known output is ready, always display the last drained output, retain bounded750ms resync and existing error recovery. Guard unsigned NAL length overflow.
- Reload saved settings before each new Host connection. Build separate Golf update and generic Android4.2+ APKs with edition resources, independent package data and actual-canvas quality labels. Both retain H.264/30fps, existing voice and vendor-component removal.
- Local tests, lint and binary inspection are separate from pending physical key, hotspot and smoothness acceptance; other firmware is not deemed compatible solely by API level.

## 0.2.7-golf.6 — 2026-10-06

- Add native100% display quality, matching1024x600 when the full canvas is available; preserve balanced80% and smooth60% choices and all startup/voice/connection preferences. Keep30fps; no60fps mode is added.
- Forward focused-window media keys through the existing Android media route and register the API17 ComponentName receiver before the first music stream. Reclaim media registration on playback/host resume.
- Share bounded event deduplication between window and broadcast. A phone key short release requests Telephony HID Hook (index1); at least800ms requests Drop (index3), with no intermediate answer. ENDCALL requests Drop directly; keep Siri on the voice key.
- Add a metadata-only key-test dialog and dedicated bounded key history in exported reports. Identify queueing separately from successful HID writes; neither is an iPhone acceptance acknowledgment.
- Verify actual window/broadcast-to-encrypted-HID behavior, cancellation, repeats, reconnect ownership and diagnostic history isolation. Direction-wheel delivery, Hook/Drop interpretation and native-resolution performance still require on-car acceptance.
- Restore the edited source tree to the preceding delivered archive by moving62 unchanged upstream vendor/ADB/cluster/hotspot source leftovers outside the build; no deleted vendor feature is reintroduced.

## 0.2.7-golf.5 — 2026-10-05

- Add three independently saved, default-off options: boot opening, automatic connection after opening and experimental Siri/microphone.
- A boot broadcast opens only the launcher; automatic connection waits at most90seconds for the saved phone, Bluetooth and hotspot, then makes one attempt. Manual start/stop and unsaved hotspot edits cancel automation.
- Enable microphone only when selected and permitted; permission denial continues projection without microphone. API17-28 voice negotiation excludes unavailable Opus while ordinary video/music settings remain unchanged.
- Bind uplink to the CarPlay local interface, support legacy AudioRecord, require successful recording state, and fall back once from unsupported voice source to MIC.
- Stop microphone on stream teardown and session loss, reject stale receive callbacks and starts after close, and release partial socket initialization on failure.
- Export preference, microphone availability and count/level diagnostics without recording audio files. Physical microphone routing, echo and voice-time performance remain pending device tests.

## 0.2.7-golf.4 — 2026-10-05

- Default to owner-approved balanced H.264/30fps at80% canvas scale (820x480 on1024x600); preserve original60% smooth quality as a saved choice.
- Add display settings with cancel/save and next-connection application; preserve hotspot and pairings on upgrade.
- Use legacy fullscreen flags on API17–19, avoiding temporary navigation hiding on API17/18; retain modern insets handling.
- Provide optional64dp top-bar avoidance with identical video and touch bounds, and use an opaque TextureView.
- Replace the remaining branded return-car image with a neutral car PNG and retain its SVG source.
- Export selected quality, avoidance, fullscreen requests and layout; preserve the working golf.3 video and wireless protocol paths.
- Field evidence now confirms visible CarPlay and basic function on golf.3. New-resolution performance and OEM bar behavior remain pending physical testing.

## 0.2.7-golf.3 — 2026-10-05

- Use a basic video format on API17–20, letting the driver choose its input buffer allocation instead of forcing 8 MiB per input buffer.
- Only request decoder priority on API23 and newer.
- Give each decoder candidate its own format and codec-specific buffers.
- Export the failing operation, decoder, exception message, AVC profile/level and first successfully queued input.
- Add five renderer regressions for legacy format rejection, fallback CSD preservation, asynchronous error diagnostics and old input-buffer feeding/capacity reports.
- Field reports confirm golf.2 wireless authentication and video receipt; visible video remains unverified for this revision.

## 0.2.7-golf.2 — 2026-10-05

- Correct Android 4.2 multicast port sharing by enabling address reuse before binding.
- Close multicast sockets when initialization fails, allowing later reconnects.
- Record the service-discovery stage and show a useful network failure message.
- Keep the existing manual hotspot, Bluetooth iAP2, AirPlay, touch and audio protocol paths.
- Include the narrowly patched JmDNS 3.6.3 sources and three regression tests.

## 0.2.7-golf.1 — 2026-10-05

- Initial local adaptation for the measured API17 aftermarket Golf head unit.
- Separate Chinese launcher, manual hotspot configuration and diagnostic export.
- H.264 at 30 fps and reduced display size; microphone and automatic startup disabled.
- Delete manufacturer-specific HUD, instrument, battery, ADB and debug integrations.
