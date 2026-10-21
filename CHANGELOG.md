# Changelog

Release versions are assigned by Windows CI. Each GitHub release includes the first dated entry below, the tested source commit and its verification run.

## 2026-10-21 — Local speech recognition and voice replies

### Added

- Add optional local voice-note recognition through Whisper tiny and synthesis through Piper, using the official sherpa-onnx Java API in an isolated CPU worker. Add explicit, cancellable, SHA-256-verified downloads for the Windows runtime and Russian/English voices, with upstream license information.
- Add Assistant voice settings: incoming recognition, text/always/random/voice-to-voice replies, probability, model selection and speech rate. Existing chat permissions and the shared bounded queue remain authoritative; speech starts disabled.
- Add manual Text/Voice generation, a playable voice draft and editable transcript. Editing invalidates old audio; resynthesis queues the exact corrected text without asking the conversation model to rewrite it. The regular composer draft stays intact.
- Send native Telegram OGG/Opus voice notes with duration, packed waveform, reply target and topic. Show recognition and synthesis phases in events. Extend the canonical contract and generated Kotlin/TypeScript clients with typed speech operations and settings.
- Add real voice testing directly in settings: speak exact entered text with the currently selected voice/rate, even before saving, with queue state, cancellation and playback. Preview-only audio cannot be sent to Telegram, and no conversation model is called.
- Add a consistent Refresh/Explorer/Import toolbar and user-provided sherpa-onnx VITS/Piper voice packages. Validate bounded archives and required sidecars, reject paths/links/executables, preserve notices, and publish model files and their local registry atomically. Imported voices survive restart and appear in the normal selector.

### Fixed

- Remove the profile dropdown's outer border. Keep speech downloads and voice-draft controls contained in compact and wide windows, with four-language labels and both themes.
- Bind generated recordings to their owner and chat, protect repeat sends, preserve uncertain native uploads and expire ordinary old previews. Stop the owned speech worker on cancellation; avoid passing private text on command lines or logging native speech output.
- Buffer compressed model archives during extraction and check cancellation beneath the buffer, including skipped entries. A manual reply to a voice note with recognition disabled reports the missing opt-in instead of answering a placeholder.

### Verification and limitations

- The latest real settings HTTP check passed with a 21 MB Piper upload, import, synthesis and playable audio in 15.782 s. The pinned-installation → Russian synthesis → Opus encode/decode → Whisper cycle passed in 36.130 s, preserving spoken words; punctuation may differ. All three curated voices passed installation/synthesis verification in 34.490 s. These are complete test timings, not latency guarantees.
- The final Gradle gate completed with 619 Kotlin cases, zero failures and 14 skips (12 unrelated opt-in AI/network/memory cases and two Windows symlink-privilege cases). All 12 installed SDK tests, 257 full UI tests, 39 Electron tests and 10 script tests passed; a final focused run passed 16 tests across four files, including three new hook regressions. Codec, native-send and HTTP regressions cover cancellation, owner isolation, ranges, strict settings and duplicate delivery.
- The earlier intermittent Ruslan installation error did not recur in standalone/final all-voice verification; its cause remains undetermined, and test-only diagnostics were added. The full browser run passed 135 scenarios, and all eight final voice scenarios passed after the last heading/hook changes.
- The actual unpacked Aigram 2.6.39 passed the new packaged speech smoke in 44.259 s: real API synthesis (10.850 s), settings synthesis/playback (10.011 s), Ogg audio and advancing playback, zero model-inference/unexpected requests, no signed-in Telegram account or chats, unchanged unsaved preferences and removal of the backend session descriptor. Synthetic evidence is retained in `build/speech-verification/packaged-proof/run-KslmQb`.
- The local portable EXE's extracted payload also passed real speech verification in 76.760 s. Native window, tray and Windows startup checks passed. Two additional notice-installation/provenance tests passed; vendored license bytes remain unchanged by Git checkout normalization.
- Windows CI publication, source/tag and downloaded EXE/SDK checksum verification, testing the published executable and restarting the actual profile remain required. The local unpacked result does not verify a published release; its separate result must be recorded in the release notes and local `complie/VERIFICATION.md`.
- Settings use real local synthesis, upload and import operations. Synthetic conversations and prepared browser audio are browser test fixtures. No Telegram messages were sent to people during verification; recurring enhancement automation remains paused.
- The recognition cache is temporary, but recognized text sent to the model and signed-in preview text can be retained in the private local activity history. Its existing retention and cleanup preferences apply; this is separate from public error diagnostics.
- Initial speech support recognizes the latest relevant incoming voice note and reuses session transcripts. Historical bulk transcription and durable-memory transcription are not included. CPU speed and transcription quality depend on hardware, language and recording quality; ten-minute/25 MiB input limits apply.
- Speech runtime and model weights remain separate optional downloads. Voice datasets carry different terms, including non-commercial or unspecified licenses; review the model card before use. Bundled codec libraries and separately installed speech assets include notices and source/build references.

## 2026-10-21 — Reliable reply output and configurable idle messages

### Added

- Add per-chat idle messages with an explicit AI or predefined-text mode, a custom delay and an independent per-chat opt-in. Settings lists the configured chats and opens their communication controls.
- Add an optional Assistant setting for moderate AI formatting. Convert supported Markdown into native Telegram entities, keep rich generated drafts editable, and retain formatting in the first attachment caption. The default remains off.
- Extend the canonical contract and all generated clients with optional formatting entities, the formatting preference and typed idle-message configuration.
- Add global and per-chat incoming-message policies: restart, finish and combine, or reply to each message sequentially. Preserve the existing restart default and bounded shared queue.

### Fixed

- Decode a recognized conversation-history JSON envelope before validating facts, formatting links or sending the final message. Ordinary JSON/code answers and research/sticker schemas remain intact. Separate output instructions reinforce the selected communication style.
- Detect silence after either participant's last read message. Preserve the shared queue, group permissions, account/settings/activity checks and durable one-follow-up-until-reply guard. Predefined text bypasses model inference and remains literal.
- Validate outgoing entity types, Unicode boundaries, nesting and links before side effects. Preserve unsupported markup as complete literal text rather than losing references or URLs.
- Keep unrelated participant names and tags out of replies through a separate relevance instruction; retain factual memory and the selected tone.
- Serialize replies within each chat, anchor their history to the intended message and distinguish automated outgoing messages through native TDLib send correlation. Manual owner messages retain priority.

### Verification and compatibility

- The full local quality gate passed 548 Kotlin tests (12 optional live tests skipped), 10 installed TypeScript SDK tests, 232 UI unit tests, 38 Electron tests and 10 script tests. Formatting, types, lint, generated-contract compatibility and the optimized web build passed.
- All 127 browser scenarios were verified across the full run and final focused reruns, including both themes and compact windows. Three synthetic cases passed against the already installed `deepseek-r1:8b`: ordinary replies, requested native formatting and unrelated-participant relevance. No real Telegram messages or model downloads were used.
- Release publication additionally requires Windows packaging, native window/tray, updater and media gates, matching EXE/SDK hashes and an actual-profile restart. Prompt adherence remains model-dependent; the live cases are regression checks, not a guarantee for every model or question.
- Updater recovery tests wait for their exact native fixture processes to exit before cleanup, including a deliberately slow restored launcher. Recovery assertions remain unchanged; the test never terminates unrelated applications.
- Existing sessions, memory, prompts and opt-in states remain compatible. No chats or automatic replies are enabled by this update.

## 2026-10-21 — Lossless Telegram message editing

### Added

- Edit the owner's sent text and photo/video captions from the message menu, with a compact visual Gravity editor, formatting controls, undo/redo and an edited timestamp.
- Preserve Telegram entities directly, including overlapping formatting, links, spoilers, custom emoji, user mentions and unsupported entity metadata. Original types stay on the backend; text and ranges never round-trip through Markdown or HTML.
- Add revision-protected edit operations to the canonical contract and all generated clients. Recheck ownership, Telegram permission and account/connection identity before saving; use Telegram's active Unicode character limits.
- Keep the editor open after saving, retain the normal composer draft, warn before discarding edits and preserve a conflicting draft until explicitly reloaded. A failed post-save refresh retries reading without sending the edit twice.

### Fixed

- Resolve event avatars from the exact current Telegram chat, including archived events and detail dialogs. Never reuse archived TDLib file URLs after reconnecting; missing photos show initials.
- Update edited archive content and invalidate only the affected AI context and writing samples. Automated replies retain their provenance and stay excluded from owner-style learning.
- Protect confirmed edits from delayed history polling, including multiple edits with the same second-resolution timestamp. Retain original link previews, photo/video placement and local context exclusions.
- Preserve adjacent copied custom emoji as separate entities and literal Markdown-like text as plain text. Load the visual editor only when it is opened.
- Keep discard confirmation inside one accessible dialog, preserving the mounted editor and undo history. Focus stays on the safe action, and Ctrl/Cmd+Enter cannot save while confirmation is open. Empty captions no longer show an unrelated command-menu placeholder.

### Verification

- The final full Gradle quality gate passed 489 Kotlin tests (11 optional live tests skipped) and nine installed TypeScript SDK tests. The unchanged original wire baseline is checked alongside explicitly reviewed edit operations, entity models and optional `editedAt`.
- The final UI unit suite passed all 213 tests, including the real Gravity view, UTF-16 ranges, overlapping entities, clipboard, IME, undo/redo, save/conflict handling and confirmation accessibility. TypeScript, ESLint, Stylelint and Prettier passed.
- Verified all 117 browser scenarios across the full regression run and the final four editing scenarios: both themes, a compact caption editor, pagination/scroll preservation and stale-history protection. All 38 Electron unit tests and 10 script tests passed.
- Release publication additionally requires the Windows packaging, native window/tray, updater and media gates. Published asset hashes and the actual-profile launch result are recorded in the release verification notes.

### Compatibility

- Existing Telegram sessions, JSON/Markdown stores and settings remain compatible. Telegram's own editing permissions and time limits still apply. Tests use synthetic messages and never edit real conversations.
- TDLib has no atomic conditional edit: changes from another Telegram client between the final revision check and the actual native edit remain subject to Telegram's server ordering.

## 2026-10-21 — Extension-based DTO mapping

### Changed

- Replaced all 21 injectable transformer classes with stateless top-level Kotlin extension functions in their existing feature packages.
- Controllers call `value.toContract()` and `value.toDomain()` directly. Nested mappings reuse extensions instead of constructing or injecting mapping services.
- Kept business operations under Spring constructor injection; mapping functions have no bean registration or lifecycle.

### Verification

- Retained the existing mapping assertions for defaults, nullable fields, large identifiers and byte counts, media metadata, context exclusions and model configuration.
- Added an AST architecture check rejecting classes, objects, non-extension functions, retained state and Spring imports in production transformer packages. The real Spring context also asserts that no transformer bean is registered.
- The full Gradle quality gate passed: 468 Kotlin tests (11 optional live tests skipped), eight installed TypeScript SDK tests, real servlet compatibility/security, contract generation, formatting and the new architecture checks.

### Compatibility

- API paths, generated contracts, JSON fields and persistent profiles are unchanged. Telegram authorization, settings and memory do not require migration.

## 2026-10-21 — Spring MVC and functional domain packages

### Changed

- Replaced the private HTTP server and router with embedded Spring MVC controllers implementing all eight generated API groups, typed business components and explicit DTO transformers.
- Preserved loopback/session protection, Host/Origin checks, static web files, raw uploads, Telegram media ranges and the existing JSON/Markdown profile.
- Moved 120 domain models into feature packages and removed legacy injection annotations in favor of standard Spring constructors and lazy `ObjectProvider` dependencies.
- Expanded conditionals through AST refactoring and corrected generated API numeric formats for wide timestamps, byte counts and durations without narrowing values or changing string identifiers.
- Migrated HTTP integration tests to a real servlet context and added regressions for DTO completeness, binary uploads, strict settings types, security/body limits and failed-refresh cleanup.
- Switched production web calls to generated `@aigram/api-client` feature operations. Removed `openapi-fetch`, `openapi-typescript` and the schema bridge; Gradle, Windows packaging and Docker generate the same SDK before frontend compilation.
- Added six request-specific input schemas with the original defaults while keeping complete response models required. Existing URLs and JSON fields are preserved; Kotlin SDK callers use the corresponding input models. Wide numeric formats and the error code now describe the existing runtime values accurately.

### Added

- Restart app-managed Ollama from its model-page card after changing the compute device. External services cannot be restarted by this action. The API exposes restart availability and returns a typed conflict for unowned processes.
- Consistent status notices for draft preparation and generation failures, with cancellation, dismissal and retry feedback. Long restart labels fit compact windows in both themes.

### Fixed

- Preserved authentication and request limits for decoded and matrix-parameter API paths, including DELETE bodies. Authorization runs before parsing request content.
- Preserved raw uploads with arbitrary MIME types, including WebP, and legacy requests without a Content-Type header. Removed API routes still return the original structured 404 response.
- Kept partial chat rules, nested model options, appearance and prompt settings compatible with their previous defaults. Explicit invalid nulls and scalar coercions are rejected at the HTTP boundary.
- Prevented a completed manual draft error from duplicating its local notice or reviving an older automatic-reply failure after polling.
- Restored complete optional N-API/WASM dependency metadata for clean npm installation after removing the old transport. Existing resolved package versions remain unchanged.
- Aligned the compact Ollama restart button to the left of the installed badge with matching height and corners, added space above engine errors and matched the loading placeholder to the final layout.

### Removed

- Removed the private HTTP server, manual router, old endpoint handlers and `ru.aigram.http`. Removed remaining JSR-330 injection imports and dependencies; Spring owns the service graph.

### Verification

- The full Gradle gate passed: 467 Kotlin tests across application, contract and client modules (11 optional live tests skipped), plus eight installed TypeScript SDK tests. This includes real servlet HTTP/security tests, Spring context lifecycle, clean regeneration and AST architecture/style checks.
- The UI unit suite passed 168 tests; focused reruns after the final form/layout changes covered 21 cases. TypeScript, ESLint, Stylelint and Prettier passed. All 113 browser scenarios, 38 Electron tests and 10 script tests passed.
- Packaged and launched local Aigram 2.6.31 with bundled Java. Native window/tray/startup, updater recovery/profile migration and media checks passed. The packaged JAR contains nine MVC controllers and no legacy HTTP/injection classes or Guice/JSR-330 libraries. Packaged desktop modules, update helper and backend JAR match the checked artifacts.
- The actual profile restored Telegram authorization automatically; saved settings and manual memory notes were preserved. Release publication still requires the green Windows workflow and published EXE/SDK checksum verification.
- A clean isolated installation passed with the CI resolver, npm 11.19.0; npm 11.6.2 compatibility and byte-stable lockfile regeneration were also verified.
- The final Ollama layout passed 11 component tests and five focused browser scenarios, including both themes, 640 px windows, equal control heights, error spacing and stable installation progress sizing.
- No database migration, model download or real Telegram send is required for these tests.

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
