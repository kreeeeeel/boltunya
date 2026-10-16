# Reply queue and request history

Events appear as soon as an eligible message enters the queue. Cards form a responsive grid of one to three columns and show the conversation's name and avatar, a status tag, the processing phase and live thinking when supplied by the model. The empty illustration appears only when no events remain.

![Reply queue](../resources/activity-queue.png)

## Controls

- **Pause / Resume** suspends dispatch, retains waiting tasks and continues accepting eligible messages. The current task may finish. The title bar's global AI switch instead cancels pending generation.
- **Priority** moves a waiting task ahead of other eligible tasks. It preserves reply delays, minimum send intervals and Telegram rate limits, and does not interrupt active work.
- **Stop** cancels a queued task or interrupts the model stream, preventing delivery before the send is committed. Once sending starts, Stop is disabled and the API returns HTTP 409; this action cannot recall a submitted Telegram send.
- **Clear completed events** removes finished, failed and stopped history while preserving waiting and active tasks.

A newer message in the same conversation replaces its pending reply. Manual replies, deletion of the triggering message, global pause and conversation setting changes cancel obsolete work. Stopped events remain visible until cleared or evicted.

## Details

Open a card to see the incoming message, timeline, delivered reply and every model request, including search planning and retries. Input messages, thinking, raw output and runtime options have separate rounded blocks. Requests also show message roles as supplied to Spring AI, finish reason and provider metrics. A workflow without a model node has no model requests.

Token usage comes from Ollama; completion tokens include reasoning when reported that way. Interrupted requests have no final usage, so the UI does not estimate tokens from chunks. Overall time includes context preparation, search, generation and delivery. Per-request duration, model loading, prompt processing and token speed use provider metadata. Generation progress is indeterminate.

![Request details](../resources/activity-details.png)

## Retention and privacy

Prompts may contain conversation history, instructions and memory. Full traces are accessible to the owner through the authenticated loopback API, loaded only for an opened modal and updated while its task is active. Closing the modal aborts its request. Thinking never substitutes for the final Telegram answer.

History stays in process memory until exit. It is not written to console logs or added to Telegram messages. Retention is bounded to 100 completed events and 8 million UTF-16 characters, approximately 16 MB of text; old completed events are evicted first and active work is preserved. The state endpoint carries short summaries instead of complete prompts.

See [OpenAPI](../../backend/contract/api.yaml) for `/api/activity`, `/api/activity/queue`, `/api/activity/{id}`, `/cancel` and `/priority`. Automated checks use synthetic conversations, fake Telegram delivery and SDK streams. Optional `LiveActivityTraceTest` checks installed local models without downloading any.
