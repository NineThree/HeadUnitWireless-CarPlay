# Contributing

Reproducible bug reports, API 17 compatibility fixes, documentation, and tests are welcome.

- Read `README.md`, `docs/BUILD.md`, and `docs/THIRD_PARTY_NOTICES.md` first.
- Use the build/check commands in `README.md`. Do not commit accessory-authentication files, Android signing files, personal reports, or generated APKs.
- Keep Golf-specific defaults in the Golf edition. The universal build must use the active display area and must not assume a particular vehicle or manufacturer-specific key code.
- Never guess what an OEM key code means. Preserve an unmapped state until a signal is observed and its use is confirmed.
- Keep changes compatible with API 17 and include focused tests for connection, media, key, or decoder changes.
- Review diagnostic reports for addresses, hotspot details, pairings, and personal information before posting publicly.

Contributions are licensed under this repository’s GPLv3 license.
