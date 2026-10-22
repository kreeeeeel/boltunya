# Changelog

Release versions are assigned by Windows CI. Each GitHub release includes the first dated entry below, the tested source commit and its verification run.

## 2026-10-22 — Shared speech updates and indexed event pagination

### Changed

- Share one speech-library snapshot, in-flight request and active-download timer across the title bar, catalog, settings and chat controls. Preserve explicit refresh, installation, cancellation and model import; unchanged responses keep the previous snapshot identity.
- Seek directly into the existing ordered event index for older pages. Retention visits only expired keys instead of copying and scanning the complete index for every request. Keep the same JSON files, cursors, account isolation and storage accounting; no database migration is needed.

### Fixed

- Suspend speech-status requests when the window is hidden and refresh on return. Release the timer, pending request and event listeners when the final consumer closes. Ignore aborted or superseded responses, and combine refreshes during an in-flight request into a fresh follow-up.
- Stop retaining the chat's voice-readiness subscription after its assistant menu closes. Keep installation action errors local to the initiating control while synchronizing the shared catalog after completion.
- Revalidate the speech catalog when another voice view opens, recovering from transient read failures and external file changes. Join an existing request and replace its download timer instead of retaining stale availability or creating parallel requests.
- Preserve exclusive event cursors when the referenced record is absent, inserted around or removed before restart. Retention keeps all events exactly at its cutoff, including timestamp ties and early-epoch boundaries.

### Verification and limitations

- A synthetic real-React comparison with three consumers and a download recorded 15 → 5 status requests in five seconds and 3 → 1 timers. Two consumers recorded 10 → 5 requests. The following hidden-window intervals recorded 10/15 → 0 requests; idle initial requests fell from 3 to 1, with no idle timer. Raw timestamps and final source hashes are retained. Neither scenario made network calls or accessed user data.
- The compiled event store with 200,000 warm index keys, 62 synthetic summary files and 30-entry pages recorded median deep-page times of 5.039 → 1.221 ms; with retention enabled and no expired entries, 12.272 → 1.004 ms. IDs, order, exclusive cursors and totals matched. The first-page control also moved from 2.834 to 1.401 ms, so these local timings include JVM/filesystem/load effects; the change removes avoidable index scans and is not a whole-application CPU or latency claim. Cold index loading, model inference and RVC conversion are outside this stage.
- Local gates passed: 640 Kotlin cases with 18 optional/platform skips and zero failures, 12 installed SDK cases, 315 UI tests including 17 targeted lifecycle cases, 40 Electron unit cases, the icon script and all 143 browser scenarios. Lint, styles, types and formatting passed. Native window/tray/startup and four updater smokes passed again after the final rebuild. Each runtime adjustment was rebuilt and opened before follow-up checks; Telegram authorization, credentials, settings, installed voices and manual memory notes were preserved, with AI paused. Exact published-payload verification is recorded in the release after successful Windows CI.

## 2026-10-22 — A leaner assistant workspace and local character voices

### Added

- Add a top-level Speech models workspace with Engine / Recognition / Voice tabs, searchable Russian/English cards, shared Refresh/Explorer/Import actions and separate voice-preview dialogs. Show required engines, formats, download sizes and source/license links.
- Offer 32 synthesis voices: 17 Piper voices and 15 optional community RVC conversions. The pinned Applio 3.7.0 integration converts local Piper speech to character timbre before Opus encoding. Install prerequisites in order with shared progress, cancellation, verified SHA-256 and safe ZIP64 extraction.
- Add inherited or independent recognition, voice, rate and text/voice policy per chat. Apply effective preferences to replies, voice drafts, selected history and memory transcription. Organize chat settings with sidebar navigation and compact sections.
- Display active model/engine downloads in the title bar, with transfer name, fill, percentage, measured speed and hover-to-cancel. Retain explicit Text/Voice generation and manual spoken-text composition.
- Display Telegram forward origins, a generic marker for unknown forwarded sources and read receipts in chat previews. Use Golos Text from the Persons interface and localized English/Russian documentation screenshots.

### Changed

- Replace ordinary message composition with a compact assistant bar: current model, memory, voice and AI actions. AI-generated text remains explicitly editable and sendable; manual voice composition remains available.
- Use borderless searches/model cards with neutral selected backgrounds, installed-model filters, centered source links, filled tabs and a compact rounded slider layout throughout settings. Move voice-library previews into dialogs.
- Size memory work units from the model's token budget across native Telegram pages, preserving whole messages, exclusions and continuation cursors. Keep the configured automatic interval and explicit deep collection behavior.
- Debounce chat search, retain a centered empty state and stop history polling when the conversation is not visible. Reuse unchanged history status instead of forcing parent renders on every poll.
- Cache up to 64 small archive buckets, with file identity/size/modification checks and write invalidation, to avoid repeatedly reading and decoding unchanged JSON. Keep JSON/Markdown storage and account-scoped history compatibility.
- Reduce desktop state polling from 1.5 to 5 seconds; explicit controls still synchronize immediately. Avoid media/waveform construction when generating chat-list previews, use indexed reply lookups and preserve memoized message/chat rows on unrelated activity changes.
- Compare only an unchanged latest history window before falling back to the existing merge, retaining the complete paginated list by identity. Continue applying edits, deletions, media updates and context exclusions through the full merge when needed.

### Removed

- Remove the Errors workspace, technical journal and its OpenAPI/SDK operations, diagnostic resources and standalone support-report script. Stop writing backend, managed Ollama and updater failure logs. Existing private log files are preserved and no longer read; current action errors and AI task outcomes remain visible. Update handoff, health and rollback markers remain functional.
- Remove internet search, current-information planning, search credentials UI, related backend integrations, dependencies and API operations. Replies use the configured model and available conversation context.
- Remove standalone photo/video viewing, manual photo/video/document uploads and their sending/gallery endpoints. Received media remains inline; voice-note sending and model-package import remain supported.
- Remove the custom palette/CSS theme editor and appearance API. Built-in light/dark themes and language selection remain available. Existing private profile files are preserved.
- Remove the resource-charts page, metrics endpoint, hardware sampling and OSHI/Recharts dependencies. Queue parallelism retains its qualitative load estimate without polling hardware.

### Fixed

- Wait for the Electron bridge title bar to mount after browser reload before asserting its exact height, drag region and control actions. Preserve the same UI requirements under slower CI rendering.
- Serialize typing-indicator cancellation with task start/stop and avoid Kotlin's size-based snapshot shortcut on a concurrently changing map. Closing a queue can no longer fail while its last worker removes the typing task.
- Repair the frontend lockfile with the same npm version used by CI, including missing optional WASM peer dependencies. Verify clean dependency installation rather than weakening `npm ci`.
- Do not start or warm local models automatically while AI is paused. Recheck pause/account validity after history and transcription and between memory-model calls. Retain explicitly requested manual collection; release app-tracked models, including configured Ollama profiles on the managed endpoint, without unloading independent servers.
- Do not show pending or failed outgoing Telegram messages as read before they have a confirmed positive message ID.
- Keep per-chat voice overrides independent of unrelated global changes. Recheck account/session and recognition preferences before committing transcribed memory; disabled transcription preserves the original history.
- Correct bubble and voice-note rounding, reply-preview spacing, compact voice-dialog actions and model-card geometry. Prevent stale preview selection and dependency-progress errors.

### Verification and limitations

- A controlled synthetic Node 24.11.1 comparison against `49c1f72` used 10,000 retained messages, 100 unchanged recent messages, 10 warmups and 100 measured refreshes. Merge plus structural sharing averaged 7.318 ms before and 0.662 ms after, with identical content. This approximately 11× improvement describes that operation, not whole-application latency.
- Real cached Alice verification passed Piper → RVC → Opus → Whisper, with meaningful transcription and a converted waveform distinct from the base voice. Installation took 420.305 s; synthesis of a 3.260 s recording took 82.607 s on two CPU threads; the full isolated check took 508.497 s. No Telegram messages or conversation-model requests were sent. Other RVC voices have reviewed metadata, not individual audio verification.
- Final local gates passed 634 Kotlin cases (18 optional/platform skips, zero failures), all 12 installed SDK tests, 303 UI tests, 40 Electron unit tests and the icon-rendering script test. All 143 browser scenarios passed after the final rebuild, with localized screenshot review. Native window/tray/Windows startup and all four updater smoke checks passed again after the typing-cancellation fix. The new lifecycle regression checks 200 concurrent stop/cancel/close rounds; retired journal routes return 404 without touching legacy files. Published-payload and preserved-profile verification are recorded in the release after publication.
- Applio is an optional 6.31 GiB download, approximately 9.67 GiB unpacked, and is expensive on CPU. Arbitrary RVC checkpoint import is not supported. Existing installed foreign voices remain usable; new downloads focus on Russian and English. Community names do not imply endorsement or dataset rights. DonationAlerts' full server-side catalog is not exported by its documented public API and is not claimed here.

## 2026-10-22 — Voice catalog, manual recordings and transcribed history

### Added

- Expand the optional local catalog to 63 synthesis voices across 39 languages, including four compatible GLaDOS voices. Add searchable synthesis/recognition/engine tabs, language filters, source and license details, and compact download cards. Existing asset identifiers and imported voices remain compatible.
- Add a microphone action beside the composer. Choose an installed voice and speech rate, enter exact text, optionally preview it, or synthesize and send a native voice note in one action. Keep the ordinary text draft, use the shared cancellable queue, and protect against duplicate or uncertain sends.
- Transcribe eligible voice notes from both participants throughout the history selected for a reply. Apply the same recognition to selected memory batches and recheck their token budget after transcription. Preserve author metadata, automation provenance and context exclusions.
- Expose typed speech operations through the hand-written Kotlin SDK facade as well as the generated clients. Extend the canonical draft request with optional per-request voice and speed, without changing saved defaults.

### Changed

- Move automatic reply format and random voice probability into Assistant → Replies. Group runtime, recognition, synthesis, testing and importing under Models and tools → Speech models.
- Remove emoji and sticker pickers from the message composer and editing toolbar. Received stickers and existing message formatting remain supported.

### Fixed

- Retain native outgoing voice and attachment inputs independently of temporary drafts. Include the bounded outgoing cache in cache statistics and cleanup, and keep uncertain native uploads pinned until successful completion rather than treating a timeout as delivery.
- Recover registered older generated voice files within their own account/session, refresh completed media state, and retry failed audio loads. Keep playing audio intact when Telegram confirms a download or supplies its identity.
- Reduce spacing between voice waveforms and message timestamps, and remove the photo-style time overlay from audio messages.

### Verification and limitations

- The local Gradle gate passed 644 Kotlin cases with zero failures and 20 opt-in/platform skips, plus all 12 installed TypeScript SDK tests. Electron and script suites passed 39 and 10 tests respectively.
- All 281 UI cases were covered by the full run and focused corrections. All 141 browser scenarios were verified across the full run and final 16 speech cases, including playback and geometry in both themes at 1440/390 px and a short 640 × 520 window. Screenshot review caught and fixed a 480/520 px modal-container mismatch; field/button containment is now asserted explicitly.
- Real GLaDOS (English) and Dmitri (Russian) installation → synthesis → Opus → Whisper verification passed in 77.408 s. Their individual synthesis calls took 3.102 s and 5.313 s on this machine under load; these are observations, not latency guarantees. The final real settings HTTP upload/import/preview and chat-bound manual synthesis check passed in 29.294 s. Two identical send requests delivered one recording to a synthetic Telegram gateway, with unchanged preferences and no conversation-model call or real Telegram send.
- Local quality gates, browser checks, real speech verification and publication results are recorded with the release. Published EXE/SDK checksums, source/tag correspondence and actual-profile restart must be verified separately from a local build.
- Recognition remains opt-in. Deleted or excluded messages are never transcribed for model context; automated replies never become owner-style examples. No automatic chat permissions are enabled by this update.
- Model weights remain explicit optional downloads. Catalog compatibility and pinned archive metadata are checked for every entry; real inference checks sample selected voices, not all 63. Dataset and character rights vary and are disclosed separately from software licenses.
- Alice, Bratishkinoff and Minecraft packs found during research use RVC or So-VITS-SVC voice conversion and cannot run in the current Piper engine. They are documented with their sources rather than offered as incompatible downloads.
- Tests use synthetic speech and conversations. No test messages are sent to people, and recurring enhancement automation remains paused.

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
- Synchronize native profile-migration verification with actual update confirmation and graceful process exit. Check pending and confirmed states separately, retaining strict cleanup and profile-preservation assertions on slower CI disks.

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
