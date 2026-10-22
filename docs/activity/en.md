# Local event history

Event cards and details resolve their avatar from the current Telegram chat with the same chat ID. Archived TDLib file URLs are never used as a photo fallback: file identifiers can be reassigned after reconnecting. Missing/deleted photos and global memory tasks show initials instead of an unrelated image.

Events and full requested model traces are stored under `<profile>/activity/<telegram-owner-id>/`. The live state contains active tasks and a small recent window; `/api/activity/history` loads terminal events in cursor pages. Details load one trace by ID. Queued or running tasks found after a restart become `interrupted`; they are never silently resent or resumed.

Defaults keep history forever with no disk quota. Optional settings allow a retention duration and a quota in MiB; zero means unlimited. An explicitly enabled quota stops recording with a visible status instead of deleting old events. Raising or removing it retries pending writes. An individual trace is limited to 32 MiB and reports a visible error if exceeded. Clearing completed history affects only the current Telegram account and preserves active work. Private prompts and responses are stored only in this local history.

Memory automatically visits only AI-enabled chats with memory enabled and allowed group settings. A single-chat manual request is a one-time explicit collection; it does not subscribe a disabled chat to future background scans. Global collection uses the AI-enabled set. New collection requests and drafts wait independently; they do not cancel another chat. If a shared model resource requires background memory to yield, its checkpoint and event ID remain and the same task returns to the queue.

## New messages during generation

Choose a global policy under **Settings → Assistant → Replies**. A chat's communication settings can inherit it or select its own policy without enabling automatic replies.

| Mode | An active reply | New eligible messages |
| --- | --- | --- |
| Restart | Cancel and replace it | Keep one task for the latest message |
| Finish, then combine | Let it finish | Combine pending messages into one next reply |
| Every message | Let it finish | Queue a separate reply to each message |

Restart remains the default for existing profiles. Replies for the same chat run sequentially even when global parallelism is enabled. The queue remains bounded. A reply's context ends at its own input anchor, so later questions do not replace the question it is answering. Group mention restrictions and excluded/ignored messages still apply.

Finishing a reply never overrides manual owner activity, account changes, changed settings, pause, deletion, context invalidation or an explicit stop. Proactive idle messages retain their separate latest-activity guard. Automated outgoing messages are identified through TDLib send identifiers, rather than text matching, so the application's own reply does not erase the remaining queue.
