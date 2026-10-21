# Voice messages

Aigram can recognize incoming Telegram voice notes and synthesize spoken replies locally. Speech processing uses sherpa-onnx, Whisper tiny and optional Piper voices; your configured conversation model still writes the answer. Speech does not require an Ollama voice model or a speech API key.

## Set up

Open **Settings → Assistant → Voice**. Download **Sherpa runtime**, **Whisper tiny** for recognition, and a synthesis voice. Each download is explicit, shows progress and can be cancelled. The application verifies the catalog's pinned SHA-256 before installing it. Downloading a voice does not enable automatic replies.

Russian Irina and Ruslan and English Amy are available. Read the license displayed on each card before downloading: voice datasets have different terms, including non-commercial or unspecified terms. The optional runtime includes eSpeak NG. [Runtime, licenses and source links](runtime-en.md).

Enable incoming voice recognition separately. Choose one reply mode:

- **Text:** generated automatic replies remain text.
- **Always voice:** AI-generated automatic replies are synthesized.
- **Random:** each generated reply independently uses your selected probability.
- **Voice to voice:** reply by voice only when the incoming message is a voice note. Music/audio attachments do not count as voice notes.

Select the recognition model, synthesis voice and speech rate. Speech settings are global; existing conversation permissions, delays, reply/send preferences and group restrictions still apply. Exact predefined idle messages remain literal text. Speech starts disabled, and missing assets produce an actionable error instead of a silent download.

## Processing

An eligible incoming voice note is downloaded through TDLib, decoded to mono 16 kHz PCM and transcribed. Its transcript replaces the voice placeholder only in the model's context. Existing author/time metadata, selected style, memory and context exclusions remain in effect. The transcript does not overwrite the Telegram message. Deleted or excluded messages are never transcribed for generation.

The conversation model returns a spoken-text answer. If voice is selected, Piper synthesizes it, then the codec produces an OGG/Opus voice note with duration and a Telegram waveform. Sending uses TDLib's native voice-note content, including the reply target and topic. Automatic sends retain their AI provenance.

Recognition and synthesis run within the same reply queue. Event cards show **Transcribing** and **Synthesizing**. Speech inference is additionally limited to one isolated worker at a time, with two CPU threads and a 512 MB JVM heap limit; native model allocations are additional. Cancelling a task interrupts its work and terminates its own speech worker. It does not stop another application's Ollama process.

This initial version processes the latest relevant incoming voice note and reuses transcripts already obtained during the session. It does not bulk-transcribe historical voice messages or feed them into the durable memory collector. Inputs are limited to 25 MiB and ten minutes; synthesis accepts up to 4,096 text characters and ten minutes of output. Long voice messages can take substantial CPU time.

## Preview before sending

In a conversation, open **Prepare an AI reply** and choose **Text reply** or **Voice reply**. A voice draft has a player and editable transcript. Editing the text invalidates the old recording immediately. Use **Update voice** to enqueue synthesis of exactly the edited text; it does not ask the conversation model to rewrite it. Sending stays disabled until the new recording is ready. Your ordinary composer draft is preserved.

Draft audio is served only through the authenticated local API and is bound to the current account and conversation. It is temporary; uncertain native uploads are retained to avoid deleting a file TDLib may still be using. No private transcript is placed in a process command line or public error log.

## Test and import voices in settings

Actual local synthesis in an isolated profile:

![Real local voice preview in Aigram settings](../resources/voice-settings-local.png)

The **Voice test** section speaks exactly the text you enter, using the currently selected voice and rate even before you save these preferences. It uses real local synthesis through the shared queue, with progress, cancellation and playback. It never calls the conversation model or sends a Telegram message. Preview-only recordings cannot be submitted as outgoing drafts. Local testing also works before Telegram sign-in.

The catalog toolbar has **Refresh list**, **Open in Explorer** and **Import model**, using the same controls as the storage settings. Refresh rereads the installed assets. In the desktop application, Explorer opens only the active profile's fixed `speech` directory once it exists; it cannot open an arbitrary supplied path.

Import accepts a compatible **sherpa-onnx VITS/Piper `.tar.bz2` package**, up to 100 MiB compressed and 512 MiB expanded. It must contain a single top-level directory with one ONNX model, `tokens.txt` and complete `espeak-ng-data` (including `phondata`, `phonindex` and `phontab`). Original license/model-card files are preserved. Sign in to Telegram before importing, then set a descriptive name, optional language and license information; imported voices appear in the normal synthesis selector and persist after restarting Aigram. The official runtime remains a separate verified download. Import validates the package structure; user-entered license information does not verify its provenance or grant usage rights.

A WAV recording, Python checkpoint or unrelated ONNX model is not a compatible voice package. A voice named “Alice” works only if it is supplied in the supported format; importing a recording does not clone a voice. Damaged or incompatible packages produce an error and do not replace installed voices. Missing imported files are reported instead of trying to download a user-supplied model from an invented URL.

## Storage and verification

Speech assets and working files live under the active Aigram profile's `speech/` directory. The default profile is `%LOCALAPPDATA%/Aigram`; `AIGRAM_DATA_DIR` can select an isolated profile. Models live in `speech/<asset-id>`, generated previews in `speech/generated`, and temporary worker files in `speech/work`. This is separate from Ollama's model directory. Recognition transcripts are held only in memory.

For isolated real verification, download the pinned test assets into `build/speech-verification` and enable `AIGRAM_LIVE_SPEECH=true`. `LiveSpeechPipelineTest` verifies installation, TTS, Opus encoding/decoding and preservation of the spoken words in Whisper's transcript; punctuation may differ. `LiveSpeechVoicesTest` exercises the catalog voices. `LiveSpeechSettingsTest` uploads a real Piper archive through the local HTTP API, imports it and synthesizes a settings sample without using a conversation model or sending anything to Telegram.

The latest settings upload/import/audio check passed in 15.782 seconds with a real 21 MB Piper package. The real TTS → Opus → Whisper cycle passed in 36.130 seconds, and installation/synthesis verification for Irina, Ruslan and Amy passed in 34.490 seconds. These are complete test timings on the verification machine, not latency guarantees. A previous intermittent Ruslan installation error did not recur in the final all-voice run; its cause was not determined, and test-only diagnostics were added.

The final backend quality gate passed with 619 Kotlin cases, zero failures and 14 skips. SDK, UI, Electron and script checks also passed. The full browser run passed 135 scenarios, and all eight final voice scenarios passed after the last changes. The actual unpacked Aigram 2.6.39 passed API synthesis, settings synthesis and audio playback in 44.259 seconds, using its bundled JVM and codec in a fresh signed-out profile. Windows publication, verification of the downloaded release and the actual-profile restart are still required. [Detailed results and optional packaged smoke](runtime-en.md#verification).
