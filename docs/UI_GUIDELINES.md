# UI development baseline

This document is the UI source of truth for new HappyTalky Phone and Wear work. It describes the **checked-in project baseline**, not a claim that every dependency version below is the newest available.

Before implementing a UI change, re-check the current Android guidance:

- Jetpack Compose: https://developer.android.com/compose
- Material 3 / Material 3 Expressive: https://developer.android.com/develop/ui/compose/designsystems/material3
- Material 3 Adaptive: https://developer.android.com/develop/ui/compose/layouts/adaptive
- Wear Compose Material 3: https://developer.android.com/reference/kotlin/androidx/wear/compose/material3/package-summary
- Wear notifications: https://developer.android.com/training/wearables/notifications
- Foreground-service / while-in-use restrictions: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start

## Checked-in baseline

Current `main` uses:

- Phone: Jetpack Compose + Material 3, Compose BOM `2026.09.00`.
- Phone: `androidx.compose.material3.adaptive:adaptive:1.3.0` is available as a dependency.
- Watch: Wear Compose Material 3 `1.7.0`.
- compile SDK 37, target SDK 36, JDK 17.
- Phone screenshot surface: 412 x 915 dp.
- Watch screenshot surface: 192 x 192 dp, 320 dpi, round.

A dependency being present does not mean every new screen must use every API. Prefer stable APIs already compatible with the project; do not introduce alpha dependencies only to imitate a newer visual style.

## Design model

Phone and Watch share product state, protocol semantics, brand identity and accessibility expectations. They **do not share page layout**.

### Phone

- Use Material 3 hierarchy, typography, shapes and motion as the default design language.
- Keep system bars / edge-to-edge behavior intentional rather than relying on incidental padding.
- Treat window size as variable. New layouts should be able to evolve through Material 3 Adaptive instead of hard-coding the 412 x 915 screenshot size.
- Keep CALL and TALK as the primary child-facing actions; TEXT remains secondary.
- Keep Phone TEXT message bodies natively selectable/copyable; sender labels, delivery metadata and timestamps remain outside the selection surface.
- Keep state feedback explicit for disabled, queued, ringing, connecting, live, reconnecting and failed states.
- Keep **Find Watch** secondary to CALL/TALK. BLE RSSI finding may show qualitative proximity and trend, but must not draw a direction arrow or label an exact physical distance.
- Preserve system theme light/dark behavior while keeping the HappyTalky brand identity stable.

### Watch

Do not shrink the Phone UI into a circle.

The wrist interaction loop should stay short:

~~~text
status / route -> primary action -> clear feedback
~~~

Use Wear Compose Material 3 primitives and round-screen spacing. Keep complex history management, explanations and setup on Phone unless they are necessary at the wrist.

Current Watch priorities are:

1. route/status;
2. CALL;
3. hold/release TALK;
4. unified Inbox for TEXT/TALK/CALL history and Message entry.

Watch TEXT rows reserve long-press for local read-aloud. Long-press must give haptic feedback and a compact visible speaking state without adding a permanent button. Horizontal swipe remains Delete, so gesture handling must preserve drag cancellation/touch slop rather than letting a slight swipe trigger speech. Communication audio has priority: Inbox exit, TALK playback/recording, and any non-idle CALL state stop TTS.

Crown/rotary scrolling and touch targets must be verified on the 192 dp round baseline, then on hardware when the change affects physical interaction.

## Priority CALL interaction invariant

Locked Priority CALL is a deliberate parent-to-Watch path and must remain visibly distinct from normal CALL:

- Phone starts it directly; there is no “wait five seconds, then escalate” flow.
- Watch does not expose Decline for the locked incoming request.
- Watch does not expose a normal END action for an active locked Priority call.
- While locked Priority is incoming, connecting, live, or reconnecting, the Watch replaces normal navigation with a dedicated non-interactive Priority screen. Inbox, TEXT compose/read-aloud, TALK recording/playback, Find Phone, and history actions are unavailable until the call leaves the locked state.
- Phone can cancel while pending and end once connected.
- Route/system failure can still terminate it.
- Actual live-audio attachment gives a short connection haptic on both endpoints; termination of a call that reached live audio gives a distinct end haptic. Reconnect attempts for the same call must not repeatedly vibrate.
- A background Data Layer listener must not directly start microphone capture or a microphone foreground service. The current locked-Priority background path is owned by AndroidX Core-Telecom; when Telecom accepts/answers the call, HappyTalky may start its PCM transport under that platform call lifecycle. If Telecom is unavailable, the visible-Activity fallback remains the supported fallback. Current device evidence belongs in [VALIDATION.md](VALIDATION.md).

Any UI change that weakens one of these rules is a behavior change and must be reviewed together with `docs/ARCHITECTURE.md` and the shared Priority policy/tests.

## Visual review contract

A successful compile is not sufficient for UI work.

At minimum:

1. render Phone and Watch screenshot suites;
2. inspect the actual images for clipping, overlap, hierarchy and state ambiguity;
3. cover dark/light where relevant;
4. cover large text / compact constraints for Phone changes;
5. cover the 192 dp round Watch baseline for Watch changes;
6. use a physical device when validating microphone/audio routing, crown behavior, system RemoteInput, notifications, lock-screen behavior or other platform-owned UI.

The CI commands are documented in [DEPLOY.md](DEPLOY.md).

## Documentation rule for future UI changes

If a UI change alters visible behavior, interaction order, platform constraints or supported states, update this file and the relevant canonical behavior document in the same PR. Screenshots are evidence of rendering; `ARCHITECTURE.md` remains the behavior contract.
