# Spring migration

The migration keeps the Electron shell, static Next.js interface, portable executable and existing JSON/Markdown profile. Spring is the chosen backend framework; a database is not required.

## Checkpoint — 21 October 2026

- [x] Select a compatible baseline: Spring Boot 4.1.1, Spring Framework 7.0.9, Spring AI 2.0.1, Kotlin 2.3.21, Gradle 8.14.3 and JDK 21.
- [x] Replace Guice with Spring constructor injection, components and feature-specific `@Configuration` / `@Bean` integrations. Remove the old module and dependency.
- [x] Keep startup explicit, close the application through the context and retain the loopback descriptor/session protocol.
- [x] Place internal `StoredUpload` state in `domain.media`; improve upload and lifecycle readability.
- [x] Local verification: 400 Kotlin tests (11 opt-in live tests skipped), 153 UI unit tests, 38 Electron tests, 10 script tests and 107 browser scenarios; formatting, lint and type checks passed.
- [x] Package and launch `complie/Aigram.exe` 2.6.29 with bundled Java. Native window/tray/startup, updater recovery/profile migration and media smoke checks passed. Existing Telegram authorization, settings and manual notes survived the actual restart.
- [ ] Verify the pushed checkpoint's Windows release and SHA-256. The release workflow publishes the matching source commit, meaningful changelog and verification link; inspect [Windows Actions](https://github.com/kreeeeeel/boltunya/actions/workflows/release.yml) and the matching release before marking publication complete.

## Next checkpoint — generated contracts

1. Inventory the existing operation IDs, methods, URLs, request/response shapes and local security tests before replacing handlers.
   The current source contains 59 paths, 73 operations and 78 schemas. Three operations lack `operationId` (`GET`/`PUT /api/model-prompts` and `POST /api/prompts/budget`); 38 operations lack tags. Verify their actual payloads and media range/error responses before code generation.
2. Split the single OpenAPI source into feature operations and individual model files. Add contract API, Kotlin client, TypeScript client and handwritten client-wrapper Gradle modules inside `backend`; keep the executable backend module until consumers can switch together.
3. Generate Kotlin Spring interfaces/DTOs, the Kotlin WebClient client and a complete TypeScript HTTP client into `build/generated`. Validate all references before generation, clear obsolete output and verify generation from a clean checkout.
4. Implement Spring MVC controllers and per-feature transformers. Add Jakarta validation and one exception handler without changing required desktop/web contracts. Preserve upload limits, static exports, media access, loopback binding and session/Host/Origin checks.
5. Update web/Electron consumers and build ordering, run compatibility and native startup/update tests, then delete `ru.aigram.http`, its server/router/handlers and replaced dependencies. The old HTTP bridge is a temporary adapter, not a completed HTTP migration.

## Following checkpoint — functional packages and cleanup

Move remaining flat domain/storage/service types by feature (`chat`, `media`, `memory`, `ai`, `settings`, `events`), keeping domain models separate from generated transport DTOs. Refactor each affected area with multiline braced conditionals, meaningful method spacing, one class per file and Java Stream API. Delete replaced code, duplicate models, obsolete imports and temporary adapters after verifying their consumers.

## Required verification

Run `:backend:check`, generated-contract validation and clean generation when introduced, web/desktop checks, browser scenarios and isolated native Electron/tray/updater/media tests. Test profile locking, context startup/close, shared queue identity, memory scheduling and local API security. Preserve actual authorization and notes; never use real Telegram sends or download models for tests. Every published release needs a changelog, green Windows CI, matching tag/source commit and an EXE whose SHA-256 matches the release checksum.

Compatibility references: [Spring AI versions](https://docs.spring.io/spring-ai/reference/getting-started.html), [Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html), [Boot Kotlin support](https://docs.spring.io/spring-boot/reference/features/kotlin.html), [Kotlin/Gradle compatibility](https://kotlinlang.org/docs/gradle-configure-project.html).
