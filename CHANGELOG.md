# Changelog

Release versions are assigned by Windows CI. Each GitHub release includes the first dated entry below, the tested source commit and its verification run.

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
