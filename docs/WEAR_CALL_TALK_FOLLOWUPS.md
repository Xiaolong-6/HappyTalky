# Historical: Wear CALL / TALK follow-up closure

> Historical implementation/closure note. It records how an older follow-up was closed; it is not the canonical current behavior specification. Use [ARCHITECTURE.md](ARCHITECTURE.md), [DEPLOY.md](DEPLOY.md), [VALIDATION.md](VALIDATION.md), and [UI_GUIDELINES.md](UI_GUIDELINES.md) for current behavior and evidence.

Baseline: current `main` after conversation, device-identity, Priority CALL, and TEXT/timeline work.

This file replaces the original documentation-only backlog captured by PR #9.

## Closed in later architecture work

### CALL history visibility

Closed by the unified conversation timeline.

Wear now interleaves TEXT, TALK, and persisted CALL events by timestamp instead of hiding CALL history under a separate section.

## Implemented state recorded at closure

### TALK swipe-delete confirmation

Wear keeps swipe-left delete for TALK and now shows a short, glanceable:

`TALK deleted ✓`

confirmation after a successful delete action.

The confirmation is transient and does not alter the read state of other messages.

### Wear incoming CALL platform path

The old Wear path incorrectly treated full-screen intent notifications as available.

Wear OS does not support `setFullScreenIntent()` or the `USE_FULL_SCREEN_INTENT` permission.

The Wear build therefore:

- no longer declares `USE_FULL_SCREEN_INTENT`;
- no longer asks the user for full-screen-intent access;
- does not call `setFullScreenIntent()` for Watch notifications;
- keeps high-priority `CATEGORY_CALL` notification actions for Answer / Decline;
- keeps the dedicated in-app call screen when HappyTalky is already foregrounded;
- publishes active Watch CALL as a Wear `OngoingActivity` for a one-tap return path.

The original v1 Priority path kept its foreground visibility gate. This closure note predates the later AndroidX Core-Telecom background path, so that old foreground-only behavior must not be treated as current. See [ARCHITECTURE.md](ARCHITECTURE.md) for the current mechanism and [VALIDATION.md](VALIDATION.md) for current evidence.

### Abnormal live-call DISCONNECTED outcome

Reconnect timeout now uses a distinct protocol terminal signal:

`/happytalky/call/disconnected`

This prevents the remote endpoint from treating an abnormal reconnect failure as a normal `COMPLETED` call.

Both endpoints can now persist `DISCONNECTED` for the same abnormal terminal event.

A pure outcome policy test covers:

- normal active remote END -> `COMPLETED`;
- abnormal active remote disconnect -> `DISCONNECTED`;
- normal pre-answer remote END -> `CANCELLED_BY_PEER`.

## Validation

Historical validation checklist used to close PR #9:

- core unit tests;
- Phone debug build;
- Wear debug build;
- existing Phone/Wear screenshot render;
- visual check of 192 dp Wear Inbox after the delete-confirmation change;
- verify the Wear manifest contains no `USE_FULL_SCREEN_INTENT`;
- verify normal CALL END still records `COMPLETED`.
