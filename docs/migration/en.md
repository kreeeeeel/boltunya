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

## Current checkpoint — Spring MVC and functional domain packages

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

## Required verification

Run `:backend:check`, generated-contract validation and clean generation when introduced, web/desktop checks, browser scenarios and isolated native Electron/tray/updater/media tests. Test profile locking, context startup/close, shared queue identity, memory scheduling and local API security. Preserve actual authorization and notes; never use real Telegram sends or download models for tests. Every published release needs a changelog, green Windows CI, matching tag/source commit and an EXE whose SHA-256 matches the release checksum.

## Backlog after migration verification

1. **Feedback-based local quality comparisons.** Add an opt-in replay set built from user-approved rated replies, complementing the existing synthetic model matrix. Compare factual accuracy, adherence to the selected tone, time to first token and token usage on the same inputs; replay must never send Telegram messages or automatically train a model. Ratings and traces already exist; this curated comparison workflow does not.
2. **Edit messages from the conversation.** Add explicit editing of the owner's sent text and media captions through TDLib, with permission checks and visible edit errors. Incoming Telegram edits are already tracked; the missing feature is an outgoing edit action and editor, rather than another history-refresh mechanism.

These are follow-up candidates, not part of this migration's implementation or release gate.

Compatibility references: [Spring AI versions](https://docs.spring.io/spring-ai/reference/getting-started.html), [Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html), [Boot Kotlin support](https://docs.spring.io/spring-boot/reference/features/kotlin.html), [Kotlin/Gradle compatibility](https://kotlinlang.org/docs/gradle-configure-project.html).
