# Deploy HappyTalky

HappyTalky installs as two matching APKs built from the same commit. The authoritative app version is defined in `mobile/build.gradle.kts` and `wear/build.gradle.kts`; this document intentionally does not duplicate mutable version metadata.

Current CI-versus-hardware evidence is tracked separately in [VALIDATION.md](VALIDATION.md).

HappyTalky installs as two APKs with the same application ID:

- `HappyTalky-phone-debug.apk` -> Android phone
- `HappyTalky-watch-debug.apk` -> Pixel Watch

Using the same application ID is intentional. Wear OS Data Layer also requires matching signatures.

## Fastest path: rolling debug release

Successful CI builds publish direct APK assets in a rolling prerelease:

- PR builds: `debug-pr-<PR number>`
- main: `debug-main`

Each release contains:

- `HappyTalky-phone-debug.apk`
- `HappyTalky-watch-debug.apk`
- `debug-dist.json`

The metadata records the exact source commit. Phone and watch APKs from one release are built together and use the same CI debug signing identity.

GitHub Actions artifacts remain available as a secondary path.

## Rename cutover

The package rename happened in **0.3.0**. Current `main` remains on `com.xiaolong.happytalky` and intentionally does not upgrade the old `com.xiaolong.happytalkie` install. Remove the old phone/watch app after installing a current build so two launcher entries and two Data Layer endpoints cannot be confused.

## Install on the Android phone

Download `HappyTalky-phone-debug.apk` on the phone and open it.

Android may ask you to allow that browser/file manager to install unknown apps. The system package installer must still confirm the installation.

For ADB:

~~~text
adb install -r HappyTalky-phone-debug.apk
~~~

If Android reports a signing mismatch from an older experimental build:

~~~text
adb uninstall com.xiaolong.happytalky
adb install HappyTalky-phone-debug.apk
~~~

## Install on Pixel Watch over Wireless debugging

On the Pixel Watch:

1. Enable **Developer options**.
2. Enable **ADB debugging**.
3. Enable **Wireless debugging**.
4. Choose **Pair new device** and note the pairing IP/port and code.

Pair:

~~~text
adb pair WATCH_IP:PAIR_PORT
~~~

Then use the separate debug connection port shown on the watch:

~~~text
adb connect WATCH_IP:DEBUG_PORT
adb -s WATCH_IP:DEBUG_PORT install -r HappyTalky-watch-debug.apk
~~~

If the existing watch build has a different signature:

~~~text
adb -s WATCH_IP:DEBUG_PORT uninstall com.xiaolong.happytalky
adb -s WATCH_IP:DEBUG_PORT install HappyTalky-watch-debug.apk
~~~

### Phone-only debugging

A phone can act as the ADB client if it has an Android ADB client installed.

The same Wear OS pairing flow applies:

1. Watch -> **Wireless debugging -> Pair new device**.
2. Pair from the phone ADB client using the pairing port/code.
3. Connect to the watch's separate debug port.
4. Install/update `HappyTalky-watch-debug.apk`.

An ordinary third-party Android app cannot silently sideload an arbitrary APK onto the watch. Wear OS still requires the platform's install authorization/confirmation path.

## First launch

Open HappyTalky once on both devices and grant:

- Microphone
- Notifications

For incoming normal CALL, Android may also control whether full-screen call notifications are permitted on Phone. Wear OS does not support full-screen-intent notifications; the Watch uses high-priority call notifications instead.

## Behavior to verify

### Nearby CALL

1. Keep phone and watch paired and nearby.
2. Confirm the UI reports **Nearby · direct**.
3. Tap **CALL**.
4. Verify the receiving device rings and shows Answer/Decline.
5. Verify the caller can **CANCEL** before answer.
6. Answer.
7. Verify live two-way audio.
8. On phone, toggle **Speaker** on/off.
9. Verify either side can **END**.
10. Verify AEC/NS behavior by speaking with both devices in the same room.

### Incoming CALL while app is backgrounded

1. Background HappyTalky on the receiving device.
2. Start CALL from the peer.
3. Verify a call-style notification appears.
4. Verify Answer and Decline work.
5. On Phone, if the OS allows full-screen call intents, verify the incoming UI appears over the lock screen.
6. On Wear, verify the high-priority call notification appears with Answer / Decline and opens the dedicated **YES / NO** in-app screen.
7. Ignore one normal call and verify it times out rather than ringing forever.
8. Verify the missed call appears in Watch CALL history with its time.
9. Complete a call and verify the history records its time and duration.
10. Decline a normal call from each side and verify the correct declined outcome is recorded.

### Locked Priority CALL

1. Install the same current build on Phone and Watch. Open Phone while Watch remains reachable and verify peer metadata refreshes automatically; opening the Watch app must not be required merely to refresh `priority_locked_call_v1`.
2. From an idle connected Phone, open **Priority call** and start it directly. Verify no normal CALL or five-second wait occurs first.
3. With HappyTalky already visible on Watch, verify the Watch enters the locked Priority screen and auto-connects without a YES/NO choice.
4. Verify Watch has no Decline/END control in the activity, incoming notification, or active-call notification.
5. While the locked Priority call is incoming/connecting/live/reconnecting, try to reach Inbox, TEXT compose/TTS, TALK playback/recording, Find Phone, and history actions. Verify the dedicated Priority screen owns the Watch UI and those secondary interactions cannot start. Press Home/crown and verify leaving the app does not end the call.
6. Verify both Phone and Watch give one short haptic when live audio is actually established. Briefly disrupt and restore the route inside reconnect grace; verify reconnection does not repeatedly replay the connection haptic.
7. End the connected call from Phone. Verify both endpoints give the distinct end haptic, return to idle, and record Priority history.
8. Start another locked Priority call and cancel it from Phone during connection. Repeat while Watch is transitioning into active state; verify the Watch still closes the call and does not leave an orphan live state. A call that never reached live audio must not give the completed-call end haptic.
9. Background HappyTalky on Watch and start locked Priority. Verify the persistent Priority/call presentation appears and, on the supported AndroidX Core-Telecom path, the Watch answers and connects without manually opening HappyTalky. The background Data Layer listener must not directly start a microphone foreground service; Telecom owns the platform call lifecycle before HappyTalky starts its PCM transport.
10. If Core-Telecom is unavailable or rejects the call, verify the visible-Activity fallback remains usable instead of silently claiming background audio.
11. During an active locked Priority call, interrupt the route long enough to exceed reconnect grace and verify `DISCONNECTED` is still allowed as the failure exit and gives the active-call end haptic.

### Route change / reconnect

1. Start a live CALL on the preferred nearby route.
2. Change network conditions so the route briefly disappears.
3. Verify UI changes to **Reconnecting**.
4. Restore a valid route inside the grace period.
5. Verify live audio returns without creating a new call.
6. Repeat while keeping the route unavailable; verify the call eventually ends and TALK is recommended.

### Remote Wi-Fi

When the peer is remotely reachable and the local active route is Wi-Fi:

- UI reports **Remote · Wi-Fi**;
- CALL is available;
- TALK remains the safer fallback for an unstable link.

### Remote cellular / unclassified internet

For a remote peer while the local active route is cellular, or when the local route cannot be classified more specifically:

- the peer must first be reported reachable by the HappyTalky Data Layer capability;
- CALL is available for new sessions;
- TALK remains available as the more tolerant fallback for an unstable link;
- the UI must not report Offline merely because the Phone is using cellular data.

Regression acceptance case:

1. Put the Phone on carrier data only and move the Watch out of Bluetooth range.
2. Keep the Watch connected to working Wi-Fi.
3. Verify the Phone reports the Watch as a reachable remote peer rather than disabling CALL because the Phone is on cellular.
4. Start a normal CALL and verify signaling and live audio can connect.
5. Repeat with a locked Priority CALL when `priority_locked_call_v1` is advertised.
6. Break the Watch network entirely and verify the peer eventually becomes Offline and new CALL is then disabled.

The route label describes the local endpoint's active transport only. It must not be used to infer the remote Watch's Wi-Fi/LTE last mile or to reject an otherwise reachable Data Layer route.

### TALK privacy and history

1. Hold **TALK**.
2. Speak.
3. Release.
4. On the receiving device, verify a notification appears.
5. Verify the audio **does not auto-play**.
6. Tap the saved message to play it.
7. On phone, long-press a TALK bubble to enter multi-selection.
8. Select one or more messages and delete them from the temporary selection bar.
9. Select all messages and verify the bulk delete path clears the history.
10. On Watch, swipe left from the home screen to open **Inbox**.
11. Verify the crown/rotary control scrolls the Inbox.
12. Verify incoming unread TALK is bold/highlighted and the unread count is visible.
13. Play one incoming TALK to completion and verify its unread emphasis/count disappears.
14. Swipe a TALK row left, reveal **Delete**, and delete it.

### Offline TALK

1. Make the peer unavailable.
2. Verify CALL is disabled.
3. Record a TALK.
4. Verify it is saved/queued rather than discarded.
5. Restore connectivity.
6. Verify the Data Layer synchronizes the TALK.

### TEXT and unified timeline

1. With the peer reachable, send TEXT from Phone and verify it appears in the same timeline as TALK and CALL events.
2. On Phone, long-press and drag across a TEXT message body; verify Android text selection handles appear and Copy works without selecting the sender/timestamp metadata.
3. Background the receiving Phone, send a new TEXT or TALK from Watch, and verify the Phone produces an audible notification plus vibration in addition to the status-bar notification. If the channel has been manually muted in Android settings, verify the app does not override that user choice.
4. On Watch, open Inbox and launch the compact Message composer; verify entry is delegated to the Wear OS system RemoteInput/IME rather than an in-app phone-style keyboard.
5. Make the peer unavailable and send TEXT only after the cached peer advertises `text_v1`; verify the local row shows **Queued**, not Delivered/Read.
6. Restore connectivity and verify the queued DataItem synchronizes without creating a duplicate row or duplicate notification.
7. Open Watch Inbox and verify incoming TEXT is marked read locally; confirm TALK still requires playback completion before its unread state clears.
8. Long-press a TEXT row and verify the Watch gives long-press haptic feedback, shows the read-aloud indicator, and speaks the message through the local TTS engine.
9. Long-press the same TEXT row again and verify speech stops. Start it again, then long-press a different TEXT row and verify the second message replaces the first.
10. While a message is speaking, leave Inbox, start TALK/voice playback, or start/receive CALL; verify TTS stops immediately and does not overlap communication audio.
11. Swipe a TEXT row horizontally while it is not being long-pressed and verify the existing Delete reveal still works without triggering TTS.
12. Delete TEXT and CALL rows locally on Watch and verify this behaves as local history management, not remote recall. Delete a TALK row and verify its local audio file is removed as well.

## Build locally

Current build baseline:

- Android Gradle Plugin 9.1.1
- Gradle 9.3.1
- compile SDK 37.0
- target SDK 36
- JDK 17
- Compose BOM 2026.09.00
- phone Material 3 via Compose BOM 2026.09.00
- Material 3 Adaptive dependency 1.3.0 in the Phone module
- Wear Compose Material 3 1.7.0

The CI installs the Android 37.0 SDK platform package explicitly while `targetSdk` remains 36.

CI-equivalent local checks:

~~~text
node --test .github/scripts/cleanup-debug-releases.test.cjs
gradle :core:testDebugUnitTest :wear:testDebugUnitTest :mobile:assembleDebug :wear:assembleDebug
gradle :mobile:updateDebugScreenshotTest :wear:updateDebugScreenshotTest
~~~

For UI changes, review [UI_GUIDELINES.md](UI_GUIDELINES.md) before implementation and visually inspect the rendered artifacts rather than treating a successful Compose build as sufficient validation.

Outputs:

~~~text
mobile/build/outputs/apk/debug/mobile-debug.apk
wear/build/outputs/apk/debug/wear-debug.apk
~~~

## Visual regression

CI renders Compose screenshot previews for both platforms before publishing debug APKs.

The screenshot artifacts are:

- `HappyTalky-phone-ui-screenshots`
- `HappyTalky-watch-ui-screenshots`

Use these to catch clipping, overlap, disabled-state errors, and small-round-screen regressions before installing on hardware.
