# fukbd-ex

Extensions for the fukbd keyboards, built on [FUTO Keyboard](https://github.com/futo-org/android-keyboard)
through our fork [zevix/android-keyboard](https://github.com/zevix/android-keyboard).

- `core/`: pure Kotlin. It reads device profiles (`schema/device-profile-v2.1.schema.json`), routes
  each physical key event to one profile by the device that sent it, and keeps per-device state.
- `schema/`: the profile JSON Schema, version 2.1.
- `profiles/examples/`: example profiles. They are illustrative and not measured on hardware.

Plans, requirements and acceptance checks live in
[zevix/fukbd-docs](https://github.com/zevix/fukbd-docs), project `docs/projects/261027_m0-foundation`.

## Build and test the core

    ./gradlew :core:test

This needs JDK 17 or newer. The Android apps are built through the fork (milestone M0, phase P4).

## License

Source-available, not open source: see `LICENSE` (draft) and `NOTICE`. Contributions need the
contributor agreement in `CLA.md`.
