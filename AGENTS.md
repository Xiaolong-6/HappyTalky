# AGENTS.md

## Product invariants

- Keep the primary UI child-friendly: CALL and TALK only.
- Text messaging is allowed as a secondary conversation capability; CALL and TALK remain the primary child-facing actions.
- CALL is transient real-time signaling.
- Locked Priority CALL is a direct Phone-to-Watch request: no ordinary-CALL delay, no Watch decline/end path, and no claim of background microphone capture that Android forbids.
- TALK must support store-and-forward delivery when the peer is temporarily offline.
- Phone and Wear OS apps must retain the same application ID and matching signatures.
- Never commit signing private keys, tokens, passwords, or service credentials.

## Build

Use JDK 17, compile SDK 37, AGP 9.1.1 and Gradle 9.3.1 (matching the checked-in build and CI configuration).

Minimum behavior validation:

~~~text
node --test .github/scripts/cleanup-debug-releases.test.cjs
gradle :core:testDebugUnitTest :mobile:assembleDebug :wear:assembleDebug
~~~

For UI changes, also render the checked-in screenshot suite before considering the work complete:

~~~text
gradle :mobile:updateDebugScreenshotTest :wear:updateDebugScreenshotTest
~~~

## Change policy

For new protocol paths, keep them under `/happytalky`.
Conversation metadata belongs in the shared Room timeline; large audio payloads stay in app-private files/Data Layer Assets.
For voice DataItems, use unique paths so offline messages cannot overwrite one another.
Any change to CALL/TALK state transitions should be tested on both roles because most behavior is intentionally shared in `core`.

## Documentation policy

- Treat `README.md`, `docs/README.md`, `docs/ARCHITECTURE.md`, `docs/DEPLOY.md`, `docs/VALIDATION.md`, `docs/UI_GUIDELINES.md`, and `dist/README.md` as current-state documentation.
- Keep PR-specific closure notes only as historical records and label them clearly; do not let old baseline commits or pre-merge checklists read like current requirements.
- Update the relevant canonical document in the same change whenever protocol behavior, permissions, UI interaction, build/deploy steps, validation evidence, or release lifecycle changes.
- Keep mutable CI/physical-device pass/fail status in `docs/VALIDATION.md`. If a code change invalidates previous device evidence, mark that row `Retest pending` in the same PR instead of leaving an old pass claim elsewhere.
- Do not duplicate the current app version in prose documentation; `mobile/build.gradle.kts` and `wear/build.gradle.kts` are the source of truth.
- For Android/Wear UI work, re-check current official Android documentation before implementation; do not assume a previously recorded library version is still the latest.
