# Settings center

Open the profile menu and choose **Settings**, or use a shortcut to a specific section. The left navigation groups application behavior, replies, models, memory, appearance and local data. Section changes preserve unsaved edits. Save actions remain at the bottom of the center and do not close it.

## Model connections

Add a provider preset, endpoint and model identifier. The advanced options control generation, reasoning, timeouts and supported capabilities. Blank sampling values preserve provider defaults. Native Ollama parameters are sent only to Ollama; unsupported capabilities are omitted by the model adapter.

Saving a connection never sends a generation request. **Send test request** explicitly runs a short request with the saved configuration; the provider may charge for it. A stored API key is never returned to the interface. Leave the key field blank to retain it, or explicitly remove the saved key.

Enabled connections appear alongside local models on the models page and in conversation settings. A model selected for one conversation takes priority over the reply route. Task routing can use separate connections for replies, memory, research and image analysis. Image analysis only offers connections with image capability enabled.

## Appearance

The base theme follows the system or uses a fixed light/dark mode. Eight optional color tokens customize the palette. Colors use hex notation, including an optional alpha channel. An empty field retains the base color.

Custom CSS is parsed and scoped to the application workspace. For example:

```css
[data-qa='chat-row'] {
    border-radius: 16px;
}
```

Imports, URLs, external images/fonts and global at-rules are rejected. The settings center is outside the custom CSS scope and keeps a readable palette, so reset remains available. Preview does not persist changes. Save writes `appearance.json` in the application data folder, independent of the local server port; reset restores the default draft, which can then be saved.

## Memory and local files

Shared memory stores facts, relationships and preferences. Writing style is controlled by the selected style and the separate style-matching setting. The collection section manages the refresh interval and manual collection. The file locations section opens the memory bank, application data and supported Ollama directories in Explorer.
