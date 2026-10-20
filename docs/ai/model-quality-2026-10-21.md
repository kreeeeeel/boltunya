# Focused memory and tone check — 21 October 2026

This check used synthetic conversations and the already installed models through the application's streaming Spring AI path. No models were downloaded and no Telegram messages were sent. The server was an existing external Ollama 0.40.1 instance. Times are observed values on this machine, not catalog estimates.

The request now contains factual owner/chat memory and a separate final instruction block for the selected style. Named styles also remain present during the quality retry. Automatic memory no longer collects writing style; legacy generated summaries are withheld until factual rebuilding.

| Model | Case | Result | Time |
| --- | --- | --- | --- |
| DeepSeek R1 8B | Recall a meeting from history | Diagnostic deadline; no stream chunk observed | 180 s |
| DeepSeek R1 8B | Business style | Diagnostic deadline; no stream chunk observed | 180 s |
| GPT-OSS 20B | Recall a meeting from history | Correct venue and 19:30 | 65.4 s |
| GPT-OSS 20B | Business style | Formal capitalization and wording | 22.9 s |
| GPT-OSS 20B | Rough style | Sharp wording, but the required profanity was omitted | 6.3 s |

The R1 run was stopped after two identical failures to start producing a stream; its rough-style case was not evaluated. This does not establish whether the delay came from model loading, scheduling or the runtime. Its local diagnostic limit is distinct from the application: active generation no longer has a 180-second total deadline, and only ten minutes without a chunk triggers the inactivity watchdog.

The GPT-OSS result confirms factual recall and the observed business-style properties, not universal instruction compliance. Its rough-style case still fails the explicit profanity requirement. Application tests verify which instructions are actually sent and that a completed profane reply is preserved; they cannot guarantee that every model will generate one. Human feedback is stored separately from factual memory and does not automatically train the model.

Reproduce with `TIHO_LIVE_AI=true`, `TIHO_TEST_MODELS=gpt-oss:20b` and `TIHO_TEST_CASES=meeting-context,style-business,style-rough`, running `LiveModelQualityTest`. Full request traces and generated reports stay in ignored build directories.
