<p align="center"><img src="web/public/assets/brand/boltunya.svg" width="88" alt="Boltunya logo"></p>
<h1 align="center">Boltunya · Болтунья</h1>
<p align="center">Your Telegram conversations. Local AI. Your own voice.</p>
<p align="center">
  <a href="https://github.com/kreeeeeel/boltunya/actions/workflows/release.yml"><img alt="Windows release" src="https://github.com/kreeeeeel/boltunya/actions/workflows/release.yml/badge.svg"></a>
  <img alt="Kotlin 2.2" src="https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Electron 44" src="https://img.shields.io/badge/Electron-44-47848F?logo=electron&logoColor=white">
  <img alt="Next.js 16" src="https://img.shields.io/badge/Next.js-16-111111?logo=nextdotjs">
  <img alt="Gravity UI" src="https://img.shields.io/badge/Gravity_UI-7-FFBE5C">
  <img alt="Google Guice" src="https://img.shields.io/badge/Google_Guice-7-4285F4">
  <img alt="Spring AI" src="https://img.shields.io/badge/Spring_AI-2.0-6DB33F?logo=spring">
  <img alt="Ollama" src="https://img.shields.io/badge/Ollama-local_AI-111111?logo=ollama">
  <img alt="Windows x64" src="https://img.shields.io/badge/Windows-x64-0078D4">
</p>
<p align="center"><a href="https://github.com/kreeeeeel/boltunya/releases/latest">Download for Windows</a> · <a href="docs/ru.md">Русская документация</a> · <a href="#development">Development</a></p>

Boltunya is a Windows desktop application for reading Telegram conversations and managing a personal AI assistant. Choose who it can reply to, shape its tone, and let local models use conversation history and long-term memory. You can also write replies yourself, build command-based workflows, and look up current information without a paid search API.

![Telegram workspace in the light theme](docs/resources/readme-chat-light.png)

*Screenshots use synthetic conversations and illustrative data. No personal Telegram history is included.*

## What it does

- **A familiar conversation view.** Avatars, formatted text, clickable links, replies, photos, video, audio, documents, spoilers and static/animated stickers. Group messages show author photos and names, with consecutive messages grouped by sender. The selected conversation's wallpaper spans the workspace behind opaque panels and floating navigation. History loads as you scroll without losing your position.
- **Replies in your voice.** Real outgoing messages and a shared owner profile inform vocabulary, punctuation and tone. Known AI replies are excluded from owner-style samples. Built-in tones and custom emoji styles are available per conversation.
- **Local models with a useful catalog.** Search 27 downloadable models across Qwen, Gemma, DeepSeek, GPT-OSS, Llama, Mistral and Phi. Compare memory estimates, context, reasoning, license and download size; measure speed on your own machine. Install Ollama and models from the application with progress feedback.
- **Current information, naturally phrased.** Keyless search, Open-Meteo weather, dated news and official exchange rates. Short follow-ups retain the previous topic. Replies are instructed to preserve significant digits and dates while matching your style; links are limited to retrieved sources and follow your preferences.
- **A memory bank you control.** Shared owner facts and habits, per-chat summaries, editable notes, incremental background collection and manual deep rebuilds with progress, queue state and cancellation.
- **Visual workflows.** Connect command, agent, reply, condition and local JSON storage nodes. Test a draft before saving it, without sending a Telegram message.
- **Deliberate automation.** Sequential replies, configurable delays and intervals, optional typing indicators, per-chat controls and group mention/all-message modes. Group automation starts disabled.
- **A visible reply queue.** Pause dispatch, prioritize or stop tasks, and inspect prompts, streamed thinking, responses and token/timing metrics in event cards. [Queue and trace details](docs/activity/en.md).
- **A proper desktop shell.** A draggable title bar, maximize within the Windows work area, compact tray menu, optional startup in the tray, and separate startup/resume preferences. Resource charts show application and system activity.
- **Four languages, two themes.** English, Russian, Kazakh and Ukrainian; light/dark themes and bundled Noto Sans typography.

The chat list uses two-line previews and a small AI badge on enabled conversations' avatars. Press **Esc** to close the current chat; an open dialog is dismissed first. Telegram chat-specific and default light/dark wallpapers are supported when TDLib provides them. Conversation backgrounds appear only while a chat is open on the Chats page. The default conversation background is plain and follows the application theme; unavailable images also use this neutral background.

<table>
  <tr><td><img src="docs/resources/readme-chat-dark.png" alt="Dark conversation view"></td><td><img src="docs/resources/readme-models.png" alt="Searchable model comparison catalog"></td></tr>
  <tr><td colspan="2"><img src="docs/resources/readme-flows.png" alt="Visual workflow editor"></td></tr>
</table>

## Get started

Models are instructed to answer clearly harmful requests with brief, calm disagreement in the owner's voice. Ordinary conversation, figurative language and questions about protecting your own account remain part of normal conversation.

**Windows x64** is supported. Download **Boltunya.exe** from [Releases](https://github.com/kreeeeeel/boltunya/releases/latest) and open it. The portable EXE includes Electron, the Kotlin application, a Java runtime and the static web interface. A separate Java or Node.js installation is not needed to run it. Ollama and model weights are additional downloads managed from the model page. TDLib may require the [Microsoft Visual C++ x64 runtime](https://aka.ms/vs/17/release/vc_redist.x64.exe).

1. **Connect Telegram.** Obtain your own API ID and API Hash at [my.telegram.org/apps](https://my.telegram.org/apps), then follow the sign-in form. In-app guidance explains these fields, the login code and two-step verification.
2. **Choose a model.** Open **AI models**. Existing Ollama installations and downloaded models are detected automatically; complete local models are recognized even while the server is stopped. Install Ollama only if it is missing, then download any additional model you want. Speed and memory labels are estimates; the benchmark measures actual local performance.
3. **Configure a conversation.** Open its conversation settings, enable replies and choose a model, tone, context and writing-style adaptation. Review the global instructions in the profile menu.
4. **Start AI.** Use the title-bar control, or enable **Enable AI when the app starts** in settings. This applies to both ordinary launches and Windows startup. Starting with Windows keeps the window hidden in the tray until you open it.

Use the profile menu for language, theme, settings, custom styles, shared memory and sign-out. Conversation settings contain memory collection and per-chat preferences. Photos, other media and deleted-message snapshots have cache and retention controls.

## Data and privacy

Inference and conversation memory run locally through Ollama. Telegram connectivity uses TDLib. When web search is enabled, external services receive a short generated query; weather services receive a city. The application does not send entire conversations or Telegram credentials to search providers. Exa MCP is an **optional** fallback with your own key; the default search path needs no key.

The persistent profile is `%LOCALAPPDATA%\Tiho` (the legacy directory is kept to preserve existing sessions):

| Location | Purpose |
| --- | --- |
| `settings.json` | Preferences, conversation rules and instructions |
| `telegram.credentials` | API credentials protected by Windows DPAPI |
| `telegram/` | TDLib session database and downloaded media |
| `memory/MEMORY.md`, `memory/<chat-id>/MEMORY.md` | Shared and conversation memory with editable notes |
| `history/`, `automated-replies.json` | Observed message snapshots and known AI message IDs |
| `engine/` | App-managed Ollama runtime |

Login codes and Telegram two-step passwords are not persisted by the application. Local memory and message snapshots contain private information; the profile, credentials, build outputs and local logs are excluded from Git. Deleted messages can only be retained if the application observed them beforehand. Clearing downloaded media does not sign you out or remove your settings and memory.

Full exit stops the application-managed Ollama process. An independently managed Ollama server is left running. Closing to the tray keeps the application active according to your settings.

## Project structure

| Directory | Responsibility |
| --- | --- |
| `desktop/` | Electron window, isolated preload, native lifecycle, tray and startup |
| `backend/` | Kotlin/JVM, Google Guice, TDLib, automation, search, memory and local API |
| `web/` | TypeScript, React, Next.js static export, Gravity UI and generated API types |
| `.github/workflows/` | Windows checks, portable packaging and GitHub Releases |
| `docker/` | Development/checking containers; not the signed-in desktop application |
| `scripts/` | Windows build, icons and browser verification |
| `docs/` | English/Russian feature documentation and synthetic screenshots |
| `complie/` | Generated EXE and SHA-256 checksum; intentionally ignored by Git |

The Kotlin package is `ru.boltunya`. Prompts, regex language packs, errors and model metadata live in backend resources. The OpenAPI contract is `backend/contract/api.yaml`; `openapi-typescript` generates the frontend types. The local API uses a private loopback session and Host/Origin checks. The renderer has no Node.js access.

Spring AI's Ollama adapter provides typed requests, structured responses, separate thinking output and inference statistics. `ConversationService` coordinates search and reply quality; `ChatCompletionService` owns inference, and `OllamaRuntime` owns local model management. Spring AI `ChatMemory` builds the recent conversation window; the Markdown memory bank retains long-term summaries and owner facts. Guice wires these services without starting a Spring Boot application. See [AI architecture](docs/ai/en.md).

Conversations follow the global model selection by default; an explicit per-chat override remains available. Speed measurements persist across restarts and run automatically when you select an unmeasured installed model. Downloads use an inline progress button with cancellation. Saved workflows appear as cards; the empty library offers a central create button.

## Development

Use **Windows x64, JDK 21 and Node.js 24**. From the repository root:

```powershell
npm.cmd ci --prefix web
npm.cmd ci --prefix desktop
npm.cmd exec --prefix web -- playwright install chromium
./scripts/build.ps1
./scripts/test-web.ps1
npm.cmd run test:smoke --prefix desktop
```

The build runs Kotlin, frontend and Electron checks, exports the web interface, creates a minimal Java runtime and writes `complie/Болтунья.exe` plus `SHA256.txt`. Browser tests serve the static export on a temporary local port. Native smoke tests use an isolated data directory and do not sign in to Telegram.

For a staging build, pass `-OutputDirectory complie/.staging -SkipShortcut`. Native smoke tests accept `BOLTUNYA_TEST_EXECUTABLE` to select the staging package's unpacked executable. CI skips local shortcut creation and publishes only after validation.

For targeted checks:

```powershell
$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:/tiho-no-unix-sockets'
./gradlew.bat check --no-daemon
npm.cmd run check --prefix web
npm.cmd run check --prefix desktop
```

Optional live research tests require a running Ollama server with **gemma3:4b** already installed and internet access. They use synthetic questions about weather, news, currencies and follow-ups; they neither download models nor send Telegram messages.

```powershell
$env:TIHO_LIVE_INTERNET='true'
./gradlew.bat :backend:test --tests 'ru.boltunya.ai.Live*' --no-daemon
```

To check conversation quality on **every installed model**, use the separate 14-scenario matrix. It exercises real local inference with synthetic conversations and deterministic weather/news/rate fixtures, including topic follow-ups, owner-style samples, reasoning separation and complete final answers. It does not install missing catalog models. Reports are written to `backend/build/reports/model-quality.json` after each case.

```powershell
$env:TIHO_LIVE_AI='true'
./gradlew.bat :backend:test --tests 'ru.boltunya.ai.LiveModelQualityTest' --no-daemon
```

The current `qwen3:4b` tag requires thinking and is listed separately from its instruct variant. Reasoning stays in its own channel; incomplete replies and detected internal analysis are retried within fixed limits and rejected if they still fail. Real owner messages contribute an abstract voice profile; unrelated phrases are not inserted as response examples. The shared memory supplies vocabulary, habits and owner facts. The optional “Ignore assistant tasks” setting skips requests such as recipes before automatic generation; manual previews can still test them.

Models that fail the application's automation checks are labelled **Drafts only**. In this release, `qwen3:0.6b` is restricted after a harmful-request test failure: it can be downloaded, selected while AI is paused and used for manual previews, while automatic sending is rejected. The application keeps the model you selected. Explicit requests to recall earlier facts allow those facts to be repeated. Previously leaked analysis from known automated messages is excluded from the model's conversation window without removing messages from the interface or archive.

Recorded model results and the complete synthetic outputs are available in the [installed-model quality report](docs/ai/model-quality-2026-10-16.md). It includes failures and manual review findings; passing the automated checks does not certify a model for every conversation.

See [Docker tooling](docker/README.md) for a static frontend preview and JVM checks. The Telegram desktop session and Windows DPAPI remain native.

## Releases

Every push to `main`, or a manual **Windows release** run, checks and builds the application on GitHub Actions. It then runs browser and native Electron tests before publishing **Boltunya.exe** and **SHA256.txt** to [Releases](https://github.com/kreeeeeel/boltunya/releases). Release versions use the source major/minor and the workflow run number; each version has a separate portable extraction directory.

The repository and its releases are private. You must be signed in with repository access to download assets. To verify a download:

```powershell
Get-FileHash -Algorithm SHA256 -LiteralPath '.\Boltunya.exe'
Get-Content -LiteralPath '.\SHA256.txt'
```

## Further documentation

[Russian guide](docs/ru.md) · [Architecture](docs/architecture/en.md) · [Dependency injection](docs/dependency-injection/en.md) · [Memory bank](docs/memory/en.md) · [Web search](docs/internet/en.md) · [Storage and cache](docs/storage/en.md)

See [NOTICE.md](NOTICE.md) for third-party licenses and data sources. Noto Sans is bundled under the [SIL Open Font License](web/src/shared/assets/fonts/OFL.txt). Model licenses remain specific to each model. Boltunya is an independent application and is not affiliated with Telegram.
