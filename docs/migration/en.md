# Spring migration

The migration keeps the Electron shell, static Next.js interface, portable executable and existing JSON/Markdown profile. Spring is the chosen backend framework; a database is not required.

## Checkpoint — 21 October 2026

- [x] Select a compatible baseline: Spring Boot 4.1.1, Spring Framework 7.0.9, Spring AI 2.0.1, Kotlin 2.3.21, Gradle 8.14.3 and JDK 21.
- [x] Replace Guice with Spring constructor injection, components and feature-specific `@Configuration` / `@Bean` integrations. Remove the old module and dependency.
- [x] Keep startup explicit, close the application through the context and retain the loopback descriptor/session protocol.
- [x] Place internal `StoredUpload` state in `domain.media`; improve upload and lifecycle readability.
- [x] Local verification: 400 Kotlin tests (11 opt-in live tests skipped), 153 UI unit tests, 38 Electron tests, 10 script tests and 107 browser scenarios; formatting, lint and type checks passed.
- [x] Package and launch `complie/Aigram.exe` 2.6.29 with bundled Java. Native window/tray/startup, updater recovery/profile migration and media smoke checks passed. Existing Telegram authorization, settings and manual notes survived the actual restart.
- [x] Published and verified [v2.6.29](https://github.com/kreeeeeel/boltunya/releases/tag/v2.6.29), source `7f6fae0`, with green Windows CI and matching EXE/tag/checksum. The published executable replaced the local build and retained the actual Telegram session and notes.

## Contract and client checkpoint

- [x] Preserve a semantic baseline for all 59 paths, 73 operations and original 78 schemas. Add the three missing operation IDs and feature tags. Compatibility tests compare each resolved operation/model, including optional PATCH fields, nullable values, string IDs, binary uploads and the `tiho` session scheme.
- [x] Split the single source under `backend/contract/api/schema`: eight feature operation files and one file per named model/enum. Extract 28 shared enums and two request models without changing their resolved wire shapes; the root now contains 108 named schemas.
- [x] Add contract API, generated Kotlin WebClient, typed Kotlin wrapper and full TypeScript HTTP SDK Gradle modules. One Gradle bundle preserves named nested references; every generator consumes that bundle after validating the original tree. Direct generation from the split files lost types in OpenAPI Generator 7.25.0, so bundled-type checks are required.
- [x] Keep generated sources, compiled SDK output and npm tarballs under `build/`; remove the tracked frontend declaration file. This checkpoint initially supplied `openapi-fetch` types; that temporary consumer was replaced during the Spring MVC cutover below.
- [x] Local verification: 415 Kotlin tests (11 optional live tests skipped), seven installed TypeScript SDK tests, 153 UI unit tests, 38 Electron tests, 10 script tests and 107 browser scenarios passed. The isolated Gradle regeneration test removed obsolete source and classes without a clean build. Native window/tray/startup, updater recovery/profile migration and media checks passed.
- [x] Built and launched local `complie/Aigram.exe` 2.6.30. The actual restart retained Telegram authorization, settings and manual memory notes. The SDK archive has the matching version and is included in the checksum file.

Publication is verified against the pushed source commit, green [Windows Actions](https://github.com/kreeeeeel/boltunya/actions/workflows/release.yml), published EXE/SDK digests and an actual launch of the downloaded executable. The release page and local `complie/VERIFICATION.md` record that result; a successful local package alone is not publication.

Usage and compatibility details: [API clients](../api-client/en.md). The Kotlin SDK uses the generator's supported Jackson 2 WebClient codecs on Spring 7; the generated Spring API uses Boot 4/Jackson 3.

## Completed checkpoint — Spring MVC and functional domain packages

- [x] Implement all eight generated interfaces with feature controllers, business components and explicit domain/DTO transformers. There is no JSON-object endpoint adapter between the generated API and application services.
- [x] Replace the private HTTP server/router with Spring Boot's loopback servlet server. Retain the local session cookie and descriptor URL, Host/Origin checks, static Next.js files, bounded JSON/raw uploads, media byte ranges and compressed sticker responses.
- [x] Move 120 domain models to functional packages under `domain.chat`, `media`, `memory`, `ai`, `settings`, `events`, `system` and `telegram`. Generated transport DTOs remain in the contract module.
- [x] Use standard Spring constructor injection and `ObjectProvider` for lazy dependencies. Remove remaining JSR-330 injection and the replaced `ru.aigram.http` package. Keep explicit runtime startup and context-owned shutdown.
- [x] Apply AST-based multiline braced conditionals and keep one class per file. Preserve existing JSON and Markdown stores; no database conversion or profile reset is part of this change.
- [x] Correct OpenAPI formats for existing wide timestamps, byte counts and timing values without narrowing Kotlin `Long` values. Keep opaque Telegram identifiers as strings and preserve binary upload/download bodies.
- [x] Move the HTTP suite to `controller.integration` against the real servlet context, including validation, raw uploads, media ranges, Host/Origin, static files, body limits and failed-refresh cleanup. Assertions for requests without Content-Type remain in place.
- [x] Preserve request defaults with six dedicated input schemas, independently of complete response models. The canonical source now has 60 paths, 74 operations and 114 schemas; app-managed Ollama restart is the added operation. Compatibility still checks the unchanged original baseline plus explicitly reviewed corrections/additions.
- [x] Pass the full Gradle quality gate: 467 Kotlin tests across application, contract and client modules (11 opt-in live tests skipped), eight installed TypeScript SDK tests, generated-source cleanup, real Spring context/HTTP/security tests and AST style checks. The style audit covers handwritten application and contract/client code, including multiline braced bodies and feature domain packages.
- [x] Verify the UI unit suite (168 tests), focused final regressions (21 cases), 113 browser scenarios, 38 Electron tests and 10 script tests. Type/lint/style checks pass. Inspect light/dark and compact screenshots of restart/draft notices; keep loading placeholders aligned with the real model card.
- [x] Package and launch local Aigram 2.6.31. Native window/tray/startup, updater recovery/profile migration and media checks pass. The packaged JAR has nine MVC controllers, no legacy HTTP/injection classes and no Guice/JSR-330 libraries. Desktop modules, updater helper and JAR match the checked artifacts. The actual profile restores Telegram authorization and retains saved settings and manual notes.

The server and web consumer now share the generated contract end to end: Spring MVC implements its interfaces, and frontend production calls use the feature operations from `@aigram/api-client`. Its workspace alias resolves generated SDK source. The temporary `openapi-fetch`/`openapi-typescript` path and schema bridge are removed. Gradle generates the SDK before `buildWeb`; the Windows build script does the same before npm checks, and the Docker web image has a JDK contract-generation stage. Standalone npm commands run `sdk:check` and require prior canonical Gradle generation; there is no second frontend schema.

The completed migration was published in [v2.6.33](https://github.com/kreeeeeel/boltunya/releases/tag/v2.6.33), source `e9c76b9`. Windows CI passed, including 178 UI unit tests and 113 browser scenarios. The published EXE and SDK matched their SHA-256 checksums; the downloaded EXE launched with the existing Telegram authorization, settings and manual memory notes preserved.

## Current checkpoint — stateless mapping extensions

- [x] Replace all 21 transformer classes with top-level receiver extension functions; remove their Spring annotations and constructor injection from all consumers.
- [x] Preserve mapping defaults, nullability, identifiers, nested collections and feature package boundaries; retain existing regression assertions.
- [x] Enforce extension-only production transformer files with an AST check and verify that the real Spring context registers no mapping bean.
- [x] Pass the full Kotlin quality gate: 468 Kotlin tests (11 optional live tests skipped), eight installed TypeScript SDK tests, real HTTP compatibility/security tests, mapping regressions, formatting and the AST architecture check.

Published and verified in [v2.6.34](https://github.com/kreeeeeel/boltunya/releases/tag/v2.6.34), source `a31d547`. Windows CI passed. Published EXE/SDK hashes and tag matched; the downloaded EXE launched with Telegram authorization, settings and manual notes preserved. Evidence is recorded in local `complie/VERIFICATION.md`.

## Current checkpoint — editing and event avatars

- [x] Define revision-protected edit operations and lossless entity references. Generate all three transports and pass contract compatibility tests without changing the original baseline.
- [x] Verify the visual editor dependency with an isolated clean `npm ci`; existing resolved versions remain unchanged.
- [x] Complete native editing, archive/context invalidation and visual editor integration. Preserve raw Telegram entities, link previews, caption placement, exact whitespace and configured Unicode limits.
- [x] Resolve event avatars from matching current chats, with initials when unavailable, in live cards, archived pages and details.
- [x] Protect archive and UI snapshots from same-second edits and account changes; retain deleted-message content and automated/excluded provenance. Pass 489 Kotlin tests (11 optional live skipped), nine installed SDK tests, the UI suite and final focused cases, 117 browser scenarios, 38 Electron tests and 10 script tests. Inspect light/dark/compact editor screenshots.

Implementation details: [editing sent messages](../messages/en.md). The implementation checkpoint is complete. Packaging, native smoke gates and publication run in Windows CI; exact EXE/SDK hashes, tag/source identity and the actual-profile restart result are recorded in the release notes and local `complie/VERIFICATION.md`. A pushed commit alone is not a verified release.

## Required verification

Run `:backend:check`, generated-contract validation and clean generation when introduced, web/desktop checks, browser scenarios and isolated native Electron/tray/updater/media tests. Test profile locking, context startup/close, shared queue identity, memory scheduling and local API security. Preserve actual authorization and notes; never use real Telegram sends or download models for tests. Every published release needs a changelog, green Windows CI, matching tag/source commit and an EXE whose SHA-256 matches the release checksum.

## Backlog after migration verification

1. **Feedback-based local quality comparisons.** Add an opt-in replay set built from user-approved rated replies, complementing the existing synthetic model matrix. Compare factual accuracy, adherence to the selected tone, time to first token and token usage on the same inputs; replay must never send Telegram messages or automatically train a model. Ratings and traces already exist; this curated comparison workflow does not.

These are follow-up candidates, not part of this migration's implementation or release gate.

Compatibility references: [Spring AI versions](https://docs.spring.io/spring-ai/reference/getting-started.html), [Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html), [Boot Kotlin support](https://docs.spring.io/spring-boot/reference/features/kotlin.html), [Kotlin/Gradle compatibility](https://kotlinlang.org/docs/gradle-configure-project.html).

## Published manual checkpoint — reply output and idle configuration

The recurring improvement automation is paused at the user's request. This manually requested release fixes history-envelope leakage, adds opt-in native formatting and makes idle follow-ups configurable per chat, with a global active-chat directory. No unrelated enhancement starts while this request is active.

- [x] Add resource-based output/formatting instructions and conversation-only envelope decoding.
- [x] Extend the single contract and pure mappings with optional entities and typed idle-message fields; retain strict PATCH defaults and the original wire baseline.
- [x] Complete native outgoing entities and literal/AI proactive modes with shared queue guards.
- [x] Add global/per-chat restart, finish-and-combine and sequential-each policies, with bounded FIFO, anchored history and exact TDLib automated-send provenance. Preserve manual owner cancellation and account/context safeguards.
- [x] Pass the full local gate: 548 Kotlin tests (12 optional live skipped), 10 installed SDK tests, 232 UI tests, 38 Electron tests, 10 script tests, formatting/type/lint checks and the optimized web build. Verify all 127 browser scenarios across full/focused runs and inspect light/dark/compact screenshots.
- [x] Verify three synthetic cases on the installed `deepseek-r1:8b`: ordinary replies, native formatting and unrelated participant relevance. Save timing-only evidence without prompts or responses.
- [x] Publish through Windows CI; verify native packaging/updater/media gates, tag/source, EXE/SDK checksums and actual-profile restart. Record the release result in its notes and local `complie/VERIFICATION.md`.

Details: [reply formatting](../messages/en.md#optional-ai-reply-formatting), [idle messages](../ai/conversation-support-en.md). This bounded manual checkpoint is published; recurring automatic feature work remains paused.

Published checkpoint: [v2.6.38](https://github.com/kreeeeeel/boltunya/releases/tag/v2.6.38), source `54cba94`. Windows CI and asset hashes passed; the downloaded EXE restored Telegram authorization, existing settings and manual memory notes in the actual profile.

## Current manual checkpoint — local voice messages

The recurring enhancement automation remains paused. Work is limited to the requested speech feature and profile-menu correction.

- [x] Add the single speech contract, generated transports, strict opt-in settings and model selection.
- [x] Implement curated downloads, isolated recognition/synthesis, OGG/Opus conversion and native Telegram voice delivery through the shared reply queue.
- [x] Verify the real pinned-asset installation → Piper → Opus → Whisper pipeline in an isolated test directory (36.130 s). Preserve the spoken words while allowing Whisper's punctuation to differ. Preserve actual Telegram data and Ollama models.
- [x] Implement editable voice drafts and four-language settings. Add real settings synthesis with current unsaved voice/rate, shared-queue progress, cancellation and playback, without a conversation-model call or Telegram send. Remove the profile-menu border.
- [x] Add Refresh/Explorer/Import controls, bounded compatible Piper archive import, retained notices and a persistent local voice selector. Verify the real settings HTTP upload/import/audio flow with a 21 MB package (15.782 s).
- [x] Pass standalone/final all-voice verification for Irina, Ruslan and Amy (34.490 s). The earlier intermittent Ruslan error did not recur; its cause remains undetermined, with test-only diagnostics added.
- [x] Pass the final backend gate: 619 Kotlin cases, zero failures and 14 skips; 12 SDK tests. Pass 257 full UI tests plus 16 focused tests across four files covering three new hook regressions, 39 Electron tests and 10 script tests. The fixed closed-queue regressions are included.
- [x] Verify all 135 browser scenarios before the final collapsed-heading/preview-hook changes.
- [x] Pass all eight final voice browser scenarios after the last changes (`build/speech-browser-last.log`).
- [x] Run real packaged speech on the local unpacked Aigram 2.6.39 (44.259 s): API and settings synthesis, actual playback, no model inference, signed out, unchanged preferences and clean shutdown. Retain the isolated evidence in `build/speech-verification/packaged-proof/run-KslmQb`. [Commands and limits](../speech/runtime-en.md#optional-packaged-speech-smoke).
- [x] Publish [v2.6.41](https://github.com/kreeeeeel/boltunya/releases/tag/v2.6.41), verify successful Windows CI, EXE/SDK SHA-256 and source `159228c`, then open the published portable with the existing profile. Real speech in its extracted payload passed in 34.822 s; Telegram authorization, settings and manual notes were preserved. Exact evidence is recorded in the release notes and local `complie/VERIFICATION.md`.

Details and current limitations: [voice messages](../speech/en.md), [runtime](../speech/runtime-en.md). Speech remains off until explicitly enabled. The integrated settings test cannot send Telegram messages. This checkpoint is published and verified; recurring enhancement automation remains paused.

## Current manual checkpoint — speech library and reply settings

- [x] Expand the catalog with distinct, verified single-speaker Piper voices, preserving existing IDs, exact artifact sizes/checksums, source model cards and license qualifications. Include a real compatible character voice only when its package is confirmed.
- [x] Move automatic text/voice reply mode and probability to Assistant → Replies. Organize Speech models using the shared Engine / Recognition / Synthesis tabs, searchable language-filtered cards and the real settings preview.
- [x] Repair outgoing attachment/voice playback using a bounded owned native-input cache, session-safe media lookup, explicit completed-cache cleanup and compact wave/time layout.
- [x] Replace composer emoji/sticker pickers with manual text-to-voice composition, per-draft voice choice, real preview, one-action synthesis/send, cancellation and duplicate-send protection. Preserve ordinary drafts and existing received media.
- [x] Transcribe every eligible incoming/outgoing voice in selected reply history and memory batches; preserve provenance/exclusions and replan memory token budgets after transcription without skipping sources.
- [x] Verify at least two newly added voices through download verification, installation, native synthesis, Opus and recognition in an isolated profile. Do not install the whole catalog in the user's profile or send Telegram messages.
- [x] Pass local backend/SDK/UI/Electron/script checks; verify all 141 browser scenarios across full/final speech runs, inspect both themes and narrow/short windows, and update English/Russian guides and screenshots.
- [ ] Publish through Windows CI, verify the downloaded EXE/SDK hashes and source/tag, exercise real speech in the published payload, and restart the preserved actual profile. Record final evidence in release notes and local complie/VERIFICATION.md.

Alice, streamer and Minecraft searches must distinguish direct text-to-speech from RVC/So-VITS voice conversion. A renamed archive is not a compatible model; unsupported packages must not become fake downloadable TTS cards.
