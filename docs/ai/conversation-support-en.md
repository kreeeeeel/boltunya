# Opt-in conversation support

Conversation support is disabled by default. Enable it under **Settings → Replies**, then choose an idle interval in minutes or hours (5 minutes to 30 days). A conversation's communication settings can inherit the global switch, enable or disable it explicitly, and override the interval. Automatic replies must also be enabled for that conversation.

Only joined conversations in the visible Telegram chat directory are considered. Aigram waits until the last message is outgoing, there are no unread messages and nobody is typing. Saved Messages and broadcast channels are excluded. Groups additionally require group replies: mention-only groups need an explicit per-chat opt-in for initiatives.

The timer starts when an eligible conversation is observed, not from an old message's timestamp. Starting the app, enabling the feature or resuming a paused queue therefore cannot immediately catch up with long-silent conversations. The scheduler checks every 30 seconds; pauses, normal reply delays, delivery intervals and queue capacity can postpone delivery. At most one initiative is queued or running at once.

An initiative is a separate **Conversation support** event in the shared queue. It can be cancelled or reprioritized like an ordinary reply. Generation uses real conversation history, factual memory, selected style and owner instructions, plus an instruction to resume naturally. No incoming contact message is fabricated. Web research and automatic stickers are disabled for this unsolicited follow-up.

Before sending, Aigram checks that the account, conversation anchor and settings still permit it. A newly received message invalidates the pending initiative. The delivery gate is persisted before contacting Telegram. Once a follow-up is attempted, no further initiative is sent to that chat until a newer real incoming message is observed. Manual owner messages, AI messages and deleted messages do not unlock it. The gate survives restarts in `automation/initiatives.json` under the Aigram profile; it stores account/chat identifiers and checkpoints, without message text.

Generation failures or cancellations before delivery wait another configured interval rather than retrying every scheduler tick. An uncertain delivery result is never automatically retried, since Telegram may already have accepted the message. A restart begins a fresh observation interval while retaining the successful-delivery gate. This is an opt-in idle follow-up mechanism, not a scheduled broadcast or a guarantee of immediate delivery.

Local API settings: `proactiveEnabled` and `proactiveIntervalMinutes`; chat rules: `proactiveMode` (`inherit`, `disabled`, `enabled`) and `proactiveIntervalMinutes` (`0` inherits the global interval).
