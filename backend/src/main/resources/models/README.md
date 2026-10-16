# Local model metadata

`catalog.json` contains the maximum context capacities advertised for the exact Ollama tags, checked on 2026-10-16. These capacities differ from the application's working window of 8,192 tokens. Sizes and memory requirements are estimates; benchmark measurements come from actual local inference and are stored separately in the user profile.

Primary sources: [Qwen 3](https://ollama.com/library/qwen3), [Qwen 3.5](https://ollama.com/library/qwen3.5), [Gemma 3](https://ollama.com/library/gemma3), [DeepSeek R1](https://ollama.com/library/deepseek-r1), [GPT-OSS](https://ollama.com/library/gpt-oss), [Llama 3.2](https://ollama.com/library/llama3.2), [Llama 3.1](https://ollama.com/library/llama3.1), [Mistral](https://ollama.com/library/mistral), [Phi 4 mini](https://ollama.com/library/phi4-mini), [Qwen 2.5](https://ollama.com/library/qwen2.5).

`inference.json` configures each tag's thinking control and extra reasoning budget. The current `qwen3:4b` tag is Thinking-2507: `/api/show` reports `thinking.values: [true]`, so disabling thinking would expose its reasoning as ordinary content. This tag and DeepSeek R1 use an explicit separate thinking channel; hybrid Qwen tags may disable it. The instruct tag remains a separate model and is never silently substituted.

Spring AI handles the request protocol, typed messages, structured output and final response. Reasoning models have an additional token allowance. A response stopped by its token limit gets at most one larger-budget retry; incomplete final text is rejected. Conversation quality checks also reject untagged internal analysis and retry once. Neither separate thinking nor rejected analysis is used as a chat answer.

See [Spring AI Ollama](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html) and [Ollama thinking controls](https://docs.ollama.com/capabilities/thinking).
