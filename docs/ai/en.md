# AI services

Boltunya uses [Spring AI 2.0](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html) as the local inference integration. Google Guice constructs the services; Spring Boot and a Spring application context are not started.

| Component | Responsibility |
| --- | --- |
| `ConversationService` | Coordinate research, owner style, long-term facts and a bounded reply-quality retry. |
| `PromptBuilder` | Build typed system/user/assistant messages; use Spring AI `MessageWindowChatMemory` for the recent conversation window. |
| `ChatCompletionService` | Call Spring AI `ChatModel`, apply inference options, request structured JSON and save actual measurements. |
| `ModelReplyReader` | Read the final response and durations from Spring AI. Use its `ThinkingTagCleaner` for legacy inline reasoning; never use separate thinking as an answer. |
| `OllamaRuntime` | List, preload, download, cancel and unload local models. It does not generate conversation replies. |
| `MemoryCoordinator` and `MemoryProcessor` | Incrementally summarize changed history into the existing Markdown memory bank. |

The conversation window is rebuilt from actual Telegram history for each request, so failed previews, failed deliveries and cancelled generations do not enter the next conversation. The library's short-term memory does not replace the archive or editable `MEMORY.md` files. Known automated replies remain excluded from the owner's writing samples and shared owner facts.

Model options live in `backend/src/main/resources/models/inference.json`, independent of prompts and language packs. A reasoning model may spend its entire short output limit on thinking. Such models have a separate reasoning allowance; an empty response truncated by its limit gets at most one larger allowance. Empty final responses remain explicit errors. No model is downloaded or changed automatically by this integration.

The model catalog's maximum context capacities and the application's 8,192-token working window are distinct. Local measurements use Ollama's actual total/evaluation durations and token counts; the application does not invent speeds for untested models. Measurements are stored in `model-measurements.json` inside the local profile.

Changing the global model affects conversations using `useDefaultModel`. A per-chat choice explicitly disables inheritance; choosing “Default” restores it. Existing rules without this flag follow the global model, removing the previous stale-model behavior.

Tests cover the Guice graph, typed roles and history limits, structured search planning, response extraction, thinking controls, bounded reasoning retries, measurement persistence and download cancellation. Optional live tests use synthetic messages against installed models and public search sources; they send nothing to Telegram.
