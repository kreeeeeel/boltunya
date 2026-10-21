# Changelog

Release versions are assigned by Windows CI. Each GitHub release includes the first dated entry below, the tested source commit and its verification run.

## 2026-10-21 — Generated contract and HTTP clients

### Added

- Added Gradle libraries for generated Spring API interfaces/DTOs, the Kotlin WebClient SDK and its typed client wrapper, plus a complete TypeScript Fetch SDK with JavaScript and declarations.
- Added an installable `Aigram-API-Client.tgz` release asset with a matching application version and SHA-256 checksum. SDK builds remain local/GitHub artifacts; nothing is published to an npm registry.
- Added semantic compatibility tests for the original 73 operations and 78 models, generated type checks, isolated regeneration tests and HTTP client acceptance tests.

### Changed

- Split the single OpenAPI source into eight functional operation documents and individual model/enum files. Added missing operation IDs and tags, extracted shared enums/request models and retained existing JSON wire shapes.
- Validate the full schema tree, then create one bundle preserving named references before generating any client or server code. This avoids nested models degrading to untyped objects in direct multi-file generation.
- Keep all generated code under module `build/` directories. Frontend types are regenerated automatically before type checking rather than checked into Git.
- Centralized Gradle plugin versions and included contract/client verification in the main quality gate and Windows release build.

### Verification

- Client tests cover session rotation, isolated credentials, large string IDs, nullable responses, omitted PATCH fields, binary media and errors without retries. The TypeScript archive is installed into a separate consumer project and type-checked.
- Regeneration is tested in an isolated Gradle project using the actual API build and templates: removing a model must remove both its generated source and compiled class without a clean build.
- Existing application, browser and native window/tray/updater/media checks remain required before release publication. Tests use synthetic data and do not send Telegram messages, download models or change the user's profile.
- Local checks passed: 415 Kotlin tests (11 optional live tests skipped), seven installed SDK tests, 153 UI unit tests, 38 Electron tests, 10 script tests and 107 browser scenarios. Native window/tray/startup, updater recovery/profile migration and media checks passed; restarting the actual application retained Telegram authorization, settings and manual notes.

### Compatibility and remaining migration

- Spring Boot already owns dependency injection. This release establishes contract/client modules; the existing private HTTP bridge still serves application requests. Spring MVC controllers, consumer transport cutover and deletion of `ru.aigram.http` remain the next checkpoint.
- The generated server API targets Boot 4/Jackson 3. OpenAPI Generator 7.25.0's Kotlin WebClient template uses supported Jackson 2 codecs on Spring 7; this compatibility setting and its deprecation notices are documented.
- Telegram sessions, API routes, the local cookie protocol, memory, settings and Electron startup are preserved. See `docs/migration/en.md` and `docs/api-client/en.md`.

## 2026-10-21 — Spring container migration

### Changed

- Replaced Google Guice with Spring Boot 4.1.1 dependency injection, constructor-injected components and feature-specific integration configurations.
- Aligned Kotlin 2.3.21, Spring Framework 7.0.9 and Jakarta validation with Boot dependency management; retained Spring AI 2.0.1 and JDK 21.
- Kept application startup explicit and coordinated shutdown under the Spring context, including the shared reply/memory queue and app-managed integrations.
- Moved internal upload state into `domain.media` and expanded compact conditionals in the upload and application lifecycle code.
- Added the Java instrumentation module to the bundled runtime and rebuild outdated cached runtimes when required modules change.

### Fixed

- Made resource shutdown idempotent so a failed Spring context refresh releases already-created queues, stores and HTTP listeners.
- Stabilized sticker-picker alignment checks by measuring the pack label and its row together while the popup repositions; the one-pixel alignment requirement is unchanged.

### Verification

- Spring context tests check the complete dependency graph, shared service identities, deferred providers, saved startup preferences and shutdown without logging into Telegram or calling a model.
- Existing Kotlin, web, desktop, browser and isolated native smoke checks remain required by Windows CI before publication.
- Local checks passed: 400 Kotlin tests (11 optional live tests skipped), 153 UI unit tests, 38 Electron tests, 10 script tests and 107 browser scenarios; window/tray/startup, updater recovery/profile migration and media smoke checks also passed. The actual application restart preserved Telegram authorization, settings and manual notes.

### Compatibility and remaining migration

- Existing profile paths, Telegram credentials/session, JSON/Markdown stores, API URLs, local session cookie and Electron launch protocol are preserved.
- This is the dependency-injection stage. The existing private HTTP bridge remains temporarily; generated Spring MVC APIs and contract/client Gradle modules are the next migration stage. See `docs/migration/en.md`.
- No models are downloaded and no Telegram messages are sent by the migration tests.
