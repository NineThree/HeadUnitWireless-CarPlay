# Evidence for the physical target

User-supplied reports: headunit-report-20261004-202253-659 and headunit-report-20261005-001738-387, initially read from KINGSTON/HeadUnitCheck. The volume was no longer mounted later; no raw report is included in this source archive.

Measured OS is Android 4.2.2, SDK 17. Device reports AC822X / ATC, Autochips ac83xx, ARMv7 with NEON, 1024x600 at 160 dpi. Total RAM is 873984000 bytes; maximum application heap is 192 MiB. Firmware Y9046MX-F01CJ2-MXP-V4.4.14R7-CN is a firmware label and is not evidence of Android 4.4.

The user confirmed the head unit can enable its own portable Wi-Fi hotspot and set its name and password. Follow-up Golf wireless reports on 2026-10-05 establish that Android exposes an enabled Bluetooth adapter and one bonded device. The later golf.2 reports confirm RFCOMM access and the iPhone bootstrap for this particular head unit and phone.

The audio probe was audible/normal (44.1 kHz PCM). Five touch targets succeeded; multitouch was not established. Only a Kingston USB disk was attached during the USB probe, so no iPhone USB result exists.

The normal MTK AVC decoder produced about 299/300 output buffers for the 800x480 sample. Its output format reported a 1920 stride and 1088 slice height. The mtkwfd component failed allocation. The software test had only the remaining eight seconds of a shared budget for a ten-second clip, so its partial frame count is not a complete independent performance result. The user later clarified that some video stages looked normal and some corrupted; the exact good decoder/display combination remains unknown.

The detector's IPv6 socket failure does not establish that the kernel lacks IPv6, especially because that detector did not request INTERNET. This fork explicitly follows the actual AirPlay session address for media, event and timing listeners rather than requiring an IPv6 wildcard on an IPv4 hotspot.

The follow-up reports from 15:11, 15:18 and 15:19 confirm golf.1 installed and started on API17. Once enabled, the manual hotspot was found as ap0/IPv4; the AirPlay TCP7000 listener attached. The next service-discovery step repeatedly failed with EADDRINUSE before the Bluetooth iAP2 stage, and the photo shows the retry screen. AuthenticationReady records only local provider readiness, not successful iPhone authentication. See CONNECTION_FIX.md for the golf.2 repair and the remaining real-device acceptance.

The golf.2 reports exported at 20:14:21 and 20:15:32 and photo IMG_7541 show a new stage: wireless iAP2 authentication was accepted, the type-130 tunnel became ready, Bluetooth handoff completed, and the type-110 screen stream was received. The phone identifies as iPhone15,2, iOS27.2; requested video is H.264 614x360 at30fps. Normal MTK AVC startup repeatedly throws IllegalStateException; the Google AVC fallback starts then also errors. Every displayed-frame statistic remains0fps, while video is received continuously with no recorded forward sequence gaps. A few touch sends are recorded, but successful interaction with a visible phone UI is not established. The photo shows blank content. These reports did not include the exact failed decoder operation or AVC profile, so the reason for the driver's rejection is still a hypothesis. Golf.3 provides a narrower format and operation-level diagnostics; see VIDEO_FIX.md.

The golf.3 reports exported at21:31:34 and21:36:28 and IMG_7545 now establish visible CarPlay; the user confirms connection and basic function. After excluding older sessions, the21:xx logs contain no decoder failures, successful normal MTK decoding of614x360, a1MiB input buffer, AVC profile100/level30, and matching receive/render rates on most samples. One250ms backlog recovery occurred. The logs and photo do not establish the specific cause of the mild stutter/ghosting the user reports, nor a30-minute endurance result. The photo clearly shows an OEM top bar overlapping the first row and a remaining branded return-car icon. The user selected balanced quality for golf.4; see DISPLAY_FIX.md for implementation and outstanding on-car acceptance.

The October6 golf.5 report exported at10:56:34 separates saved quality from the current session. Its current10:48 layout is1024x600, with80% negotiation and an820x480 normal MTK AVC decoder; the header saved smooth for the next connection. Decoder stride1920/slice1088 is allocation layout, not physical resolution. Current receive/render statistics track each other and sometimes approach30fps; static frames do not establish inability to handle30fps. Native1024x600 adds56% pixels per frame relative to820x480, without establishing aCPU percentage.

The user confirms Siri and microphone are normal and other basic functions work on golf.5. Phone and previous/next wheel buttons have no effect in projection, but work in the original music/Bluetooth-phone applications. IMG_7551 is a photo of the steering wheel, not the projection screen: it shows one phone button, a separate voice button and previous/next keys. No Android key code, scan code or MCU event has yet been measured. Golf.6 adds standard input forwarding and a scoped key-test diagnostic. Physical button delivery, telephony HID acceptance, native-resolution performance, cold boot and long-session endurance remain separate acceptance gates. The user cancelled60fps; the fork keeps30fps.


## October6 golf.6 follow-up,12:28–12:40

Four user reports were copied from KINGSTON and retained outside the source archive.12:34:41 and12:35:05 share the same12:30:36.923/pid21815 session; the longer report is counted once. Current native1024x600/H.264/30fps decoding uses OMX.mtk.video.decoder.avc. The user confirms native quality is basically smooth, while physical buttons still do not respond. This advances native-resolution evidence but not key compatibility.

The unique12:30 session records19 recoveries, all caused by the old250ms video backlog threshold. No current queue-overflow, input-stall or decoder-exception messages were observed in that session. One sample has receive28.1fps/render21.0fps with3recoveries. Static-screen gaps and touch-to-next-frame measurements do not independently establish persistent latency or CPU load.

Retained12:23 and12:36 sessions show missing AP interface waits of60seconds; automatic retry observed hotspot off, and after manual off/on ap0 reappeared. This motivates one bounded recovery of an already-enabled matching legacy AP, not unconditional hotspot activation or proof every DHCP/Wi-Fi failure is repairable.

Key diagnostics contain239/scan563 and236/scan560, plus BACK4/scan158 and VOLUME_MUTE164/scan113. Standard next/previous/CALL is not present. The reports do not label which physical key produced each OEM signal; no role is assigned without explicit owner learning. OEM Bluetooth phone/music working remains distinct from ordinary app input delivery and iPhone telephony acceptance.

The12:39 report also stores native preference while showing an older60%/614x360 negotiation. Golf.7 reloads persisted settings before a new Host connection; current-session diagnostics remain authoritative rather than preferences alone.

Golf.7/universal.1 have no physical acceptance yet: automatic AP recovery, learned-button execution, retained voice/call path, smoothness/endurance and all other car firmware require new-device reports. Current-host tests and Java queue benchmarks are not real-device measurements.
