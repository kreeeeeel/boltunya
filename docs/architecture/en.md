# Architecture

Electron owns the frameless native window, tray and startup registration. It starts a single Kotlin process, waits for the private loopback session descriptor, loads the static Next.js export and removes the handshake secret from browser history. The UI has no Node access; the isolated preload exposes a narrow, sender-checked IPC API.

## Modules

- `desktop/`: backend lifecycle, native window controls, compact Gravity-based tray and cached startup registration.
- `backend/`: Kotlin under `ru.boltunya`, Google Guice constructor injection and explicit singleton bindings. `ApplicationRuntime` starts and closes services and owns the profile lock.
- `http/`: session checks, routing, input validation, resources and feature-specific endpoints. `SettingKey` validates PATCH value types.
- `telegram/`: connection state, cache, versioned update handlers, chat directory, history, media/wallpaper mapping and threaded message sending.
- `ai/`: Spring AI integration, typed conversation context, resource-based model catalog, local model management and personal writing samples; `model/` owns inference and final responses, while `research/` provides web and weather lookup. See [AI services](../ai/en.md).
- `automation/`: sequential reply queue, debounce, stale-result cancellation, pacing and Telegram flood-wait handling.
- `flows/`: validated acyclic node graphs, command/agent/reply/storage/condition execution, per-flow/per-chat JSON storage and side-effect-free draft previews.
- `monitoring/`: OSHI CPU/RAM/process/network samples and optional NVIDIA GPU readings.
- `web/src/modules/`: workspace, flow editor and metrics UI; hooks separate editor state from presentation. Shared code includes the generated API client, themes and RU/EN/KK/UK dictionaries.

Each Kotlin class has its own file. Prompts, regex, error text and model metadata live in resources. Language regex packs are combined using `languages.json`. Resource templates perform a single substitution pass, so user-supplied variables are not expanded recursively. Concrete exceptions live in `exception/impl` with stable error codes.

## State and delivery

`%LOCALAPPDATA%/Tiho` is retained for existing sessions. Settings and flow data are atomically replaced; credentials use Windows DPAPI. TDLib owns its session database. Existing automated message IDs drive local AI badges and exclusion from personal writing examples.

Ordinary launches pause the queue. Startup resumes it only when explicitly enabled in saved settings. Groups default to disabled and support global/per-chat mention/all overrides. Outgoing messages reply to the source message and forum topic. Local media rendering includes photos, video, audio and stickers; model context includes text/captions, not visual or speech recognition.

Full exit closes the reply queue, HTTP bridge, app-managed Ollama, monitoring and Telegram. External Ollama servers are not terminated. Electron waits for the backend to flush its session before exit.

## Packaging

The Windows build exports the frontend, bundles it with the Gradle backend distribution, creates a Java runtime using jlink and packages Electron as `complie/Болтунья.exe`. Ollama and model weights remain optional downloads. Windows CI checks code, builds the executable, runs browser scenarios and publishes a release on main. Docker recipes are development/checking tools; the signed-in Windows desktop session is not containerized.
