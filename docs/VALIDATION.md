# Validation status

This file is the single current-state source of truth for **what HappyTalky has actually been validated on**.

A green CI build proves compilation/tests/screenshot rendering. It does not prove radio behavior, microphone routing, haptics, speaker output, system notifications, background execution or other hardware/platform-owned behavior.

Use [DEPLOY.md](DEPLOY.md) for the acceptance procedure. Update this file only when new evidence changes a status.

## Status legend

- **Passed** — the stated scope has been exercised successfully.
- **CI only** — automated/software validation exists, but the hardware behavior is not claimed as physically verified.
- **Retest pending** — implementation changed after the previous evidence, so a targeted device pass is still required.
- **Experimental** — proof-of-concept behavior; do not treat as product-ready.

## Current matrix

| Area | Automated / CI | Physical device | Notes |
| --- | --- | --- | --- |
| Phone + Watch debug build | **Passed** | N/A | Core/Wear unit tests, both APK builds and Compose screenshot rendering run in Android CI. |
| Google Play release-bundle build | **Passed** | N/A | CI builds both release AABs with one ephemeral CI-only signing identity to exercise release configuration. Production signing material is never used or stored in CI. |
| Normal CALL / TALK / TEXT core flows | **Passed** for checked-in automated coverage | Manual acceptance remains release-dependent | Use the full device checklist in DEPLOY; do not infer audio/radio quality from CI. |
| Locked Priority CALL, including background Core-Telecom path | **Passed** for policy/build/screenshot coverage | **Retest pending** | A physical test found Watch microphone capture could be silenced after pressing Home while downlink playback continued. The runtime foreground-service policy now keeps `microphone` and adds `phoneCall` for Telecom-managed Priority; bidirectional background audio and reconnect must be retested on hardware. |
| Find Watch (Phone -> Watch BLE RSSI) | **Passed** | **Passed — basic end-to-end scope** | Qualitative proximity/trend only; no exact distance or direction claim. |
| Find Phone (Watch -> Phone BLE RSSI) | **Passed** | **CI only** | Physical Watch-to-Phone BLE acceptance is still required. |
| Remote CALL: Phone on carrier data, Watch remote on Wi-Fi | **Passed** for route policy/unit/build coverage | **Retest pending** | The previous policy incorrectly blocked local cellular routes. After the fix, a reachable remote Data Layer peer is CALL-capable; the real remote topology still needs a post-fix device retest. |
| Watch TEXT read-aloud | **Passed** for toggle policy, build and screenshot state | **CI only** | Real Watch speaker output, long-press haptic feel and swipe-vs-long-press interaction still need hardware acceptance. |
| Wear captive-portal PoC | **Passed** for parser/build scope | **Experimental / hardware pending** | Main open question is whether the target Watch keeps an unvalidated captive Wi-Fi network attached long enough for the flow to finish. |

## Evidence boundaries

A physical pass is intentionally narrow:

- it applies to the target hardware/software pair that was exercised;
- it does not certify all Android phones, Wear OS watches, carriers, routers or TTS engines;
- a later change to the relevant protocol, permissions, audio route, BLE behavior or background execution can invalidate the previous evidence.

When a change invalidates evidence, mark the row **Retest pending** in the same PR rather than leaving an old “Passed” claim in README or architecture prose.

## Documentation rule

Mutable validation claims belong here.

Other canonical documents should link to this file instead of repeating statements such as “hardware validated”, “not yet tested” or model-specific pass/fail status. Historical documents may preserve old evidence only when clearly labeled as historical.
