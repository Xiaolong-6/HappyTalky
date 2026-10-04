# HappyTalky

Current `main` app build: **0.4.1** (`versionCode 7`) for both Phone and Watch.

HappyTalky is a deliberately simple Android + Wear OS companion app for a paired phone and Pixel Watch.

Its primary child-facing communication actions are:

- **CALL** — synchronous live two-way voice when the current phone/watch route is suitable for streaming.
- **TALK** — hold to record and release to send a persistent voice message. TALK remains the fallback when a live CALL is unavailable.

Secondary **TEXT** messaging is also supported. Text, TALK voice messages and CALL events share one local conversation timeline on each endpoint.

HappyTalky provides bidirectional short-lived BLE proximity finding: **Find Watch** on Phone and **Find Phone** on Watch. Both report qualitative signal proximity/trend instead of pretending RSSI is an exact distance or direction. Find Watch has passed basic end-to-end physical-device validation on the target Phone + Watch pair; Find Phone is software/CI validated in this release and still needs its own physical-device acceptance test.

## Why two modes?

CALL is for synchronous conversation. Normal CALL uses Wear OS Data Layer signaling and begins live audio after the receiver explicitly answers. Priority CALL uses a separate locked request described below; both modes use the same bidirectional Data Layer audio channel once connected.

TALK is asynchronous and privacy-preserving:

- received TALK messages are stored and notified, never auto-played;
- playback always requires a user action;
- Data Layer DataItems/Assets can queue while a peer is temporarily unavailable and synchronize after connectivity returns;
- saved TALK history can be replayed, selectively deleted, or cleared.

## TEXT

TEXT uses persistent Wear OS Data Layer DataItems, so a message can be written while the peer is temporarily unavailable and synchronize later. A locally accepted send is shown as **Sent** when the peer is reachable or **Queued** when it is offline; neither label claims remote receipt. HappyTalky does not claim Delivered or Read until those states are explicitly acknowledged.

Phone uses a normal Material 3 composer. Wear shows a compact Message composer in Inbox and delegates real entry to the Wear OS system RemoteInput experience, including dictation, emoji, quick replies and the configured IME.

On Watch, long-pressing a TEXT row reads that message aloud with the local Android text-to-speech engine. Long-pressing the same row again stops playback; long-pressing another TEXT row replaces the current utterance. Read-aloud is Watch-local behavior and adds no Data Layer protocol message. It stops when Inbox closes, when TALK/voice playback starts, or when CALL leaves the idle state.

## Connectivity

HappyTalky exposes the route it can actually infer from Wear OS Data Layer and the local network:

- **Nearby · direct** — the paired peer is a nearby Data Layer node and can be reached directly. This is the preferred CALL route.
- **Remote · Wi-Fi** — the peer is reachable through the remote Data Layer path while this device has Wi-Fi. CALL is allowed, with TALK as the more tolerant fallback.
- **Remote · Cellular** — the peer is reachable remotely while this device is using cellular data. CALL is allowed; this label describes the local device's network and does not imply that the remote peer is also on cellular.
- **Remote** — the peer is reachable remotely but the local transport cannot be classified more specifically. CALL is allowed because peer reachability has already been established by the Data Layer.
- **Offline** — no reachable peer is currently available, so CALL is disabled; TALK can still be recorded and queued for later synchronization.
- **Reconnecting** — an active CALL keeps a short grace period while HappyTalky tries to restore the live channel after a route change.

The UI deliberately does not claim to know the remote peer's exact LTE/Wi-Fi leg when Wear OS does not expose it.

## CALL behavior

CALL has an explicit lifecycle:

1. CALL
2. ringing
3. answer / decline / cancel / busy / timeout
4. connecting
5. live
6. reconnecting after a transient route change, when needed
7. end

Incoming normal CALL uses an actionable high-priority call notification. Phone may use lock-screen/full-screen presentation where Android permits it; Wear OS does not support full-screen intent notifications, so Watch relies on notification actions and the in-app call screen. Active Watch calls publish an Ongoing Activity return path. Either side can decline/cancel/end a normal CALL.

**Priority call** is a separate immediate parent-initiated request. It does not wait for an unanswered normal CALL and has no five-second escalation delay. A compatible Watch advertises `priority_locked_call_v1`; the Phone creates a fresh Priority call ID and sends the locked request directly. The Watch cannot decline the locked request or normally end the active Priority call from the app, notification, or foreground-service notification. The Phone can cancel while connection is pending and can end the connected call. Route loss or system failure can still terminate it.

Android 14+ treats microphone access as a while-in-use permission. Locked Priority therefore uses AndroidX Core-Telecom instead of directly starting a microphone foreground service from the background. The Watch registers the request as an incoming VoIP call, requests immediate answer, and then starts the existing Data Layer PCM transport. Opening HappyTalky remains a fallback if Telecom setup is unavailable or fails. **The background locked-Priority path has passed basic end-to-end physical-device validation** on the target Phone + Watch pair.

Phone live calls include an explicit **Speaker** toggle. Phone audio does not force speaker mode by default. Wear uses its communication speaker route.

Live audio uses the `VOICE_COMMUNICATION` path with acoustic echo cancellation and noise suppression when the device provides them.

## UI

Phone and Watch share product state and communication logic, but not page layout:

- **Phone:** Jetpack Compose Material 3
- **Wear:** Wear Compose Material 3, designed independently for a small round screen

Wear prioritizes **route/status → CALL → TALK**, with a unified Inbox where TEXT, TALK and CALL history rows can all be swiped left for local deletion, crown scrolling, and a fixed system-input Message composer. Incoming calls stay actionable from the call notification; when HappyTalky is foregrounded, the dedicated wrist call UI is used.

Compose screenshot previews are rendered in CI so phone and 192 dp round-watch layouts can be visually reviewed before shipping APKs.

## Debug builds

Every successful CI build publishes phone and Wear APK artifacts. PR builds also maintain a rolling prerelease such as `debug-pr-4`; main maintains `debug-main`.

The phone and watch APKs from one build use the same package name and debug signing identity, which Wear OS Data Layer requires.

See [docs/DEPLOY.md](docs/DEPLOY.md).

## Documentation

- [Documentation map](docs/README.md) — canonical vs historical docs and maintenance rules.
- [Architecture](docs/ARCHITECTURE.md) — protocol, persistence, routing, CALL/TALK/TEXT behavior and UI contracts.
- [Deploy and validation](docs/DEPLOY.md) — install, device checks, CI-equivalent validation and debug distribution.
- [UI development baseline](docs/UI_GUIDELINES.md) — current Android/Wear design rules, responsive testing and project-specific interaction invariants.

## Package

`com.xiaolong.happytalky`

## License

MIT


## Experimental hardware gates

Public Wi-Fi captive-portal support remains a debug-only PoC and has **not yet been validated on the target Pixel Watch**. Its parser/build tests may pass while Wear OS still refuses to keep an unvalidated captive network attached long enough for the flow to finish. **BLE Find Watch and locked Priority Call have passed physical-device validation; Find Phone remains hardware-unverified until the Watch→Phone BLE path is tested on the target pair.**
