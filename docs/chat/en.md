# Chat controls

The composer has separate emoji and sticker buttons. Choosing an emoji replaces the selected text or inserts it at the cursor, then returns focus to the draft. It never sends the draft automatically. Sticker packs use an adaptive grid: a short pack does not leave a large empty panel, and longer packs scroll within the available window height.

The attachment menu offers photos, videos and documents with matching file filters. Media uses the existing preview, caption and upload flow before sending.

Right-click anywhere on a message row, including its empty area, to open message actions. Nested range actions appear beside the menu and move to the other side near a window edge. Opening the menu preserves the current history position and does not request an older page. Keyboard users can focus a row and press Shift+F10.

Excluding a message from AI context keeps its Telegram content visible. A compact exclusion marker appears immediately before the AI marker in message metadata. The menu can exclude or restore one message, the preceding range or the following range; restoring the entire chat is also available.

The separate Delete message action checks Telegram permissions and opens a confirmation dialog. Only the supported scopes are offered: delete for yourself or for everyone. Cancellation does not change Telegram, and permissions are checked again before deletion. A local copy remains in deleted-message history, excluded from AI context; facts collected from that chat are rebuilt while manual notes are preserved.

Permission checks use TDLib's [message properties](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1message_properties.html); the chosen scope maps directly to [deleteMessages](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1delete_messages.html). An outdated permission or failed request is shown as an error and never silently changes the scope.
