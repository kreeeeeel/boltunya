# Settings center

Open the profile menu and choose **Settings**. Aigram stays fixed above the icon navigation. Sections cover the application, assistant replies, model connections, memory, languages and local files. Switching sections retains drafts; saving keeps the dialog open and briefly confirms success.

## Assistant and model connections

Choose a provider, endpoint and model identifier. Advanced options cover supported generation, thinking and timeout settings. Empty generation fields keep provider defaults. Ollama-only options are sent only to native Ollama. Saving does not run inference; **Send test request** makes an explicit request using saved configuration and may incur provider charges. Saved keys are not returned to the UI; an empty key preserves it, while the separate removal control deletes it.

Enabled connections appear beside local models. A chat's explicit model takes priority over the general reply route. Routing can assign reply, memory and image-analysis models; image analysis offers only compatible connections. Automatic work stays off until explicitly enabled.

The ordinary text composer is replaced by the assistant bar. It shows the effective model and opens chat memory, manual voice composition or queued Text/Voice draft generation. Generated text can still be edited with native formatting and sent explicitly. Chat preferences have their own grouped navigation and inherit or override voice settings.

## Voice library

**Speech models** is a workspace page with Engine, Recognition and Voice tabs, Russian/English search, an installed filter and Refresh/Explorer/Import actions. Testing a voice opens a separate preview dialog. Assistant reply settings choose text, voice, random voice or voice-for-voice delivery; the voice preferences choose installed models and rate. Optional RVC conversion is considerably slower on CPU than direct Piper synthesis.

## Theme and language

Use the built-in light or dark theme; automatic mode follows the system. Language cards show a flag, the language's own name and its active radio control. The custom palette/CSS editor has been removed. Scrollbars appear on hover or keyboard focus without changing content width.

## Memory and local files

Shared memory stores facts, relationships and substantive preferences; the selected conversation style is a separate instruction. Automatic collection observes the configured interval, while explicit deep collection can continue through successive token-budgeted history pages. Excluded/deleted records and known AI replies are not owner learning examples. File-location actions open application data, memory, speech and supported Ollama directories in Explorer. Resource charts and their hardware polling have been removed; queue parallelism shows a qualitative load estimate.
