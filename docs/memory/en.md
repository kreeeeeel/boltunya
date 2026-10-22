# Conversation memory

Aigram learns a factual owner profile from real outgoing messages: confirmed personal facts, interests, everyday habits and substantive preferences. This profile is shared across conversations. Separate chat summaries retain relationships, important events and private arrangements. Automatic summaries exclude writing style, tone, emoji habits and instructions about how the model should respond. Incremental batches gradually cover available history.

The selected conversation style is a separate system instruction after the factual context. Optional punctuation and vocabulary observations come from manual outgoing messages, outside the memory bank, and remain subordinate to that style. Named styles keep their own instructions separate from additional chat instructions.

This follows the distinction between factual and procedural memory described in [LangMem's concepts](https://langchain-ai.github.io/langmem/concepts/conceptual_guide/). The existing [Spring AI chat memory](https://docs.spring.io/spring-ai/reference/api/chat-memory.html) remains responsible for the recent message window. A second memory framework or external database is not required for this local profile.

## Files

Under the application's existing `%LOCALAPPDATA%/Aigram` data directory:

```text
memory/
  MEMORY.md
  <telegramAccountId>/
    PROFILE.md                   learned account profile
    state.json
    <chatId>/MEMORY.md
    <chatId>/PARTICIPANTS.json    observed group authors
```

The global file is created at startup and combines manual notes with the active account’s learned profile. Profiles are also stored in `memory/<telegramAccountId>/PROFILE.md` and restored when switching accounts. Contact facts stay in individual chat summaries. Chat files are created for chats in use, profile sources and explicit update requests.

Generated content is enclosed by `<!-- boltunya:auto:start -->` and `<!-- boltunya:auto:end -->`. Text outside this section is maintained by the owner. Both the app and an ordinary editor can edit it. Background updates preserve manual notes, including Markdown indentation; concurrent edits invalidate a pending summary instead of being overwritten.

Factual generated sections carry a version marker. Legacy automatic summaries may contain writing instructions, so they are excluded from generation and subsequent summarization until rebuilt. Their text remains visible and manual notes are preserved. Only eligible chats already selected by the worker are scheduled for factual backfill; upgrading does not scan every account chat. Successful batches replace the old generated section and advance its factual version.

## Scheduling and processing

The worker starts 30 seconds after startup and waits for authentication. By default, automatic and global collection use only chats whose AI rule is enabled and permits memory. **Settings → Memory → Collection scope** can explicitly expand this to all allowed conversations, including chats without automatic replies. Chat-level memory opt-outs still apply, and broadcast channels are excluded. Receiving or sending a message in another chat does not enroll it. An explicit single-chat collection is a one-time request: it may finish that history even while AI is disabled, but does not subscribe to subsequent messages. Completed, unchanged chats are skipped. Only chats actually present in Telegram’s main or archive list are eligible, not arbitrary cached TDLib objects.

An initial pass reads the newest messages so useful memory becomes available quickly. A separate backfill cursor then moves toward the beginning of the chat. Each pass fetches up to 100 messages and selects a bounded input chunk. Messages outside that chunk remain behind the cursor and are included later. The worker checks for eligible work every 15 seconds; this polling frequency does not start a collection before its configured interval. Chats rotate, and checkpoints resume after restarting.

Incoming and outgoing messages mark a chat dirty. Once backfill is complete, updates process only new messages. A large incoming backlog also uses pagination: the new head is captured separately and committed only after the whole batch is processed. Messages arriving during that operation remain pending.

Automatic collection defaults to **6 hours** per conversation. Settings provide a separate memory toggle and interval selection; the API supports 1–168 hours. Both new-message updates and unfinished historical backfill respect this interval. The last automatic attempt is saved with the checkpoint, including failed attempts, so restarting the app or a generation error cannot trigger another automatic pass a few minutes later. Existing checkpoints retain their previous refresh time when upgrading. Explicit manual collection bypasses this interval and continues its batches until complete or cancelled. Fresh-message scheduling has its own timestamp, so historical chunks cannot postpone it forever.

Replies, drafts and memory use the shared queue. Adding a new job does not cancel a running collection. A local resource deferral returns the same memory event to its queued state, preserving its ID and checkpoint. Memory chunks remain sequential to safely merge the shared owner profile. Messages newer than 10 seconds are deferred so automated outgoing IDs can settle. Memory has its own enable switch.

Memory uses its configured task route, or the global default model and connection when no separate route is selected. It does not download models. Local collection waits when Ollama or the selected model is unavailable. Failed automatic updates wait for the configured interval; an explicit manual run can retry sooner. Status remains visible in settings and Events.

Only `speaker=owner` sources reach the profile model: contact messages and known AI replies are removed beforehand. Generated message IDs are no longer truncated after 5000 entries and survive logout. Older replies whose IDs were already lost cannot be reliably identified. Quotes and jokes must not become facts; the profile remains a probabilistic model summary. Manual notes take precedence. Both generated memories are saved before advancing the chat checkpoint; failures or edit conflicts leave the batch for retry. Previously completed histories are reread once after upgrading to build the profile.

An unfinished explicit request for a disabled chat does not restart automatically with the application. Start it explicitly again to continue from its saved cursor. Cancelling a queued or running collection also persists the automatic cooldown, including cancellation before generation starts; restarting does not clear that wait. A new explicit manual request can bypass it.

## UI and context

- **Settings → Memory** edits global notes and shows the tracked-chat count.
- Conversation settings open chat memory, generated summaries and collection progress.
- **Update memory** refreshes the selected chat; global collection follows the configured collection scope and chat memory permissions.
- **Rebuild summary** preserves manual notes and restarts deep processing.
- **Use chat memory** controls that chat’s prompt context and collection; the existing shared owner profile remains available.

Both memory windows use compact actions sized to their labels. Controls wrap on narrow screens and keep their size while opening Explorer.

Global notes, the owner profile, chat notes and the current summary supplement recent history for autoreplies and drafts. They are encoded as reference JSON data rather than executable instructions. Fresh messages and explicit owner settings take precedence. Known automated messages are distinguished from actual owner statements; group sources include sender IDs so equal display names do not merge people.

This is compressed context, not a verbatim archive or guaranteed recall of every detail. Summaries are limited to 6000 characters. Production source batches are sized by the effective model’s token budget, including existing summaries, instructions and the output/reasoning reserve. Complete source messages are retained; an individual message that cannot fit fails without advancing the checkpoint. A token-budgeted memory excerpt enters replies to preserve room for recent messages and photographs. Attachments contribute captions to the memory bank; image and speech recognition are not stored there. Confirmed message deletion or a context exclusion invalidates the affected chat summary and the combined learned owner profile while retaining manual notes. This does not reset other chats’ checkpoints or force a global collection before its scheduled interval. Memory files remain in your local data directory.

## API and validation

`backend/contract/api/schema/api.yaml` describes the local-session-protected API. Existing Host/Origin checks apply:

- `GET/PATCH /api/memory`: global notes.
- `POST /api/memory/refresh`: changed chats already in use.
- `GET/PATCH/POST /api/memory/{chatId}`: read, edit notes and refresh/rebuild.

PATCH requires notes and their `revision`; stale edits return 409. A rebuild uses `{"rebuild":true}`. Settings are `memoryEnabled`, `memoryIntervalHours` and `memoryCollectionScope` (`ai-enabled` by default, or `all-allowed`); chat rules use `useMemory`.

Tests cover a 700-message history, resumed checkpoints, large deltas, scheduling during backfill, skipping unused/unchanged chats, account isolation, Markdown preservation, conflicts, inference priority, HTTP protection and note editing. Optional `LiveMemoryTest` uses an already installed model and synthetic messages only; it never sends Telegram messages.

## Manual context collection

The “Collect context” button in communication settings opens a separate dialog. “New messages” preserves the summary and adds history after its checkpoint. “Entire available history” resets the chat’s generated summary and cursors while preserving manual notes. Status and processed-message count show batch progress; an unknown total is not represented as a fabricated percentage.

A manual run sends `POST /api/memory/{chatId}` with `immediate: true` and the chosen `rebuild` value. It queues that conversation without waiting for the hourly interval. It does not cancel another chat’s running collection or reply. Disabled memory must be enabled explicitly. Closing the dialog does not cancel collection.

**Settings → Memory → Collect global context** runs the same process for conversations permitted by the configured collection scope. “New messages” preserves existing memory. “Entire available history” clears generated owner facts and eligible chat summaries while preserving manual notes. A full run uses `POST /api/memory/refresh` with `{"rebuild":true,"immediate":true}`. One memory batch runs at a time in the shared queue.

Private headers show the first active Telegram username and omit the line when absent. Group headers use cached participant counts from Telegram updates without per-chat network requests. Unknown counts display “Group”. The `Chat` API exposes `username` and nullable `memberCount`.


After a manual start, communication settings replace the collection button with server-owned progress: status, queued chats, position and messages processed during the current pass. Closing the dialog preserves the job. Cancel removes pending work and invalidates unfinished inference; notes and committed summaries remain. Automatic processing resumes after the configured interval or a new manual request. Global cancellation removes all remaining manual jobs. Endpoints: POST /api/memory/{chatId}/cancel and POST /api/memory/cancel.

Dialog rows render in pages of 40 with a Gravity Loader on downward scrolling. Search and filters cover the complete fetched list; a skeleton appears during filter transitions. Outgoing previews show You; group previews use the short sender name. Username controls copy the tag and Telegram link separately.


## Group author index

Group memory also keeps a small account-specific author index in `memory/<accountId>/<chatId>/PARTICIPANTS.json`: sender ID, display name, Telegram username and source recency. It is assembled from messages actually observed or fetched for that conversation, using cached Telegram users. It does not fetch the complete member list, download extra histories or generate personality descriptions. Older backfill cannot overwrite identity metadata from newer messages.

The file retains at most 1,000 recent participants. The model receives at most 80 identity records within the memory character budget; message text is not stored in this index. Excluded source messages are omitted, and policy changes also hide previously collected records whose last source was excluded. Account and chat directories remain isolated.

## Editor refresh lifecycle

Memory display reads pause while the window is hidden or its settings section is inactive, and refresh when it becomes visible again. This stops only GET requests; it does not cancel queued collection, change the hourly schedule or enable AI. Closing an editor releases its read request and timer.

Each editor keeps one read lane. Reload, background polling and update events cannot apply older responses after a successful save, collection start or cancellation. An unchanged complete document retains its identity; the notes revision alone cannot identify changes to collection progress. Different visible editors still have independent lanes, without a shared private cache.

Background reads preserve draft notes and the revision on which they were based. Save retains text typed while its submitted version is being saved. A real revision conflict remains visible and is not retried automatically. Explicit reload reads a fresh base; switching chats does not reuse another chat's draft or accept its late response.

Chat-note edits retain the account captured during access checks and reject an owner change before writing. Shared global notes keep their existing behavior, including editing before Telegram sign-in. Notes revisions remain hashes of the notes text; they are not secrets or versions of collection progress.
