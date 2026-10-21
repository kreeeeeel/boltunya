# Speech and audio notices

This directory accompanies Aigram's speech features. The files are complete upstream licence texts, notices and model cards, not rewritten summaries. [provenance.json](provenance.json) records their source URLs, SHA-256 digests and sizes. [manifest.json](manifest.json) lists the files copied into the portable app's `resources/legal/speech` and each separately installed speech asset's `legal` directory.

## Included in the application

| Component | Version / selected terms | Source and build information |
| --- | --- | --- |
| JavaCV | 1.5.14; Apache-2.0 option of its dual licence | [Source](https://github.com/bytedeco/javacv/tree/1.5.14), [JavaCV-LICENSE.txt](JavaCV-LICENSE.txt) |
| JavaCPP and FFmpeg preset bindings | 1.5.14; Apache-2.0 option | [JavaCPP](https://github.com/bytedeco/javacpp/tree/1.5.14), [presets](https://github.com/bytedeco/javacpp-presets/tree/1.5.14), [JavaCPP-LICENSE.txt](JavaCPP-LICENSE.txt), [JavaCPP-Presets-LICENSE.txt](JavaCPP-Presets-LICENSE.txt) |
| FFmpeg native libraries | 8.1.2-1.5.14, standard `windows-x86_64`; LGPL-3.0-or-later | [FFmpeg source archive](https://ffmpeg.org/releases/ffmpeg-8.1.2.tar.bz2), [preset source and patches](https://github.com/bytedeco/javacpp-presets/tree/1.5.14/ffmpeg), [exact build script](https://github.com/bytedeco/javacpp-presets/blob/1.5.14/ffmpeg/cppbuild.sh), [FFmpeg-LICENSE.md](FFmpeg-LICENSE.md) |
| libopus | 1.3.1; BSD-3-Clause and the upstream patent notices | [Source](https://github.com/xiph/opus/tree/v1.3.1), [Opus-COPYING.txt](Opus-COPYING.txt) |
| sherpa-onnx Java API | 1.13.8; Apache-2.0 | [Source](https://github.com/k2-fsa/sherpa-onnx/tree/v1.13.8), [sherpa-onnx-LICENSE.txt](sherpa-onnx-LICENSE.txt) |

JavaCV and JavaCPP copyright Samuel Audet and their contributors; the upstream dual-licence notices are retained in full. FFmpeg libraries are unmodified upstream JavaCPP binaries and remain dynamically loaded DLLs. Their configuration enables shared libraries and `--enable-version3`; Aigram does not select the `-gpl` classifier or enable `--enable-gpl`.

The FFmpeg build also uses zlib, LAME, Speex, OpenCORE AMR, VisualOn AMR-WB, OpenSSL, OpenH264, libvpx, FreeType, HarfBuzz, libxml2, SRT, libwebp, libaom, SVT-AV1 and zimg. The build script above pins their versions and original source download URLs. Their included `FFmpeg-*` notices retain the applicable licence and copyright terms; Apache-2.0 also applies to the OpenCORE/VisualOn code. Aigram selects the FreeType Project License alternative for FreeType. Portions of this software are copyright © The FreeType Project (https://www.freetype.org/). All rights reserved.

For the FFmpeg libraries, retain both [LGPLv3](FFmpeg-LGPL-3.0.txt) and [GPLv3](GPL-3.0.txt), which LGPLv3 incorporates. The linked original sources, preset build scripts, patches and dependency source URLs form the corresponding-source route for these upstream binaries. Distributors must preserve access to the source corresponding to the binaries they convey and permit replacement and debugging of the LGPL libraries under the applicable terms. Changing a native classifier or rebuilding the binaries requires updating this record.

## Downloaded only when requested

The native sherpa runtime and voice/recognition model weights are not included in the EXE. They are downloaded separately from the official releases recorded in Aigram's [versioned catalog](https://github.com/kreeeeeel/boltunya/blob/main/backend/src/main/resources/speech/catalog.json). Optional installation does not remove their licence conditions.

- **sherpa-onnx Windows JNI runtime 1.13.8:** sherpa-onnx code is Apache-2.0; its ONNX Runtime dependency is MIT. The upstream Windows build [pins ONNX Runtime 1.28.2](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/cmake/onnxruntime-win-x64.cmake). Retain [ONNX-Runtime-LICENSE.txt](ONNX-Runtime-LICENSE.txt) and [ONNX-Runtime-ThirdPartyNotices.txt](ONNX-Runtime-ThirdPartyNotices.txt).
- **eSpeak NG:** the TTS runtime contains the phonemizer and Piper archives contain its data. sherpa's [build definition](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/cmake/espeak-ng-for-piper.cmake) pins the [fork at ed530aa113046142eb5115cf2fc9157854d0ffe1](https://github.com/csukuangfj/espeak-ng/tree/ed530aa113046142eb5115cf2fc9157854d0ffe1). It builds this dependency statically; the Apache-2.0 licence of sherpa's own source does not replace the GPL-3.0-or-later terms of the included code. Retain [eSpeak-NG-COPYING.txt](eSpeak-NG-COPYING.txt) and access to its corresponding source and build configuration when distributing the runtime or data.
- **Whisper tiny:** OpenAI publishes its code and model weights under [MIT](Whisper-LICENSE.txt). Aigram uses the sherpa ONNX conversion from the pinned upstream release.
- **Piper Irina:** [the original model card](Piper-Irina-MODEL_CARD.md) states that the dataset licence is unknown. Aigram does not infer unrestricted commercial or redistribution rights from the repository's MIT metadata.
- **Piper Ruslan:** [the original model card](Piper-Ruslan-MODEL_CARD.md) identifies the dataset's [CC-BY-NC-SA-4.0 terms](CC-BY-NC-SA-4.0.txt), including attribution, noncommercial and share-alike conditions.
- **Piper Amy:** [the original model card](Piper-Amy-MODEL_CARD.md) refers to [Mimic3 voice/dataset notices](https://github.com/MycroftAI/mimic3-voices). The repository-wide MIT metadata does not replace the selected dataset's terms.

Model cards are retained with the notices even where an upstream archive omits a separate model licence. None of the three synthesis voices is described here as unconditionally unrestricted for commercial redistribution. Source packages and models must keep their own notices when repackaged.
