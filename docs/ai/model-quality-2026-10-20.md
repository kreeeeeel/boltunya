# Installed-model validation · 20 October 2026

These checks used the application's Spring AI/Ollama streaming path, synthetic conversations and the two installed catalog models. They did not download models or send Telegram messages. Other catalog entries were not tested on this machine.

## Response parsing

Both `deepseek-r1:8b` and `gpt-oss:20b` returned complete, nonempty final answers with `finishReason=stop`. Reasoning was captured separately in the request trace and did not appear in the final answer. Token counts and request timings were retained.

The installed R1 tag continued emitting reasoning when asked for `think:false`. Its production policy therefore keeps reasoning enabled with a separate budget. Thinking capabilities depend on the actual model build; an unsupported flag cannot be assumed to disable them.

## Conversation tone

The six-style matrix returned final text for all twelve model/style combinations. Automated tone checks passed 2/6 cases for R1 and 5/6 for GPT-OSS. These are narrow checks of one synthetic situation, not model rankings: for example, a plausible sarcastic answer can miss a keyword-based aggressive-style check.

A later targeted run strengthened the rough-style instructions and added unrelated tone examples. Both models still omitted the profanity required by that test:

| Model | Result | Complete final answer | Request duration |
| --- | --- | --- | --- |
| DeepSeek R1 8B | Rough-style check failed | Yes | 22.2 s |
| GPT-OSS 20B | Rough-style check failed | Yes | 84.7 s |

The trace confirmed that the selected rough instructions reached each model. A separate deterministic HTTP integration test verifies that the application preserves a profane final response unchanged. There is no application filter replacing it with a canned refusal. Stronger wording in a prompt still does not guarantee model compliance, and exact tone remains a known limitation.

Durations include loading and inference and depend on available CPU/GPU memory and other work on the machine. They should not be treated as expected warm response times.

## Reproduce

With those models already installed and Ollama running:

```powershell
$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:/tiho-no-unix-sockets'
$env:TIHO_LIVE_AI='true'
$env:TIHO_TEST_MODELS='deepseek-r1:8b,gpt-oss:20b'
$env:TIHO_TEST_CASES='style-rough'
./gradlew.bat :backend:test --tests 'ru.aigram.ai.LiveModelQualityTest' --no-daemon
```

This optional quality test currently fails for the limitation above. Ordinary offline tests skip live inference. Reports are written under `backend/build/reports/`; `TIHO_TRACE_REQUESTS=true` additionally writes the synthetic request trace for local inspection.
