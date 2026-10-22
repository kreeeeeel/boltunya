# Messages, drafts and context

## A draft shares the inference queue

The Generate button creates a manual draft job. It appears in the same queue and Events view as automatic replies, with a distinct draft kind. It progresses through queued, generating, ready, error or cancelled. A ready draft is placed in the input for the owner to review. It never calls Telegram send, marks an outgoing message as automated, or downloads a model. The shared queue runs one task at a time by default; settings permit up to four active tasks. Telegram delivery remains serialized and respects the configured interval even when generation runs concurrently. Pausing the shared queue pauses new drafts too; pausing automatic replies alone still allows an explicit manual draft. Queued jobs can be prioritized or cancelled, and active inference can be cancelled.

Draft context is read from the real chat history once the worker starts. The compatibility `text` field does not fabricate an extra incoming message. An optional selected `messageId` limits history through that real incoming message. Message selections, the Telegram account and context exclusions are validated before inference. The request and result remain in the current session's bounded Events history; feedback is a local human rating, not model training.

API: `POST /api/preview/jobs`, `GET /api/preview/jobs/{id}`. Existing activity cancellation and priority endpoints apply. The synchronous `/api/preview` endpoint remains for the communication-style preview form.

## Memory uses the same queue

Every memory batch is an Events task, including startup and scheduled collection. The scheduler only discovers eligible tracked chats and enqueues one bounded history page; it does not read every chat at startup. Queue pause, cancellation and priority apply to memory too. Cancelling collection preserves manual notes and completed checkpoints. Automatic batches yield to replies and drafts, while explicitly requested or prioritized collection may wait fairly for its model resource instead of being repeatedly preempted.

The active-task limit is not a promise of concurrent generation on one GPU. Local Ollama requests keep their model-resource gate; independently routed API targets can run alongside other resources when task routing is enabled. Events show resource waiting separately. Memory Events retain model, duration and token counts. Changing only the queue limit does not cancel existing work.

## Excluding context

The message menu can exclude one message, everything through it, or everything starting from it. Individual messages can be restored, and the complete chat policy can be cleared. Exclusion changes no Telegram message. Excluded messages stay visible with a marker, but are removed from future model context and owner-style samples, including previously cached samples. Quotes pointing at an excluded message lose their quoted text in the model context. A wholly excluded context fails explicitly rather than inventing a prompt.

An exclusion change cancels queued and active generation for that chat before delivery. Automatic memory from tracked sources is rebuilt to remove facts obtained from excluded text; manual notes are preserved. Policies are stored per Telegram account and chat in `context-exclusions/`. Resetting this policy permits future collection again; it does not restore an old automatic summary verbatim.

API: `PUT /api/chats/{chatId}/messages/{messageId}/context` with `scope: message|through|from` and `excluded`, and `DELETE /api/chats/{chatId}/context-exclusions`.

## Media and delivery

The owner can upload photographs, videos and documents, or choose trusted Telegram stickers from recent, favorite and installed sets. Uploads are temporary and bound to the current Telegram account. Raw upload requests use a URL-encoded `X-File-Name` header and binary body; filesystem paths are never accepted from the web client. The upload limit is 100 MiB, with up to 20 pending files and a 500 MiB pending budget. The owner can remove a pending upload. Temporary uploads expire after an hour. A common album caption is attached to its first item. Files are cleaned up after Telegram confirms delivery, not merely after receiving a temporary pending message ID.

The global delivery mode is reply or standalone send, with a per-chat override. It affects text, media and stickers while preserving the forum topic. Gallery retrieval around a selected message is bounded; it does not scan all history. Telegram message links come from TDLib rather than a fabricated URL. Incoming typing and upload indicators expire after a short TTL.

Photo analysis and AI sticker choice are separately disabled by default for every chat. Photo analysis requires a Vision-capable selected target and opt-in before any image is downloaded for inference. An enabled remote provider receives image bytes and the associated conversation context. Native Telegram photo files are limited to 10 MiB for inference. Text and optional stickers are produced by one structured model request; only sticker IDs from a bounded recent/favorite allowlist are accepted. The capability must support structured output. Manual text drafts do not automatically send or choose stickers.

See [the OpenAPI contract](../../backend/contract/api/schema/api.yaml) and [model connections](../ai/providers-en.md).
