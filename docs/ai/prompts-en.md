# Token budgets and model instructions

**Settings → Replies → Instructions** contains shared owner instructions and a separate editor for each model. The selected conversation style remains a final, independent system instruction. Automatic memory supplies facts and relationships; it does not set the writing style. Model-specific instructions affect replies, not the factual memory-summary format.

Saved preferences are keyed by model identifier and connection ID in `models/prompts.json`. Two API connections can use the same model name without sharing their instructions. Changing a connection's model starts with that model's own preferences. Saving a prompt does not cancel running tasks; subsequent generations use the saved settings.

## Working context

The catalog's maximum context is a model capability, not the memory allocated to every request. Without an explicit override, known local models start at 8,192 tokens and grow as needed, up to the smaller of 32,768 tokens and their catalog capacity. Unknown local models retain their conservative configured window. You can set an explicit window in the model prompt editor; it is respected without automatic expansion. API connections take their working context from the connection configuration.

Increasing local context can consume substantially more memory. Ollama documents this distinction in its [context-length guide](https://docs.ollama.com/context-length). No model is downloaded or called when editing preferences or checking a prompt budget.

The budget indicator reports:

- The effective working context and catalog/configured maximum.
- Estimated system-instruction tokens, including shared instructions, model instructions and the selected style.
- Reserved answer tokens, including the model's reasoning allowance where applicable.
- Space remaining for conversation history, memory and other request content.

This preview excludes the actual conversation history, memory and images. Runtime validation checks the complete request separately. A green preview does not mean an arbitrarily large conversation will fit.

## Estimation and overflow

Counting uses the existing Spring AI [JTokkitTokenCountEstimator](https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/tokenizer/JTokkitTokenCountEstimator.html). Automatic selection uses O200K for the supported OpenAI/GPT families and CL100K otherwise; either encoding can be selected explicitly. Estimates include a 15% text margin, message overhead and a fixed image allowance. Other model tokenizers and image encoders can differ, so the provider's actual counts remain authoritative.

Before inference, optional memory is fitted to the available token budget. Every system instruction, the latest message and all selected album photographs are preserved; older text history may be removed. Automatic native windows grow for the complete request, including the answer and reasoning reserve. Explicit limits remain hard limits. If mandatory content cannot fit, generation stops with a required-token estimate and limit instead of silently discarding the question or part of an album.

Memory collection also sizes each source batch against its effective model, existing summary, instructions and output reserve, including a possible reasoning retry. It does not use a fixed number of messages. A source message is never silently shortened before advancing the checkpoint: an individual message that cannot fit stops collection with an actionable error and retains the checkpoint for retry.

Failures remain visible in the conversation generation block and Events, with a retry-as-draft action. There is no political-topic filter or phrase-repetition classifier. A model can still return an empty answer or apply its own provider policies; Events retains its separate thinking, final output and finish information when available.

## Local API

- `GET /api/model-prompts`: saved model preferences.
- `PUT /api/model-prompts`: save instructions, tokenizer preference and optional local context.
- `POST /api/prompts/budget`: estimate a proposed rule, shared prompt and model preferences without inference.

All endpoints use the existing private local session and Host/Origin checks. Tests cover persistence, connection/model isolation, capability validation, instruction priority, long latest messages and pre-inference overflow.
