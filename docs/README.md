# Documentation map

HappyTalky separates **current product contracts**, **validation evidence**, **experimental work**, and **historical closure notes** so an old PR or hardware result cannot silently become a current requirement.

## Canonical current-state documents

- [../README.md](../README.md) — concise product/repository entry point, screenshots and navigation.
- [ARCHITECTURE.md](ARCHITECTURE.md) — protocol, persistence, routing, CALL/TALK/TEXT semantics, Priority CALL and platform integration.
- [DEPLOY.md](DEPLOY.md) — installation, build baseline and manual acceptance procedure.
- [PLAY_RELEASE.md](PLAY_RELEASE.md) — Google Play packaging, signing, form-factor tracks and first-release procedure.
- [VALIDATION.md](VALIDATION.md) — single source of truth for mutable CI and physical-device validation status.
- [UI_GUIDELINES.md](UI_GUIDELINES.md) — Android/Wear UI baseline and project-specific interaction invariants.
- [../dist/README.md](../dist/README.md) — rolling debug release/tag lifecycle and distribution limits.
- [../AGENTS.md](../AGENTS.md) — repository invariants for development agents.

When these documents disagree with checked-in source or CI, treat the mismatch as documentation drift and fix the relevant canonical document in the same change.

## Experimental documents

- [WEAR_CAPTIVE_PORTAL_POC.md](WEAR_CAPTIVE_PORTAL_POC.md) — debug-only Wear OS captive-portal proof of concept. Product-readiness evidence belongs in [VALIDATION.md](VALIDATION.md).

Experimental documentation may describe exploratory behavior that is checked into `main`, but it must not be presented as a supported child-facing product contract.

## Historical implementation notes

- [PHONE_UI_RELEASE_CLEANUP.md](PHONE_UI_RELEASE_CLEANUP.md)
- [WEAR_CALL_TALK_FOLLOWUPS.md](WEAR_CALL_TALK_FOLLOWUPS.md)

These files preserve decisions and closure context. Baseline commit IDs, PR numbers, old platform limitations and “before merge” checklists inside them are historical evidence, not current requirements. When a later implementation supersedes an old statement, the historical note should say so explicitly rather than pretending its original state is still current.

## Maintenance rules

1. Update a canonical document whenever a change alters protocol semantics, permissions, user-visible CALL/TALK/TEXT behavior, build/deploy procedure, debug release lifecycle or UI interaction.
2. Keep mutable **validation status** only in [VALIDATION.md](VALIDATION.md). README, architecture and experimental docs should link to it rather than repeating “passed/pending” claims.
3. Keep mutable **app version metadata** in root `gradle.properties`. Phone and Wear derive separate version-code ranges from the shared release sequence; avoid copying the current version into prose docs unless the document is an immutable release record.
4. Label PR-specific planning/closure documents as historical once the work is merged.
5. Do not describe a library version as “latest” unless it was verified at the time of the change. Prefer “checked-in baseline” for repository dependencies.
6. Before Android or Wear UI work, re-check the current official Android documentation linked from [UI_GUIDELINES.md](UI_GUIDELINES.md).
7. Keep Phone and Watch behavior consistent where the shared protocol requires it, but do not force the same page layout onto both form factors.
