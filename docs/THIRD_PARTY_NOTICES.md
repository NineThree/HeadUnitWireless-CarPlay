# Credits and license notices

This adaptation starts from DiPlay-Legacy-Android v0.2.7 by programmerguohuajing (https://github.com/programmerguohuajing/DiPlay-Legacy-Android), commit c8884adcc75bfda3c134db63877bd6c6f83beb74. DiPlay itself modifies xcertplay by shilapi (https://github.com/shilapi/xcertplay); GNU GPL version 3 is retained in LICENSE. Upstream credits LIVI and Showcase for protocol research. Existing source attribution is preserved.

The earlier DiAuto-derived launcher and static website were removed in this adaptation. The new GolfWirelessActivity uses ordinary Android views. The historical DiAuto license notice is retained under docs/licenses for provenance.

The neutral return-car PNG and its docs/assets/golf-car-home.svg source were created for this adaptation and follow the project GPLv3 license. They replace the previous branded default.

The unmodified CarPlay icon originates from Apple's developer asset site, https://developer.apple.com/assets/elements/icons/carplay/carplay-96x96_2x.png. CarPlay and that icon are Apple marks/assets and are not relicensed as project code. No Apple certification or approval is asserted.

Runtime dependencies: AndroidX and Kotlin (Apache-2.0), Bouncy Castle 1.79 (Bouncy Castle license), JmDNS 3.6.3 (Apache-2.0) and SLF4J (MIT). Dependency license files are retained under docs/licenses/dependencies. JmDNS is now built from its included sources under legacy-mdns, with only openMulticastSocket modified for old Android port reuse and failed initialization cleanup. The original source artifact hash and platform reference are recorded in legacy-mdns/README.md; all original source license headers are retained. Test-only ASM is licensed under BSD-3-Clause and is absent from the APK.

The standalone preview explicitly reuses the runtime accessory certificate/key from the public Legacy v0.2.7 preview APK, whose origin is public Carlinkit C2Air Allwinner V821 firmware per the upstream notice. This is experimental authentication material, not newly issued Apple credentials for this project. The supplied golf.2 reports confirm acceptance for the tested phone/session; continued acceptance remains unverified. The source archive excludes these runtime files and all separate Android signing keys.

Removed vendor maneuver icons and their integration are absent from the build and source. No vendor-specific navigation service, instrument display, battery report, ADB helper, permission or debug activity is retained.
