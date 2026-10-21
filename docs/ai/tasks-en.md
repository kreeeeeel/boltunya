# Task models and local image analysis

In **Settings → Task routing**, each task can use an installed Ollama model or a saved API connection:

| Task | Purpose |
| --- | --- |
| Replies | Generate conversation replies using the chosen conversation style and context. |
| Memory | Summarize eligible conversations and facts. |
| Research | Plan a search for current information. The reply model still writes the final answer. |
| Image analysis | Generate the final reply with the attached image, conversation history, memory, and chosen style in one model request. |

Enable task routing, choose a model for each task, and save. Selecting an Ollama entry stores its model tag directly; no additional provider profile is created. An API selection and a local tag are mutually exclusive for the same task. Saving routing performs no generation or download.

An explicit reply model in a conversation overrides the global reply assignment. A task's own selection overrides its fallback. Without a selection, memory uses the global default; research and image analysis inherit the effective reply model. The image fallback must support vision. Disabling routing preserves the ordinary global and per-conversation model choices. Assignments survive an application restart.

Installed models outside the built-in catalog also appear. Their unverified size, context capacity, memory requirement, speed, and quality are shown as unknown. Such models are not assumed to support images. For a custom visual model, create an Ollama provider profile and explicitly enable its verified vision capability.

## Images with Ollama

Enable **Analyze photos** for the conversation and choose an installed visual model for the image-analysis task. Aigram passes image bytes as Spring AI `Media` on the user message; Spring AI uses Ollama's native `images` field. It does not send a local file URL to the model. The selected visual model produces the final answer; a second text-model request is not required.

The catalog includes Gemma 3 4B/12B/27B, the listed Qwen 3.5 variants, and Qwen3-VL 2B/4B/8B as visual models. Gemma 3 1B is text-only. The model browser has **All**, **Generation** (text-focused models), and **Image analysis** categories underneath its search field. Multimodal models remain usable for text replies as well.

The Qwen3-VL catalog tags have a 256K model context capacity and downloads of approximately 1.9 GB, 3.3 GB, and 6.2 GB respectively. The configured inference context can be smaller than that model capacity. Memory requirements in the catalog are estimates, while response-time measurements are collected only after an explicit benchmark.

This implementation uses the existing Spring AI Ollama adapter. An incompatible visual selection is rejected before a Telegram image is downloaded for inference. Remote API profiles continue to use their declared capabilities; remote analysis remains subject to the conversation's explicit photo-analysis setting.

## Concurrency

Task routing selects the model, while the shared task queue controls concurrency. Local tasks share the existing inference resource group. Assigning different local models does not promise parallel execution on one GPU or bypass Ollama's own queue.

Official references: [Spring AI Ollama multimodality](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html), [Gemma 3 variants](https://ollama.com/library/gemma3), [Qwen 3.5](https://ollama.com/library/qwen3.5), [Qwen3-VL 2B](https://ollama.com/library/qwen3-vl:2b), [4B](https://ollama.com/library/qwen3-vl:4b), and [8B](https://ollama.com/library/qwen3-vl:8b).
