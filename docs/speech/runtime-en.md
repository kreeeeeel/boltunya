# Local speech runtime

Aigram supports two optional local CPU engines on Windows x64. The official **sherpa-onnx 1.13.8 Java API** runs Whisper recognition and Piper/VITS text-to-speech. **Applio 3.7.0** converts a synthesized recording into the timbre of a selected RVC character model. RVC is voice conversion, not standalone text-to-speech: its path is **Piper → WAV → Applio/RVC → WAV → Ogg/Opus**. Conversion failure reports an error; it never silently substitutes the base Piper voice.

The new-download catalog contains **32 Russian/English synthesis voices: 17 Sherpa/Piper and 15 RVC models**, plus two runtimes and Whisper tiny. Previously installed voices in other languages and imported models retain their IDs and remain selectable. Uninstalled foreign-language entries are not offered as new downloads. The following table lists representative dependencies and voices, not all 32; see the [complete catalog](catalog-en.md) for exact sources and individual terms.

| Asset ID | Download bytes | Purpose |
| --- | ---: | --- |
| `sherpa-runtime` | 8,277,046 | Windows x64 CPU JNI runtime |
| `applio-runtime` | 6,775,972,339 | Applio 3.7.0 portable Python/PyTorch runtime; 6.31 GiB compressed |
| `whisper-tiny` | 116,204,861 | Multilingual Whisper tiny; installed inference uses int8 encoder/decoder |
| `piper-ru-irina` | 21,149,417 | Russian Irina voice |
| `piper-ru-ruslan` | 21,127,907 | Russian Ruslan voice |
| `piper-en-amy` | 21,099,246 | English Amy voice |
| `rvc-alice` | 64,237,755 | Community YandexAliceRVC checkpoint and FAISS index; requires Applio and Russian Irina |

Sizes describe downloads, not expanded disk use. Starting the app, changing a selection and requesting inference never install missing assets. An explicit **Install** action queues missing dependencies before the selected asset, with their sizes visible before downloading. Current recognition language choices remain automatic detection, Russian, English, Ukrainian and Kazakh; the RU/EN restriction applies to new synthesis voices. Small Whisper models can misrecognize speech. No audio is sent to an external speech service.

## Installation and integrity

The versioned [catalog](../../backend/src/main/resources/speech/catalog.json) records exact URLs, byte counts, SHA-256 digests and required files. Sherpa uses the small official native JAR. Applio uses its pinned upstream portable ZIP, and RVC checkpoints/indexes come from pinned community repository revisions. Downloads permit HTTPS only on an explicit GitHub/Hugging Face host allowlist, including approved redirect hosts. Byte count and SHA-256 must match before extraction.

Archives extract into a unique staging directory; an atomic directory move publishes the completed installation. Failed or cancelled attempts do not become installed assets. Limits are:

| Archive | Maximum entries | Maximum expanded bytes |
| --- | ---: | ---: |
| Curated Sherpa/Piper `.tar.bz2` | 4,096 | 512 MiB |
| Curated RVC model ZIP | 4,096 | 512 MiB |
| Curated Applio runtime ZIP64 | 50,000 | 12 GiB |

Extraction rejects traversal, absolute paths, links and duplicate/unsafe Windows ZIP names. ZIP entries also have declared-size and CRC checks. Required inference files and upstream notices are retained. Applio's inspected archive contains 37,231 entries totaling 10,386,551,490 expanded bytes; before its download the installer requires free space for the archive plus the 12 GiB extraction allowance. The selected runtime includes its interpreter, RVC sources/support models and dependency notices, not an Aigram-managed pip installation.

One installer worker processes a bounded queue and deduplicates in-flight assets. Installing an RVC voice queues Sherpa, Applio and its base Piper voice before its own weights. A failed dependency makes the parent fail rather than remain queued. Cancelling a voice cancels its dependency jobs only when another requested installation does not need them. Cancelling an active runtime cancels the dependent installation plans as well, including when that runtime was separately requested. An already installed imported voice remains a no-download operation; its missing Sherpa runtime must be installed explicitly.

Installed assets live below the configured application directory, normally `%LOCALAPPDATA%/Aigram/speech/<asset-id>`. Installation markers identify the pinned artifact. Pending transfers are not resumed automatically after app restart.

## Settings preview and imported voices

The integrated settings preview queues real synthesis with the supplied text, selected voice and rate, without saving these preferences or calling the conversation model. It returns authenticated temporary Ogg/Opus audio with playback and cancellation. A preview-only recording cannot be sent through the outgoing voice-draft API. Preview works without Telegram sign-in; importing a model requires the current signed-in account.

Model import uploads a `.tar.bz2` archive through the local authenticated API, then validates it in an isolated staging directory. Limits are 100 MiB compressed, 512 MiB expanded and 4,096 entries. The package must have one top-level directory, one root-level ONNX model, `tokens.txt` and the required eSpeak data. Traversal, duplicate paths, links, special files and executable files are rejected. Original model notices and Aigram's runtime notices are retained. Import computes a local SHA-256 fingerprint; unlike curated downloads, it has no pinned upstream identity.

Successful imports publish a `custom-…` voice and its persistent local registry atomically. The catalog includes it in the regular synthesis selector after restart. This import path supports Sherpa-compatible ONNX packages, not arbitrary RVC/PTH/ZIP models or executable runtimes. Missing files are reported as an incomplete local installation; a user-provided voice has no automatic download URL. **Refresh list** rereads installation state, and the desktop **Open in Explorer** action accepts only the fixed `speech` storage-location ID. See the [supported package layout](en.md#test-and-import-voices-in-settings).

The manual **Voice message** dialog uses a separate chat-bound draft path. It accepts an installed voice and speed for this request without updating global preferences. **Test** is optional; **Speak and send** queues synthesis and sends the resulting voice note, or reuses an unchanged ready preview. Closing or cancelling clears the send intention before dispatch. An uncertain send cannot be retried with the same recording, and a failed refresh after confirmed delivery retries only the refresh. See [manual voice and AI drafts](en.md#preview-before-sending).

## Worker isolation and private data

Each Sherpa stage starts an owned JVM worker without Spring. Text travels through bounded UTF-8 JSON on standard input; a bounded private result file carries the response and is removed after the request. RVC starts only the installed `env/python.exe -s core.py infer` with local WAV/checkpoint/index paths. Neither command line contains the utterance or transcript. Native standard output and error are discarded in production.

Native stages share a serialized inference slot and a ten-minute process timeout. Sherpa uses two CPU threads and a 512 MiB Java heap; native memory is additional. Applio's CPU thread environment is set to two and GPU visibility is disabled. The JVM heap limit does not constrain the Python/PyTorch process; its memory use depends on the loaded models. Cancellation stops the owned worker and its descendants, not external Ollama or unrelated processes. RVC deletes its intermediate Piper WAV after conversion; the integration removes the final WAV after Opus conversion.

Applio never runs its installer, updater, web server or cloud TTS. The adapter checks real paths for the interpreter, CLI, checkpoint, index, ContentVec files and RMVPE predictor before launch. Python home/search paths point to the installed runtime, user site packages are disabled and Hugging Face/Transformers use offline mode. `APPLIO_CONFIG_FILE`, `APPLIO_CONFIG_DIR`, `APPLIO_ROOT`, `APPLIO_CODE_ROOT`, `APPLIO_LOGS_DIR`, `HF_HOME`, `TORCH_HOME`, `TEMP` and `TMP` are explicitly scoped to the runtime or request work directory, overriding inherited Applio profile locations.

Recognition accepts a caller-owned WAV, and synthesis returns a caller-owned WAV. Temporary request/configuration/result files are cleaned after success and failure. Audio contents and native diagnostics are not written to application error logs. Recognized text included in model prompts and signed-in voice-preview text can persist in private local activity history under its retention and cleanup settings.

Synthesis accepts up to 4,096 UTF-16 units and speed 0.5–2.0; RVC applies that speed to the base Piper synthesis. Input audio is limited to 25 MiB. Output must fit 600 seconds and 64 MiB; a converted WAV must be signed 16-bit mono PCM at 8–48 kHz. The codec normalizes incoming Telegram voice notes to mono 16 kHz PCM WAV and converts synthesized WAV to Ogg/Opus.

## Licences and upstream sources

The catalog displays licence provenance instead of calling every model unrestricted. Downloads remain upstream artifacts; Aigram does not bundle the model weights.

- sherpa-onnx is [Apache-2.0](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/LICENSE); ONNX Runtime is MIT. The TTS build includes eSpeak NG. Its [pinned dependency source](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/cmake/espeak-ng-for-piper.cmake) points to the [GPL-3.0-or-later phonemizer](https://github.com/csukuangfj/espeak-ng/blob/ed530aa113046142eb5115cf2fc9157854d0ffe1/COPYING).
- OpenAI Whisper code and weights are published under [MIT](https://github.com/openai/whisper/blob/main/LICENSE).
- Piper's voice repository is MIT, but the individual dataset notices matter: [Irina's model card](https://huggingface.co/rhasspy/piper-voices/blob/main/ru/ru_RU/irina/medium/MODEL_CARD) leaves the dataset licence unspecified; [Ruslan's card](https://huggingface.co/rhasspy/piper-voices/blob/main/ru/ru_RU/ruslan/medium/MODEL_CARD) identifies CC-BY-NC-SA-4.0; [Amy's card](https://huggingface.co/rhasspy/piper-voices/blob/main/en/en_US/amy/low/MODEL_CARD) links to its Mimic3 dataset provenance. The voice archives also include eSpeak NG data.
- The [official Java integration example](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/java-api-examples/gradle-examples/build.gradle) separates the small JVM API dependency from platform-native JARs.
- [Applio 3.7.0](https://github.com/IAHispano/Applio/tree/3.7.0) application code is MIT. Its optional portable archive contains dependencies with their own licences; their notices and upstream terms are preserved. Aigram invokes the [official CLI](https://github.com/IAHispano/Applio/blob/3.7.0/core.py), not a locally maintained Python ecosystem.
- Community RVC cards identify their actual uploader/model names. The Alice and Joxi repositories declare a generic OpenRAIL tag without an exact licence text or dataset permission. This does not establish unrestricted commercial, redistribution or voice/character rights, or affiliation with Yandex, streamers or DonationAlerts. Complete source cards and available notices are in [licenses](licenses/README.md).

## Verification

### Real RVC verification

The recorded local synthetic checks passed: `ApplioSpeechWorkerTest` (3 cases), `SpeechZipInstallerTest` (4), `SpeechDependencyInstallTest` (6) and `SpeechConversionTest` (2). These 15 cases check CPU/offline/private-argument construction, explicit environment isolation, archive limits/traversal/links/Windows aliases, dependency ordering/failure/cancellation, preservation of installed legacy voices and conversion cleanup without base-voice substitution. They use synthetic files and executor stubs and do not establish real RVC audio quality. This is targeted evidence, not a claim that the current complete release gate has passed.

Real Alice verification passed with the pinned Applio runtime and community model: Piper → RVC → Opus → Whisper retained the spoken meaning, and the converted waveform differed from the base voice. Isolated installation took 420.305 s; synthesis of a 3.260 s recording took 82.607 s on two CPU threads; the whole check took 508.497 s. These are measured local timings, not latency guarantees. No Telegram messages or conversation-model requests were sent. Evidence: `build/speech-verification/rvc-proof-1434426430849561619/proof.json`. The other 14 RVC voices have verified metadata, not real audio verification.

The passed opt-in gate uses only those cached artifacts plus cached Sherpa, Irina and Whisper tiny archives:

```powershell
$env:AIGRAM_LIVE_RVC = 'true'
.\gradlew.bat :backend:test --tests 'ru.aigram.speech.LiveRvcSpeechTest' --rerun-tasks
```

`LiveRvcSpeechTest` installs into a fresh `build/speech-verification/rvc-proof-*/profile`, runs real Piper → Applio → Opus → 16 kHz WAV → Whisper, and requires both a changed WAV and recognizable synthetic words. It does not use Telegram, the real app profile or conversation-model inference. Synthetic audio and timing/boolean evidence remain in that proof directory; transcripts are not added to the report. A passing result must be recorded before claiming verified RVC operation. The packaged smoke below continues to verify the Sherpa path, not Applio.

### Historical Sherpa baseline

The original speech release's unit and HTTP regressions cover pinned byte bounds and checksums, archive traversal/link rejection, installation state, cancellation, offline startup, selected models, exact Unicode synthesis input, protocol cleanup, preview/send separation and imported-model persistence. Its final Gradle gate completed with 619 Kotlin cases, zero failures and 14 skips: 12 unrelated opt-in AI/network/memory checks and two Windows symlink-privilege checks. All 12 installed SDK tests, 257 full UI unit tests, 39 Electron tests and 10 script tests passed. A further focused run passed 16 tests across four files, including three new hook regressions. These are historical baseline results. New expanded-library local results are recorded in the main speech guide; publication is verified separately.

Live tests are opt-in with `AIGRAM_LIVE_SPEECH=true` and use previously downloaded artifacts in `build/speech-verification`. The following isolated checks have passed:

| Check | Actual processing | Duration |
| --- | --- | ---: |
| `LiveSpeechSettingsTest` | Upload a real 21 MB Piper archive through HTTP, import it, synthesize the entered sample and retrieve playable Ogg audio | 15.782 s |
| `LiveSpeechPipelineTest` | Verified installation → Russian Piper synthesis → Ogg/Opus → mono 16 kHz WAV → Whisper recognition | 36.130 s |
| `LiveSpeechVoicesTest` | Install and synthesize with the original three curated voices: Irina, Ruslan and Amy | 34.490 s |

The original three-voice run recorded 14,866 ms for installation and synthesis times of 3,886 ms for Irina, 3,673 ms for Ruslan and 5,994 ms for Amy. The earlier intermittent Ruslan install error was not reproduced by standalone/final three-voice verification; its cause remains undetermined. Test-only diagnostics capture installation failures without changing production behavior.

The pipeline preserved the spoken words; Whisper infers punctuation, so punctuation equality is not required. Test totals include setup and cleanup, not just model latency, and depend on the verification hardware. These checks do not contact Telegram, use the actual app profile or alter Ollama models. The original release's full browser run passed 135 scenarios; all eight voice browser scenarios passed after its collapsed-heading and preview-hook changes (`build/speech-browser-last.log`). These totals do not cover the new catalog or manual dialog. Browser fixtures verify controls independently; the packaged check below also exercises actual local synthesis and playback.

## Optional packaged speech smoke

This opt-in Windows check uses the actual bundled JVM, speech worker and codec. It is separate from the default test suite and never downloads weights. Install the workspace npm dependencies and place the catalog-pinned `sherpa-onnx-native-lib-win-x64-1.13.8.jar` and `vits-piper-ru_RU-irina-medium-int8.tar.bz2` in `build/speech-verification` first. From the repository root, select an unpacked build or a downloaded portable EXE:

```powershell
$env:AIGRAM_TEST_EXECUTABLE = (Resolve-Path '.\complie\win-unpacked\Aigram.exe').Path
npm --prefix desktop run test:speech

# To check a downloaded release instead:
$env:AIGRAM_TEST_EXECUTABLE = 'C:\Downloads\Aigram.exe'
npm --prefix desktop run test:speech
```

`BOLTUNYA_TEST_EXECUTABLE` remains a supported alias; without either override the script uses `complie/win-unpacked/Aigram.exe`. It verifies source artifact sizes and SHA-256, prepares a fresh `build/speech-verification/packaged-profile-*` and launches the app signed out with updating disabled. It exercises the real preview API and settings synthesis/playback, asserts no conversation-model inference through a local Ollama request trap, closes its owned app and verifies removal of `launch.url`.

For a portable EXE, the script uses the installed Windows 7-Zip helper to extract only its fixed NSIS `$PLUGINSDIR/app-64.7z` payload, then unpacks that application into a fresh `build/speech-verification/packaged-extract-*`. It validates `Aigram.exe` and `resources/app.asar` and launches the extracted application; it does not attach a debugger to the NSIS wrapper. An already unpacked EXE with adjacent `resources/app.asar` runs directly. The report retains both the original EXE SHA-256 and the launched payload EXE SHA-256, with `portablePayloadExtracted` indicating the selected path. Extraction directories are retained with the other synthetic evidence.

Synthetic audio, the settings screenshot and timing/boolean evidence are retained under `build/speech-verification/packaged-proof/run-*`; the test profile is retained too. Existing profiles, Telegram credentials and Ollama models are untouched.

The actual unpacked Aigram **2.6.39** passed this check in **44.259 s**. The API synthesis flow took 10.850 s; the settings synthesis/playback flow took 10.011 s. Both produced real Ogg audio, and the settings player loaded its duration and advanced playback. The model trap recorded zero inference and zero unexpected requests. Telegram remained signed out with no chats, unsaved settings stayed unchanged, and `launch.url` was removed after shutdown. Local evidence is in `build/speech-verification/packaged-proof/run-KslmQb/proof.json`.

The local portable EXE's extracted payload also passed in **76.760 s**, retaining its original EXE and payload hashes in `build/speech-verification/packaged-proof/run-193EDQ/proof.json`. Native window, tray and Windows startup checks passed separately. These earlier runs verified local packages.

### Published v2.6.41 verification

The published [v2.6.41 release](https://github.com/kreeeeeel/boltunya/releases/tag/v2.6.41) passed [Windows CI](https://github.com/kreeeeeel/boltunya/actions/runs/39668438423). Its release tag and source commit both resolve to `159228cc30f4fca063e9ddad0cd59b4a244a5b58`; the downloaded EXE and SDK hashes were checked against the published digests. The downloaded portable EXE is 267,083,244 bytes, SHA-256 `a3ecb4870f6d981f8e8df967d0791293809b3a77c8caa14fdb0b69f5625e679f`.

That exact downloaded application's extracted payload passed real speech verification in **34.822 s**: API synthesis took 4.743 s and settings synthesis/playback 4.417 s. The real audio played in a fresh signed-out profile with zero conversation-model inference or unexpected model requests. Unsaved voice/rate preferences remained unchanged and shutdown removed the session descriptor. Evidence: `build/speech-verification/packaged-proof/run-V0O1bt/proof.json`.

The published portable EXE was then launched normally with the existing profile, upgrading **2.6.38 → 2.6.41** while preserving Telegram authorization, settings and manual memory notes. The safe-status report is `build/aigram-2.6.41-published-restart.json`; the combined release evidence is recorded in local `complie/VERIFICATION.md`. This verifies the published baseline, not the expanded catalog or new manual dialog, whose newer local results are recorded in the main speech guide.
