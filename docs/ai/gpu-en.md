# Ollama compute device

Settings provide **Automatic**, **CPU**, and individual compatible **NVIDIA GPUs**. The preference applies to the Ollama server started by this application, and therefore to models using that server. It does not pin different models to different devices.

NVIDIA choices come from `nvidia-smi.exe`, including the GPU UUID, name, memory, compute capability and driver version. The standard Windows System32 location is checked before PATH. Numeric OSHI, Windows and Vulkan device positions are never treated as CUDA identifiers. Compute capability below 5 is unavailable for explicit CUDA selection. Older drivers show a recommendation, rather than blocking selection: the installed Ollama version determines actual compatibility. Current official guidance recommends driver 550+, or 570+ for compute capabilities 5.0–6.2. Discovery is cached for 30 seconds and its command has a two-second timeout. If the command cannot identify a card, Automatic and CPU remain available for a locally managed runtime.

The hardware checks follow [Ollama's official hardware requirements and GPU selection documentation](https://docs.ollama.com/gpu). A compatible identifier is not a promise that a particular model fits in VRAM; Ollama can still offload part or all of a model to CPU. The displayed active mode is the preference used when starting the process, not measured GPU utilization.

## Applying a change

Saving `ollamaDevice` does not restart Ollama, install drivers or download models. The choice is passed to the next Ollama child process started by the application. An already running app-managed server reports a pending change. Restarting the application starts it with the new preference after its managed process has shut down.

- **Automatic** preserves the parent process's environment, including existing user CUDA, HIP and Vulkan masks. It does not clear external environment settings.
- **CPU** hides CUDA, HIP/ROCm and Vulkan devices from the child process and disables the Vulkan backend.
- **NVIDIA UUID** exposes that CUDA device and hides alternative HIP/ROCm and Vulkan devices, avoiding an unintended alternative GPU backend.

These variables affect the new child process only. The Windows user's and system's environment variables are not modified. A saved UUID that temporarily disappears is retained; the application does not silently select a different GPU. A UUID that is not present cannot be chosen as a new preference.

## External services and AMD/Intel

The application cannot change GPU selection in an existing external Ollama process, a Windows service, Docker, or a remote server. The API and interface report that limitation. Those services keep running and must be configured where they are launched; see [Ollama's Windows environment configuration](https://docs.ollama.com/faq#setting-environment-variables-on-windows).

AMD and Intel devices remain under Ollama's automatic detection. Explicit AMD selection needs a verified HIP/ROCm identifier; explicit Vulkan selection needs the actual Vulkan backend index. OSHI's enumeration supplies neither. This version does not invent a mapping or install ROCm/Vulkan components. AMD's own documentation distinguishes [Windows HIP and Linux ROCr isolation variables](https://rocm.docs.amd.com/projects/HIP/en/latest/reference/env_variables.html#gpu-isolation-variables).

## Local API

[OpenAPI](../../backend/contract/api.yaml) describes `GET /api/engine/devices` and `PATCH /api/settings` with `ollamaDevice: auto | cpu | GPU-UUID`. Device discovery requires the local session. The status includes configured and known active modes, whether the process is managed, selection support, restart requirements and explicit limitations. Unsupported new choices return `INVALID_OLLAMA_DEVICE` with HTTP 400.
