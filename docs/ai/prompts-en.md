# Token budgets and model instructions

**Settings → Replies → Instructions** contains shared owner instructions and a separate editor for each model. The selected conversation style remains a final, independent system instruction. Automatic memory supplies facts and relationships; it does not set the writing style. Model-specific instructions affect replies, not the factual memory-summary format.

Saved preferences are keyed by model identifier and connection ID in `models/prompts.json`. Two API connections can use the same model name without sharing their instructions. Changing a connection's model starts with that model's own preferences. Saving a prompt does not cancel running tasks; subsequent generations use the saved settings.

## Working context

The catalog's maximum context is a model capability, not the amount of memory allocated to every request. Local models default to an 8,192-token working window. You can change it within the catalog maximum in the model prompt editor. API connections take their working context from the connection configuration, so there is one source of truth.

Increasing local context can consume substantially more memory. Ollama documents this distinction in its [context-length guide](https://docs.ollama.com/context-length). No model is downloaded or called when editing preferences or checking a prompt budget.

The budget indicator reports:

- The effective working context and catalog/configured maximum.
- Estimated system-instruction tokens, including shared instructions, model instructions and the selected style.
- Reserved answer tokens, including the model's reasoning allowance where applicable.
- Space remaining for conversation history, memory and other request content.

This preview excludes the actual conversation history, memory and images. Runtime validation checks the complete request separately. A green preview does not mean an arbitrarily large conversation will fit.

## Estimation and overflow

Counting uses the existing Spring AI [JTokkitTokenCountEstimator](https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/tokenizer/JTokkitTokenCountEstimator.html). Automatic selection uses O200K for the supported OpenAI/GPT families and CL100K otherwise; either encoding can be selected explicitly. Estimates include a 15% text margin, message overhead and a fixed image allowance. Other model tokenizers and image encoders can differ, so the provider's actual counts remain authoritative.

Before inference, the application preserves every system instruction and the latest message, then removes only older history as needed. The latest message is not silently replaced by a system-only request. If mandatory content plus reserved output cannot fit, generation stops with an explicit required-token estimate and limit. Reduce instructions or output budget, or increase the working context within the model's supported capacity.

Failures remain visible in the conversation generation block and Events, with a retry-as-draft action. There is no political-topic filter or phrase-repetition classifier. A model can still return an empty answer or apply its own provider policies; Events retains its separate thinking, final output and finish information when available.

## Local API

- `GET /api/model-prompts`: saved model preferences.
- `PUT /api/model-prompts`: save instructions, tokenizer preference and optional local context.
- `POST /api/prompts/budget`: estimate a proposed rule, shared prompt and model preferences without inference.

All endpoints use the existing private local session and Host/Origin checks. Tests cover persistence, connection/model isolation, capability validation, instruction priority, long latest messages and pre-inference overflow.
