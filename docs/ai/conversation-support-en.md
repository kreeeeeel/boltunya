# Opt-in conversation support

Conversation support is disabled by default. In a chat's communication settings, explicitly enable an idle follow-up and choose an interval in minutes or hours. Choose **AI message** for a natural follow-up using the conversation context, or **Your text** to send an exact predefined message without using a model. An explicit per-chat opt-in works independently of automatic replies. The inherited global switch applies only to chats with automatic replies enabled. No chat is enabled automatically.

**Settings → Conversation support** shows every configured active chat, its message mode and idle interval, with local pagination. Open its communication settings from that list after saving any unsaved global changes. The global switch and default interval remain available there; an interval of zero in a chat inherits the global interval. Chat text is limited to 4096 UTF-16 code units and is sent literally, including Markdown-like characters.

Only joined conversations in the visible Telegram chat directory are considered. Aigram waits until there are no unread messages and nobody is typing; either participant may have written the last message. Saved Messages and broadcast channels are excluded. Groups additionally require group replies: mention-only groups need an explicit per-chat opt-in for initiatives.

The timer starts when an eligible conversation is observed, not from an old message's timestamp. Starting the app, enabling the feature or resuming a paused queue therefore cannot immediately catch up with long-silent conversations. The scheduler checks every 30 seconds; pauses, normal reply delays, delivery intervals and queue capacity can postpone delivery. At most one initiative is queued or running at once.

An initiative is a separate **Conversation support** event in the shared queue. It can be cancelled or reprioritized like an ordinary reply. AI generation uses real conversation history, factual memory, selected style and owner instructions, plus an instruction to resume naturally. Fixed-text mode bypasses inference. No incoming contact message is fabricated. Web research and automatic stickers are disabled for this unsolicited follow-up.

Before sending, Aigram checks that the account, conversation anchor and settings still permit it. A newly received message invalidates the pending initiative. The delivery gate is persisted before contacting Telegram. Once a follow-up is attempted, no further initiative is sent to that chat until a newer real incoming message is observed. Manual owner messages, AI messages and deleted messages do not unlock it. The gate survives restarts in `automation/initiatives.json` under the Aigram profile; it stores account/chat identifiers and checkpoints, without message text.

Generation failures or cancellations before delivery wait another configured interval rather than retrying every scheduler tick. An uncertain delivery result is never automatically retried, since Telegram may already have accepted the message. A restart begins a fresh observation interval while retaining the successful-delivery gate. This is an opt-in idle follow-up mechanism, not a scheduled broadcast or a guarantee of immediate delivery.

Local API settings: `proactiveEnabled` and `proactiveIntervalMinutes`; chat rules: `proactiveMode` (`inherit`, `disabled`, `enabled`), `proactiveIntervalMinutes` (`0` inherits the global interval), `proactiveMessageMode` (`ai` or `text`, default `ai`) and `proactiveText`. Existing saved rules retain their AI mode and opt-in state.

![Per-chat idle message settings](../resources/reply-controls-per-chat-light.png)
