# AI services

Boltunya uses [Spring AI 2.0](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html) as the local inference integration. Google Guice constructs the services; Spring Boot and a Spring application context are not started.

| Component | Responsibility |
| --- | --- |
| `ConversationService` | Coordinate research, owner style, long-term facts and a bounded reply-quality retry. |
| `PromptBuilder` | Build typed system/user/assistant messages; use Spring AI `MessageWindowChatMemory` for the recent conversation window. |
| `ChatCompletionService` | Call Spring AI `ChatModel`, apply inference options, request structured JSON and save actual measurements. |
| `ModelReplyReader` | Read the final response and durations from Spring AI. Use its `ThinkingTagCleaner` for legacy inline reasoning; never use separate thinking as an answer. |
| `ResearchFactGuard` | Compare required currency values with the final text, accepting equivalent decimal formatting and rejecting rounded or missing values. |
| `OllamaRuntime` | List, preload, download, cancel and unload local models. It does not generate conversation replies. |
| `MemoryCoordinator` and `MemoryProcessor` | Incrementally summarize changed history into the existing Markdown memory bank. |

The conversation window is rebuilt from actual Telegram history for each request, so failed previews, failed deliveries and cancelled generations do not enter the next conversation. The library's short-term memory does not replace the archive or editable `MEMORY.md` files. Known automated replies remain excluded from the owner's writing samples and shared owner facts.

Currency quotes carry explicit required values from the provider. A mismatch triggers one quality retry with those values; repeated inaccuracies become an explicit error before sending. URL and date fragments cannot satisfy a required quote. Explicit recall requests may reuse earlier facts.

`PersonalStyleBuilder` reduces genuine owner messages to an abstract voice profile. Literal examples from unrelated topics are not added to generation prompts: live testing showed that small models could copy their meaning instead of answering the question. Shared owner memory supplies learned vocabulary, habits and facts. Known automated analysis leaks are excluded from the model's recent window; valid automated replies and manual messages remain available, and the UI/archive are preserved.

`ModelInfo.supportsAutomation` distinguishes manual drafts from automatic sending. A known unsuitable model (`qwen3:0.6b` in this release) remains available for previews. Settings, conversation rules and `PersonalReplyGenerator` enforce the restriction before inference, including legacy rules and workflow agent calls.

Explicit human-identity claims on direct questions about automation trigger a quality retry. Source-only follow-ups may omit previously quoted rates; if a rate is included, it must remain exact. A combined request for the rate and source still requires the quote. Live provider tests use the same local clock as the production Guice configuration, including across midnight.

Owned engine shutdown snapshots child process handles before stopping the server, then forces remaining captured children to stop even if their parent has exited. A regression test covers the orphan-process race found during live checks.

Model options live in `backend/src/main/resources/models/inference.json`, independent of prompts and language packs. Thinking-only tags such as the current `qwen3:4b` use an explicit thinking channel and an extra token allowance; turning thinking off is not supported by that model. Hybrid tags can disable it. A response stopped by its token limit gets at most one larger-budget retry. Empty or still-incomplete final responses remain explicit errors, and untagged internal analysis is retried and rejected before delivery. No model is downloaded or substituted automatically.

The model catalog's maximum context capacities and the application's 8,192-token working window are distinct. Local measurements use Ollama's actual total/evaluation durations and token counts; the application does not invent speeds for untested models. Measurements are stored in `model-measurements.json` inside the local profile.

Changing the global model affects conversations using `useDefaultModel`. A per-chat choice explicitly disables inheritance; choosing “Default” restores it. Existing rules without this flag follow the global model, removing the previous stale-model behavior.

Tests cover the Guice graph, typed roles and history limits, structured search planning, response extraction, thinking controls, bounded reasoning retries, measurement persistence and download cancellation. Optional live tests use synthetic messages against installed models and public search sources; they send nothing to Telegram.

`LiveModelQualityTest` runs 20 scenarios on every installed model: 14 conversation, context and factual cases, plus the six built-in writing styles. External facts in this matrix are deterministic fixtures; inference uses the actual local models. Style checks use the same incoming message and a conflicting learned lowercase voice; the business style must retain formal writing. These checks cover observable properties and still require manual review of tone. Results are checkpointed to `backend/build/reports/model-quality.json`, including failures instead of stopping at the first model. Each case has a three-minute deadline, and models are unloaded between groups. Independent live-internet tests exercise real providers. Set `TIHO_LIVE_AI=true` to run the model matrix; `TIHO_TEST_MODELS` and `TIHO_TEST_CASES` restrict diagnostic reruns to comma-separated installed tags and case IDs.
