# Local speech runtime

Aigram uses the official sherpa-onnx **1.13.8 Java API** for CPU speech recognition and synthesis. Its Windows x64 JNI runtime and the selected models are optional downloads. Opening the app or changing a setting never downloads them. Models require the separately installed `sherpa-runtime` asset; inference reports a missing installation instead of installing dependencies automatically.

| Asset ID | Download bytes | Purpose |
| --- | ---: | --- |
| `sherpa-runtime` | 8,277,046 | Windows x64 CPU JNI runtime |
| `whisper-tiny` | 116,204,861 | Multilingual Whisper tiny; installed inference uses int8 encoder/decoder |
| `piper-ru-irina` | 21,149,417 | Russian Irina voice |
| `piper-ru-ruslan` | 21,127,907 | Russian Ruslan voice |
| `piper-en-amy` | 21,099,246 | English Amy voice |

Sizes describe downloads, not expanded disk use. Current recognition language choices are automatic detection, Russian, English, Ukrainian and Kazakh. Small Whisper models can misrecognize speech; synthesis voices and recognition models are independent selections. No audio is sent to an external speech service.

## Installation and integrity

The versioned [catalog](../../backend/src/main/resources/speech/catalog.json) contains the exact official URLs, sizes, SHA-256 digests and required files. The runtime is an official release JAR, not the much larger CLI bundle. Downloads accept only HTTPS GitHub release hosts, enforce the declared size and verify SHA-256 before installation.

Model archives extract into a unique staging directory. Extraction rejects traversal, absolute paths, links and special files, limits archive entries and expanded size, and retains the required inference files and upstream model card. An atomic directory move publishes a completed installation. Failed or cancelled attempts do not become installed assets. There is one installer worker with a bounded queue; repeated requests for an in-flight asset are deduplicated.

Installed assets live below the configured application directory, normally `%LOCALAPPDATA%/Aigram/speech/<asset-id>`. Installation markers identify the pinned artifact. Pending transfers are not resumed automatically after app restart.

## Settings preview and imported voices

The integrated settings preview queues real synthesis with the supplied text, selected voice and rate, without saving these preferences or calling the conversation model. It returns authenticated temporary Ogg/Opus audio with playback and cancellation. A preview-only recording cannot be sent through the outgoing voice-draft API. Preview works without Telegram sign-in; importing a model requires the current signed-in account.

Model import uploads a `.tar.bz2` archive through the local authenticated API, then validates it in an isolated staging directory. Limits are 100 MiB compressed, 512 MiB expanded and 4,096 entries. The package must have one top-level directory, one root-level ONNX model, `tokens.txt` and the required eSpeak data. Traversal, duplicate paths, links, special files and executable files are rejected. Original model notices and Aigram's runtime notices are retained. Import computes a local SHA-256 fingerprint; unlike curated downloads, it has no pinned upstream identity.

Successful imports publish a `custom-…` voice and its persistent local registry atomically. The catalog includes it in the regular synthesis selector after restart. Missing files are reported as an incomplete local installation; a user-provided voice has no automatic download URL. **Refresh list** rereads installation state, and the desktop **Open in Explorer** action accepts only the fixed `speech` storage-location ID. See the [supported package layout](en.md#test-and-import-voices-in-settings).

## Worker isolation and private data

Each inference starts an owned JVM worker without Spring. Requests, including text, travel through bounded UTF-8 JSON on standard input. The command line contains no utterance or transcript. Native standard output and error are discarded; a bounded private result file carries the response and is removed after the request.

The worker uses CPU inference with two threads, a 512 MiB Java heap and a ten-minute timeout. Native inference allocates additional memory outside the Java heap. Cancelling the request terminates its owned worker. Recognition accepts a caller-owned WAV; synthesis returns a caller-owned WAV that the integration removes after Opus conversion. Temporary protocol files are cleaned after success and failure. Audio contents and native diagnostic output are not written to application error logs. Recognized text included in model prompts and signed-in voice-preview text can persist in the private local activity history under its retention and cleanup settings.

Synthesis accepts up to 4,096 UTF-16 units and a speed multiplier between 0.5 and 2.0. Input audio is limited to 25 MiB; produced audio must fit the ten-minute and 64 MiB output limits. The codec normalizes incoming Telegram voice notes to mono 16 kHz PCM WAV and converts synthesized WAV to an Ogg/Opus voice note.

## Licences and upstream sources

The catalog displays licence provenance instead of calling every model unrestricted. Downloads remain upstream artifacts; Aigram does not bundle the model weights.

- sherpa-onnx is [Apache-2.0](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/LICENSE); ONNX Runtime is MIT. The TTS build includes eSpeak NG. Its [pinned dependency source](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/cmake/espeak-ng-for-piper.cmake) points to the [GPL-3.0-or-later phonemizer](https://github.com/csukuangfj/espeak-ng/blob/ed530aa113046142eb5115cf2fc9157854d0ffe1/COPYING).
- OpenAI Whisper code and weights are published under [MIT](https://github.com/openai/whisper/blob/main/LICENSE).
- Piper's voice repository is MIT, but the individual dataset notices matter: [Irina's model card](https://huggingface.co/rhasspy/piper-voices/blob/main/ru/ru_RU/irina/medium/MODEL_CARD) leaves the dataset licence unspecified; [Ruslan's card](https://huggingface.co/rhasspy/piper-voices/blob/main/ru/ru_RU/ruslan/medium/MODEL_CARD) identifies CC-BY-NC-SA-4.0; [Amy's card](https://huggingface.co/rhasspy/piper-voices/blob/main/en/en_US/amy/low/MODEL_CARD) links to its Mimic3 dataset provenance. The voice archives also include eSpeak NG data.
- The [official Java integration example](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/java-api-examples/gradle-examples/build.gradle) separates the small JVM API dependency from platform-native JARs.

## Verification

Unit and HTTP regressions cover pinned byte bounds and checksums, archive traversal/link rejection, installation state, cancellation, offline startup, selected models, exact Unicode synthesis input, protocol cleanup, preview/send separation and imported-model persistence. The final Gradle gate completed with 619 Kotlin cases, zero failures and 14 skips: 12 unrelated opt-in AI/network/memory checks and two Windows symlink-privilege checks. All 12 installed SDK tests, 257 full UI unit tests, 39 Electron tests and 10 script tests passed. A further focused run passed 16 tests across four files, including three new hook regressions.

Live tests are opt-in with `AIGRAM_LIVE_SPEECH=true` and use previously downloaded artifacts in `build/speech-verification`. The following isolated checks have passed:

| Check | Actual processing | Duration |
| --- | --- | ---: |
| `LiveSpeechSettingsTest` | Upload a real 21 MB Piper archive through HTTP, import it, synthesize the entered sample and retrieve playable Ogg audio | 15.782 s |
| `LiveSpeechPipelineTest` | Verified installation → Russian Piper synthesis → Ogg/Opus → mono 16 kHz WAV → Whisper recognition | 36.130 s |
| `LiveSpeechVoicesTest` | Install and synthesize with all three curated voices: Irina, Ruslan and Amy | 34.490 s |

The voice run recorded 14,866 ms for installation and synthesis times of 3,886 ms for Irina, 3,673 ms for Ruslan and 5,994 ms for Amy. The earlier intermittent Ruslan install error was not reproduced by standalone/final all-voice verification; its cause remains undetermined. Test-only diagnostics capture installation failures without changing production behavior.

The pipeline preserved the spoken words; Whisper infers punctuation, so punctuation equality is not required. Test totals include setup and cleanup, not just model latency, and depend on the verification hardware. These checks do not contact Telegram, use the actual app profile or alter Ollama models. The full browser run passed 135 scenarios; all eight final voice browser scenarios passed after the collapsed-heading and preview-hook changes (`build/speech-browser-last.log`). Browser fixtures verify controls independently; the packaged check below also exercises actual local synthesis and playback.

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

The local portable EXE's extracted payload also passed in **76.760 s**, retaining its original EXE and payload hashes in `build/speech-verification/packaged-proof/run-193EDQ/proof.json`. Native window, tray and Windows startup checks passed separately. This verifies local packages. After Windows CI publishes the release, verify its source/tag, downloaded EXE/SDK hashes, the downloaded application's behavior and the actual-profile restart. Record that separate result in the release notes and local `complie/VERIFICATION.md`.
