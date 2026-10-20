# Attachments, cache and deleted messages

## Automatic downloads

Profile → **Settings** → **Automatic downloads** controls photos, videos/GIFs, audio/voice messages and documents separately. Photos up to 20 MB are enabled by default; the other categories are disabled. The limit applies per file. The interface offers 5, 10, 20, 50 and 100 MB; the API accepts an integer from 1 to 100.

New attachments enter a sequential background queue capped at 64 files. Current preferences are checked again before downloading. History attachments are checked when displayed. Disabled categories show a **Download** button; already cached attachments remain viewable regardless of that switch. Manual downloads have a 100 MB hard limit. Avatars, wallpapers, stickers and thumbnails use the existing display mechanism independently of these four preferences.

## Media cache

Settings show total bytes, file count and category sizes. Refresh requests current TDLib statistics. **Clear media cache** calls `optimizeStorage` and displays the actual remaining size. Files in use or protected by TDLib may remain.

These statistics cover Telegram files; Ollama models, executable files, the Telegram database, settings, memory and local message snapshots are excluded. Clearing does not sign out or delete saved message text. Visible attachments permitted by the current preferences can download again on a subsequent request; disable the relevant category to prevent this.

The clear request explicitly lists discovered file types, including avatars, stickers, wallpapers and thumbnails, because TDLib's default selection excludes some of them. See [TDLib optimizeStorage](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1optimize_storage.html).

## Retaining deleted messages

The application stores local copies of received messages and fetched history pages. A permanent deletion reported by Telegram marks a known original as **Deleted**, retaining its text, formatting, original date and attachment metadata in the conversation. The **Deleted messages** button in communication settings opens a paginated list of deleted messages.

Content never received by the application cannot be recovered. Deletion must arrive as a Telegram update; missing items in a history page are not treated as proof of deletion. Temporary deletion and TDLib cache eviction are ignored. See the flags documented in [updateDeleteMessages](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1update_delete_messages.html).

A deleted attachment remains available while Telegram still serves it or a local media copy exists. Clearing the cache may remove its last available copy; saved text and deletion markers remain. Stable file fingerprints prevent showing an unrelated attachment if a new Telegram session reuses a numeric file ID.

Files reside in `%LOCALAPPDATA%/Tiho/history/<accountId>/<chatId>/`: separate JSON message chunks plus permanent tombstones in `deleted.json`. Writes are atomic and accounts are isolated. These files contain conversation content and should not be shared. The text archive currently has no automatic size limit.

Deletion cancels a pending reply to that message. Archived deleted content is used for display and is not injected into the model's conversation history. Memory summaries created before deletion are updated separately.

## API and validation

`backend/contract/api.yaml` describes the API. Existing local-session authorization and Host/Origin checks apply:

- `GET /api/cache`: current statistics.
- `POST /api/cache/clear`: cleanup and remaining statistics.
- `POST /api/media/{fileId}/download`: explicitly download a known attachment.
- `GET /api/chats/{chatId}/deleted?before=<messageId>`: a deleted-message page. IDs use strings to preserve precision.
- `PATCH /api/settings`: `autoDownloadPhotos`, `autoDownloadVideos`, `autoDownloadAudio`, `autoDownloadFiles`, `autoDownloadMaxMb`.

Tests cover categories and limits, sequential prefetch, explicit media download, cleanup with remaining bytes, protected endpoints, text and formatting across restarts, account isolation, pagination, permanent deletion versus cache eviction, attachment fingerprint conflicts and cancellation of replies to deleted messages. Browser tests use mocked API responses and do not change real conversations or user cache files.

## Local files and error history

Open **Settings → File locations** to see the directories used by the running application. **Open in Explorer** is available for existing local folders. Electron receives a location identifier and checks the backend allowlist again; web content cannot supply an arbitrary filesystem path.

| Location | Content |
| --- | --- |
| Application data | Settings, Telegram session, styles, scenarios and local stores. The default Windows profile is `%LOCALAPPDATA%\Tiho`; a configured profile directory takes precedence. |
| Memory | `memory/MEMORY.md` contains shared notes and the automatic owner profile. Per-chat documents are under `memory/<account-id>/<chat-id>/MEMORY.md`; account state and owner summaries are stored alongside them. |
| Telegram files | The `telegram` directory contains TDLib data and downloaded media. Use the cache controls to clear downloaded media without manually deleting session files. |
| Ollama runtime | The installed application-managed or detected local Ollama executable directory. |
| Ollama models | `OLLAMA_MODELS`, when configured, or the current user's `.ollama/models` directory for a detected local installation. An external service can use a different directory. |
| Error history | `diagnostics/errors.json`, a bounded support log with up to 100 grouped errors. |

Docker, another operating-system user, and remote Ollama services can use storage that is not visible to the application. Such locations are shown as external, without an invented host path or Explorer action. This page does not move files or download models.

The **Errors** page records failed replies, model downloads and warmup, memory operations, server requests and desktop updates. Repeated errors of the same source, code and exception type share a card with a counter. A card shows the time, a static explanation, suggested checks and technical metadata. The history survives restarts and can be cleared.

The support log excludes exception messages, Telegram message contents, account and chat identifiers, prompts and credentials. Technical metadata is limited to HTTP status, exception types, application stack locations and updater stages. It does not include a raw log or a full generation trace. Desktop updater failures are imported from the fixed file `desktop/updates/last-error.json`; clearing the history also clears this imported source.

The local authenticated API is defined in [the OpenAPI contract](../../backend/contract/api.yaml):

- `GET /api/storage/locations` — known folders, availability and whether Explorer can open them.
- `GET /api/diagnostics/errors` — safe grouped errors and details.
- `DELETE /api/diagnostics/errors` — clear the support history.

These endpoints require the application's local session cookie. They do not expose a public network service. For a separate report with model inventory and an optional synthetic generation check, use the [Windows diagnostic script](../diagnostics/ru.md).
