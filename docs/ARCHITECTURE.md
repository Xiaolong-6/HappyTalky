# Architecture

> Canonical current-state architecture for `main`. PR-specific closure notes under `docs/` are historical unless this document links to them as normative behavior. Mutable CI/physical-device evidence is tracked in [VALIDATION.md](VALIDATION.md).

## Product contract

HappyTalky keeps two primary child-facing voice actions:

- **CALL** = synchronous live two-way voice
- **TALK** = asynchronous persistent voice message

The conversation model includes **TEXT** as a secondary message type so text/emoji share the same timeline as TALK and CALL instead of creating a separate storage or history subsystem.

## Modules

- `core`: communication state machine, Data Layer transport, live audio, AEC/NS, reconnect policy, TALK storage, notifications, incoming-call actions, and shared observable UI state.
- `mobile`: Android phone Compose Material 3 activity and phone-specific interaction design.
- `wear`: Wear Compose Material 3 activity and round-screen interaction design.

Both application modules use application ID `com.xiaolong.happytalky` and must be signed identically.

## Conversation persistence

Phone and Watch each keep their own local Room database. They share the schema and protocol semantics, not one physical database.

The `conversation_items` table is the metadata source of truth for:

- TALK voice messages;
- CALL events;
- TEXT messages.

Every row carries a stable ID, direction, creation time, read time and delivery state, plus type-specific fields. CALL rows also retain call ID, outcome, mode, start/end timestamps and duration.

TALK audio remains in app-private `voice-history` files. The first database access imports legacy TALK filename/read-state metadata and legacy CALL JSONL entries once, preserving existing installs while new writes go to Room.

The Store APIs are currently synchronous so the existing UI/state machine can read a consistent snapshot without a broader reactive-state refactor. A future change may expose Room as `Flow` without changing the persisted schema or protocol semantics.

TEXT uses a stable UUID and is stored directly in Room. Outgoing text is initially `LOCAL`. If `DataClient.putDataItem()` accepts it while the peer is reachable, the UI may label the local send attempt `SENT`; if the peer is offline it remains `QUEUED` for later synchronization. Neither state claims remote receipt. HappyTalky does not label an outgoing message Delivered or Read without an explicit receiver acknowledgement. A reachable peer may send TEXT while device-info metadata is still refreshing; offline queueing requires previously confirmed `text_v1` support.

Watch TEXT read-aloud is local Android `TextToSpeech` presentation. No TTS audio or speaking state is synchronized to Phone. Gesture, haptic and communication-audio priority rules live in [UI_GUIDELINES.md](UI_GUIDELINES.md).

## Companion discovery

The phone and watch advertise different static Wear OS capabilities:

- phone: `happytalky_phone`
- watch: `happytalky_watch`

`CapabilityClient.FILTER_REACHABLE` is used for CALL readiness and signaling. This matters because `NodeClient` can report Android nodes even when the HappyTalky companion app is not installed or does not support the current protocol.

The preferred peer is a reachable nearby/direct capability node; otherwise HappyTalky uses one reachable remote capability node.

Each endpoint also publishes persistent metadata at:

`/happytalky/device-info/<stable-device-id>`

The payload contains the endpoint role, manufacturer/model, app version, protocol version, supported feature capabilities, and a publication timestamp. A stable app-scoped UUID identifies the endpoint across ordinary Data Layer reconnects. Peer metadata is accepted only when it is at least as fresh as the cached snapshot, so legacy persistent DataItems from an older install cannot overwrite current capabilities. `Node.displayName` is retained only as a human-readable fallback while the persistent device-info item has not arrived.

Publishing alone is not treated as a sufficient refresh mechanism after an app upgrade. When a reachable peer is detected, HappyTalky sends `/happytalky/device-info/request`; the peer republishes its current device-info item with a fresh timestamp. The Activity also requests peer metadata whenever reachability is refreshed. This prevents an otherwise healthy Phone/Watch link from remaining gated by stale cached capabilities when the devices stayed connected through an upgrade.

This lets presentation use labels such as `Watch · Pixel Watch 3` and lets later protocol features be gated by advertised capabilities instead of assuming both endpoints were upgraded simultaneously.

## Nearby BLE finding

Current BLE-finding validation evidence is tracked in [VALIDATION.md](VALIDATION.md). RSSI proximity is functional guidance, not exhaustive RF characterization or an exact distance measurement.

BLE finding is bidirectional:

- **Find Watch:** Phone creates a fresh random 64-bit session token, sends `/happytalky/proximity/start`, Watch advertises the token, and Phone scans.
- **Find Phone:** Watch creates the session token and sends the same start path, Phone advertises the token, and Watch scans.
- The responder advertises the token for at most 60 seconds as BLE service data under a HappyTalky-specific UUID. The payload contains no stable device ID, account identifier, device name, or other persistent identity.
- The requester performs a filtered low-latency scan for the exact token and converts RSSI into **Far / Nearby / Close / Very close**, plus **Getting closer / About the same / Getting farther**.
- RSSI presentation uses a short median window followed by exponential smoothing. It deliberately does not claim exact distance or direction.
- Closing either Find screen sends `/happytalky/proximity/stop`; advertising also has an independent timeout.
- The responder reports `/happytalky/proximity/ready` or `/happytalky/proximity/error` so the requester can distinguish permission/radio failure from weak or temporarily missing signal.
- `ble_proximity_v1` remains the compatibility capability for Phone→Watch Find Watch. `ble_proximity_v2` advertises bidirectional support, including Watch→Phone Find Phone.
- CALL state takes precedence: an active Find screen exits when call state begins, and a busy responder rejects a new proximity request.
- The ranging layer stays separate from CALL/TALK so a future UWB or Bluetooth Channel Sounding backend can replace RSSI without changing the high-level interaction.

Android 12+ requires runtime `BLUETOOTH_SCAN` on the scanning endpoint and `BLUETOOTH_ADVERTISE` on the advertising endpoint. Because HappyTalky intentionally derives physical proximity from scan RSSI, the scanning endpoint also requests foreground location permission instead of using the `neverForLocation` assertion. The Phone is provisioned for advertising while HappyTalky is opened so a later Watch-initiated request does not depend on a background permission dialog; the Watch requests scan/location permission in context when Find Phone is opened.

## CALL state machine

Signaling uses transient `MessageClient` paths:

- `/happytalky/call/ring`
- `/happytalky/call/answer`
- `/happytalky/call/decline`
- `/happytalky/call/cancel`
- `/happytalky/call/busy`
- `/happytalky/call/end`
- `/happytalky/call/disconnected`
- `/happytalky/call/priority` (legacy escalation compatibility)
- `/happytalky/call/priority-locked`

### Priority CALL

Current locked-Priority validation evidence is tracked in [VALIDATION.md](VALIDATION.md).

Locked Priority CALL is a separate immediate Phone-to-Watch request. It is not an escalation of an ordinary ringing CALL.

- New support is advertised through `priority_locked_call_v1`. The older `priority_call_v1` path is retained only for protocol compatibility with older builds.
- Phone can start locked Priority only from an idle, CALL-capable route and only after the Watch advertises the new capability.
- Starting Priority creates a fresh call ID and sends `/happytalky/call/priority-locked` immediately. It does not start a normal CALL first and has no delay.
- Watch does not expose an opt-out switch for locked Priority. The legacy `PriorityCallSettings` / `priorityAutoAnswer` field remains only for compatibility with the older `/happytalky/call/priority` path; it does not gate `/happytalky/call/priority-locked`.
- A locked Priority incoming state has no Decline action and no ordinary missed-call timeout. The Watch call screen shows the locked state without NO/END controls.
- Once connected, the Watch cannot normally terminate a locked Priority call through Compose UI, notification actions, `CallActionReceiver`, or the foreground-service notification. The Phone remains allowed to cancel a pending request or end an active call.
- Locked Priority also owns the Watch interaction surface while incoming/connecting/live/reconnecting: Inbox, TEXT compose/TTS, TALK recording/playback, Find Phone, and history actions cannot take focus or start competing audio. Core guards reject Watch TEXT and TALK playback if an already-open secondary surface tries to complete an action during the locked call.
- If Phone CANCEL races with Watch auto-answer, Watch accepts the Phone cancellation even after the local state has already moved from incoming to active.
- Route failure, process/system failure, or reconnect timeout can still terminate the call and persist `DISCONNECTED`.
- Android 14+ treats `RECORD_AUDIO` as a while-in-use permission, so a background Data Layer listener does not start a microphone FGS directly. Locked Priority instead registers an incoming VoIP call through AndroidX Core-Telecom. Telecom owns the platform call lifecycle and foreground call execution; after `answer()` succeeds, HappyTalky starts its existing Data Layer PCM transport. The shared Wear `LiveCallService` declares both `microphone` and `phoneCall`. Every live call keeps the runtime `microphone` foreground-service type because HappyTalky owns the `AudioRecord` capture. A Telecom-managed locked Priority session adds `phoneCall` rather than replacing `microphone`, so pressing Home does not let the audio framework silence Watch capture while the Telecom call remains active. The visible Watch Activity path remains a fallback when Telecom is unavailable or cannot add the call.
- CALL history stores `PRIORITY` mode so these calls remain auditable.

The normal CALL lifecycle remains:

~~~text
READY
  -> OUTGOING_RINGING -> ANSWERED -> CONNECTING -> LIVE
  -> INCOMING_RINGING -> ANSWERED -> CONNECTING -> LIVE

OUTGOING_RINGING -> CANCELLED | DECLINED | BUSY | TIMEOUT
INCOMING_RINGING -> ANSWERED | DECLINED | CANCELLED | MISSED
LIVE -> RECONNECTING -> LIVE
LIVE/RECONNECTING -> ENDED | DISCONNECTED
~~~

When the PCM channel actually attaches, both endpoints emit one short connection haptic for that call ID. A call that reached live audio emits a distinct end haptic when it terminates locally, remotely, or through a disconnect/failure path. Reconnection of the same call ID does not replay the connection haptic.

Locked Priority adds a distinct path:

~~~text
READY
  -> PRIORITY_LOCKED_WAITING
  -> CONNECTING
  -> LIVE

PRIORITY_LOCKED_WAITING -> CANCELLED_BY_PHONE | BUSY | FAILED
LIVE -> ENDED_BY_PHONE
LIVE/CONNECTING -> RECONNECTING -> LIVE | DISCONNECTED
~~~

A normal CALL never becomes live merely because a RING arrived; the receiver must answer. Locked Priority is the explicit exception: the Watch asks Telecom to answer immediately, then starts the existing PCM channel when Telecom confirms the call active.

After ANSWER, the initiator opens:

`/happytalky/call/audio/<call-id>`

through `ChannelClient`.

Both sides then run simultaneously:

- microphone -> PCM16 -> channel output
- channel input -> PCM16 -> communication output

Audio parameters:

- 16 kHz
- mono
- 16-bit PCM
- `VOICE_COMMUNICATION` audio source
- acoustic echo cancellation when available
- noise suppression when available

Phone starts on the system-selected communication route. Speaker is an explicit user toggle. Wear requests its built-in communication speaker when available. Priority CALL uses the same audio transport once connected; the locked policy changes call initiation and local termination rights, not PCM transport.

A foreground service keeps outgoing/live-call state alive in the background. Normal calls expose Cancel/End from the ongoing notification. An active locked Priority call on Watch deliberately omits End. On Wear OS, active calls are also published as an `OngoingActivity` so the watch face/launcher can provide a one-tap return path.

## Reconnection

A transient channel or peer disconnect does not immediately destroy an active CALL.

HappyTalky:

1. marks the route `RECONNECTING`;
2. preserves the active call ID;
3. gives the route a short reconnect grace period;
4. retries the outgoing live channel from the original call initiator;
5. ends the call and recommends TALK only after the grace period expires;
6. signals `/happytalky/call/disconnected` so both endpoints persist `DISCONNECTED` rather than allowing the remote side to misclassify an abnormal drop as `COMPLETED`.

This is intended for ordinary Bluetooth/Wi-Fi handovers and brief network interruptions, not indefinite background calling.

## Route model

`PeerRoute` separates peer reachability from the local device's currently observed transport:

- `NEARBY_DIRECT`: Data Layer reports the peer as nearby/direct. This is the preferred CALL route.
- `REMOTE_WIFI`: peer reachable remotely and the local device's active network is Wi-Fi. CALL is allowed.
- `REMOTE_CELLULAR`: peer reachable remotely and the local device's active network is cellular. CALL is allowed.
- `REMOTE_INTERNET`: peer reachable remotely but the local route type cannot be classified more specifically. CALL is allowed.
- `OFFLINE`: no reachable peer. New CALL is disabled.
- `RECONNECTING`: temporary active-call recovery state. New CALL waits for reachability to return.

CALL readiness is gated by a reachable HappyTalky capability node, not by whether the local phone happens to be on Wi-Fi or cellular data. In particular, Phone-on-cellular + remote Watch-on-Wi-Fi is a valid remote CALL topology when the Watch remains reachable through the Wear OS Data Layer.

The app does not infer the remote peer's exact last-mile transport from local network state. `REMOTE_CELLULAR` describes the local endpoint's active network only; it does not mean the remote peer is using cellular.

## Incoming CALL presentation

Incoming CALL uses:

- ringtone and vibration;
- an importance-high `CATEGORY_CALL` notification;
- Answer and Decline notification actions;
- phone-only full-screen intent / lock-screen activity presentation where Android permits it;
- a dedicated Answer / Decline screen whenever the Wear activity is already foregrounded;
- the same Answer/Decline actions in the foreground Compose UI.

Wear OS does not support `setFullScreenIntent()` or the `USE_FULL_SCREEN_INTENT` permission, so the Wear build does not request that permission or attempt that notification path. A normal background incoming call uses a dedicated high-importance Wear notification with Answer / Decline actions and the normal ring timeout.

A locked Priority incoming call posts an ongoing high-priority notification with only an Open fallback action. It has no local Decline action and no missed-call timeout. In foreground or background, a Telecom-capable Watch registers the incoming VoIP call and immediately requests `answer()`. The paired Phone remains the only product-level endpoint allowed to cancel/end the locked call; genuine Telecom, permission, route, or process failures may still terminate it. If Telecom is unavailable, opening HappyTalky retains the previous visible-Activity fallback.

Final CALL outcomes are persisted locally, including completed duration, declined, missed/no-answer, cancelled, busy, failed, and disconnected cases.

## TEXT

TEXT transfer uses a persistent DataItem:

`/happytalky/message/<uuid>`

The payload carries stable ID, origin role, creation time and UTF-8 text. Text is capped at 500 characters for the first protocol version.

A receiving endpoint performs idempotent insert-by-ID, consumes the synchronized DataItem, updates the shared timeline, and posts the same message notification family used by TALK. Replayed DataItems are deleted without producing duplicate user notifications.

On Phone, unread TALK/TEXT uses an importance-high message notification channel with the system default notification sound and vibration enabled. Channel IDs are versioned when app-defined alert defaults change because Android persists a channel's audible/haptic behavior after first creation; the user's system notification settings remain authoritative.

Phone uses a Material 3 single-line composer with IME Send and a quick emoji affordance.

Wear keeps only a compact fixed composer in Inbox. Tapping it launches the system Wear RemoteInput flow through `androidx.wear:wear-input`, enabling dictation, emoji, predefined choices and the system IME. HappyTalky does not attempt to render a phone-style keyboard on a 192 dp round screen.

Opening the Watch Inbox marks incoming TEXT as read locally. TALK retains its stricter playback-completes-read rule.

## TALK

TALK records AAC/M4A:

- mono
- 16 kHz
- 32 kb/s
- maximum 60 s per recording

Every message gets a stable UUID and timestamp. Its metadata is stored in the unified conversation database.

Local audio copies are stored under the app-private `voice-history` directory.

Transfer uses a persistent DataItem + Asset:

`/happytalky/voice/<uuid>`

This makes TALK independent from CALL and lets a local write synchronize after a temporary disconnect.

On receive, HappyTalky:

1. saves its own local copy;
2. removes the synchronized DataItem after ingestion;
3. posts a notification;
4. updates history;
5. **does not play audio automatically**.

Incoming TALK keeps an unread flag on each device until playback completes. Unread TALK drives the notification count and Watch Inbox emphasis; deleting a message also removes its unread state.

The phone history supports tap-to-play, selective deletion, select-all, and clear-all.

## UI contract

Phone and Watch share `HappyTalkyUiState`, not presentation code. New UI work must also follow [UI_GUIDELINES.md](UI_GUIDELINES.md), which defines the current Android/Wear design baseline and screenshot/device review requirements.

### Phone

Compose Material 3 follows a voice-messenger information architecture:

- compact conversation header with peer reachability and CALL/TALK availability;
- one chronological conversation timeline containing TEXT, TALK and persisted CALL events;
- TEXT message bodies on Phone are selectable/copyable without including sender/timestamp metadata in the selection surface;
- incoming TALK bubbles on the left and outgoing TALK bubbles on the right;
- tap to play, with duration and timestamp shown in the bubble;
- long-press any TALK to enter multi-selection; the temporary top bar provides select-all and delete;
- current CALL state appears inside the conversation timeline instead of occupying a permanent dashboard card;
- bottom action area keeps CALL and press-and-hold TALK on the first row and a Message composer beneath them;
- the conversation header exposes **Priority call** as a direct action when the connected Watch advertises locked-Priority support; it creates a separate immediate request rather than escalating a normal CALL;
- incoming CALL temporarily replaces the bottom actions with Decline / Answer;
- live CALL replaces the right action with the Speaker toggle;
- light/dark ColorSchemes follow the Android system theme while keeping the HappyTalky brand blue stable.

### Wear

Wear Material 3 presents a shorter wrist-first loop:

- route/action cue;
- central CALL / END / CANCEL control;
- a dedicated full-screen incoming CALL screen;
- a direct hold/release TALK surface at the bottom, with the press gesture owned by the surface itself rather than a disabled child button;
- swipe left from the home screen to enter the unified Inbox;
- Inbox supports touch scrolling and the watch rotary/crown;
- unread incoming TALK is counted and bold/highlighted in chronological history, and loses emphasis after playback completes;
- TEXT, TALK and CALL rows are interleaved by timestamp; every persisted row can be swiped left to reveal Delete, with a short type-specific confirmation. Deletion is local history management; TEXT/CALL deletion is not a remote recall operation, while TALK deletion also removes its local audio file;
- persisted CALL events are interleaved with TEXT/TALK by timestamp, while the latest CALL is still summarized on the home screen;
- locked Priority incoming presentation is visually distinct, exposes no Decline control, and uses Core-Telecom for background auto-answer with a visible-Activity fallback;
- during any non-idle locked Priority state the Watch shows a dedicated non-interactive Priority screen; the normal home/Inbox controls are not rendered until the call returns to READY;
- a fixed compact Message composer launches the system RemoteInput/IME with emoji and dictation support when no locked Priority interaction lock is active.

Bulk history management and secondary explanation stay on the phone.

## Visual regression

CI renders Compose screenshot previews for:

- ready;
- outgoing call;
- incoming call;
- recording;
- offline;
- live;
- reconnecting;
- phone direct Priority ready/requested states and dialog;
- Watch locked Priority incoming and locked live states;
- Phone mixed TEXT/TALK conversation;
- Watch mixed TEXT/TALK/CALL Inbox.

Phone previews use a 412 x 915 dp surface. Wear previews use a 192 dp round device specification.
