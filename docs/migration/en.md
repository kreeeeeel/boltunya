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

## Current checkpoint — generated contracts

- [x] Preserve a semantic baseline for all 59 paths, 73 operations and original 78 schemas. Add the three missing operation IDs and feature tags. Compatibility tests compare each resolved operation/model, including optional PATCH fields, nullable values, string IDs, binary uploads and the `tiho` session scheme.
- [x] Split the single source under `backend/contract/api/schema`: eight feature operation files and one file per named model/enum. Extract 28 shared enums and two request models without changing their resolved wire shapes; the root now contains 108 named schemas.
- [x] Add contract API, generated Kotlin WebClient, typed Kotlin wrapper and full TypeScript HTTP SDK Gradle modules. One Gradle bundle preserves named nested references; every generator consumes that bundle after validating the original tree. Direct generation from the split files lost types in OpenAPI Generator 7.25.0, so bundled-type checks are required.
- [x] Keep generated sources, compiled SDK output and npm tarballs under `build/`; remove the tracked frontend declaration file. Current web `openapi-fetch` types derive from the same canonical source until its transport switches with the controllers.
- [x] Local verification: 415 Kotlin tests (11 optional live tests skipped), seven installed TypeScript SDK tests, 153 UI unit tests, 38 Electron tests, 10 script tests and 107 browser scenarios passed. The isolated Gradle regeneration test removed obsolete source and classes without a clean build. Native window/tray/startup, updater recovery/profile migration and media checks passed.
- [x] Built and launched local `complie/Aigram.exe` 2.6.30. The actual restart retained Telegram authorization, settings and manual memory notes. The SDK archive has the matching version and is included in the checksum file.

Publication is verified against the pushed source commit, green [Windows Actions](https://github.com/kreeeeeel/boltunya/actions/workflows/release.yml), published EXE/SDK digests and an actual launch of the downloaded executable. The release page and local `complie/VERIFICATION.md` record that result; a successful local package alone is not publication.

Usage and compatibility details: [API clients](../api-client/en.md). The Kotlin SDK uses the generator's supported Jackson 2 WebClient codecs on Spring 7; the generated Spring API uses Boot 4/Jackson 3.

## Next checkpoint — Spring MVC cutover

1. Implement the generated Spring interfaces with thin feature controllers and transformers. Apply Jakarta validation and a single exception handler; keep existing required methods, URLs and JSON payloads.
2. Replace the private server and routing with Spring MVC. Verify binary uploads, media range/error responses, static exports, loopback binding, the descriptor/session protocol, Host/Origin protection and graceful shutdown against the existing HTTP tests.
3. Switch web/Electron consumers to generated transport, run browser and native startup/tray/updater checks, then remove `ru.aigram.http`, its server/router/handlers and dependencies. The current bridge is explicitly temporary; this foundation release does not complete HTTP migration.

## Following checkpoint — functional packages and cleanup

Move remaining flat domain/storage/service types by feature (`chat`, `media`, `memory`, `ai`, `settings`, `events`), keeping domain models separate from generated transport DTOs. Refactor each affected area with multiline braced conditionals, meaningful method spacing, one class per file and Java Stream API. Delete replaced code, duplicate models, obsolete imports and temporary adapters after verifying their consumers.

## Required verification

Run `:backend:check`, generated-contract validation and clean generation when introduced, web/desktop checks, browser scenarios and isolated native Electron/tray/updater/media tests. Test profile locking, context startup/close, shared queue identity, memory scheduling and local API security. Preserve actual authorization and notes; never use real Telegram sends or download models for tests. Every published release needs a changelog, green Windows CI, matching tag/source commit and an EXE whose SHA-256 matches the release checksum.

Compatibility references: [Spring AI versions](https://docs.spring.io/spring-ai/reference/getting-started.html), [Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html), [Boot Kotlin support](https://docs.spring.io/spring-boot/reference/features/kotlin.html), [Kotlin/Gradle compatibility](https://kotlinlang.org/docs/gradle-configure-project.html).
