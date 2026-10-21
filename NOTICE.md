# Сторонние компоненты

Поставка использует динамические библиотеки и открытые проекты. Их исходники, лицензии и условия доступны по ссылкам:

| Компонент | Лицензия / источник |
| --- | --- |
| Kotlin, kotlinx.serialization | Apache-2.0 — https://github.com/JetBrains/kotlin |
| TDLight Java / native bindings | LGPL-3.0 — https://github.com/tdlight-team/tdlight-java |
| Telegram TDLib | Boost Software License — https://github.com/tdlib/td |
| JNA | LGPL-2.1 / Apache-2.0 — https://github.com/java-native-access/jna |
| Hibernate Validator | Apache-2.0 — https://github.com/hibernate/hibernate-validator |
| Next.js, React | MIT — https://github.com/vercel/next.js , https://github.com/facebook/react |
| Gravity UI | MIT — https://github.com/gravity-ui/uikit |
| Gravity Markdown Editor / ProseMirror | MIT — https://github.com/gravity-ui/markdown-editor , https://github.com/ProseMirror/prosemirror |
| highlight.js / lowlight | BSD-3-Clause / MIT — https://github.com/highlightjs/highlight.js , https://github.com/wooorm/lowlight |
| next-intl | MIT — https://github.com/amannn/next-intl |
| openapi-typescript / openapi-fetch | MIT — https://github.com/openapi-ts/openapi-typescript |
| jsoup | MIT — https://jsoup.org/ |
| MCP Java SDK | MIT — https://github.com/modelcontextprotocol/java-sdk |
| Noto Sans | SIL Open Font License — https://github.com/notofonts/latin-greek-cyrillic; bundled license: web/src/shared/assets/fonts/OFL.txt |
| Ollama | MIT — https://github.com/ollama/ollama |
| Spring AI / Spring Framework | Apache-2.0 — https://github.com/spring-projects/spring-ai , https://github.com/spring-projects/spring-framework |
| JavaCV 1.5.14 | Apache-2.0, выбранный вариант двойной лицензии — [лицензия и исходники](https://github.com/bytedeco/javacv/blob/1.5.14/LICENSE.txt) |
| JavaCPP 1.5.14 / JavaCPP FFmpeg bindings | Apache-2.0, выбранный вариант двойной лицензии — [JavaCPP](https://github.com/bytedeco/javacpp/blob/1.5.14/LICENSE.txt), [FFmpeg preset](https://github.com/bytedeco/javacpp-presets/tree/1.5.14/ffmpeg) |
| FFmpeg 8.1.2, JavaCPP preset 8.1.2-1.5.14 | LGPL-3.0-or-later, обычный `windows-x86_64` classifier — [условия](https://github.com/FFmpeg/FFmpeg/blob/n8.1.2/LICENSE.md), [LGPLv3](https://github.com/FFmpeg/FFmpeg/blob/n8.1.2/COPYING.LGPLv3), [GPLv3, включённая в LGPLv3](https://github.com/FFmpeg/FFmpeg/blob/n8.1.2/COPYING.GPLv3) |
| libopus 1.3.1 в FFmpeg | BSD-3-Clause — [лицензия и патентные уведомления](https://github.com/xiph/opus/blob/v1.3.1/COPYING) |
| sherpa-onnx Java API 1.13.8 | Apache-2.0 — [лицензия и исходники](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/LICENSE) |
| Qwen 3, Qwen 2.5 7B | Apache-2.0 — https://huggingface.co/Qwen |
| Gemma 3 | Gemma Terms of Use — https://ai.google.dev/gemma/terms |
| Eclipse Temurin runtime | GPL-2.0 with Classpath Exception — https://adoptium.net |

Лицензии Java runtime находятся в его каталоге `legal`. Уведомления зависимостей доступны в соответствующих JAR/META-INF и репозиториях; не каждый upstream JAR содержит полные тексты лицензий, поэтому для аудиокомпонентов ниже отдельно указаны точные источники. Папка Ollama содержит лицензии её нативных библиотек. Пользовательские API ID / API Hash не включены в исходники или поставку. Приложение не связано с Telegram официально.

- [lottie-web](https://github.com/airbnb/lottie-web), MIT — отображение анимированных Telegram-стикеров TGS.

Погодные данные предоставляет [Open-Meteo](https://open-meteo.com/) по [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Основной поиск обращается к DuckDuckGo и резервной RSS-выдаче Bing; Exa MCP используется только как дополнительный резерв с ключом пользователя; эти внешние сервисы не входят в поставку приложения. Бесплатный доступ Exa имеет ограничения; собственный ключ относится к учётной записи пользователя Exa.

- [Electron](https://github.com/electron/electron), MIT — окно и системный трей. Уведомления Chromium включены в поставку Electron.
- [Spring Boot](https://github.com/spring-projects/spring-boot), Apache-2.0 — жизненный цикл и внедрение зависимостей.
- [OSHI](https://github.com/oshi/oshi), MIT — мониторинг ресурсов.
- [Recharts](https://github.com/recharts/recharts), MIT — графики.

Официальные курсы запрашиваются из XML-сервиса Банка России. Датированные новостные заголовки и описания запрашиваются из публичных RSS РБК и ТАСС; сервисы и их содержимое не входят в поставку приложения.

## Офлайн-речь и аудиокодек

JavaCV, JavaCPP и динамические библиотеки FFmpeg входят в приложение. Используется стандартный Windows x64 артефакт FFmpeg с `--enable-version3`, без `--enable-gpl` и без classifier `windows-x86_64-gpl`. Это сборка LGPL-3.0-or-later, а не Apache-2.0. Исходники соответствующей версии доступны в [архиве FFmpeg 8.1.2](https://ffmpeg.org/releases/ffmpeg-8.1.2.tar.bz2); точные параметры сборки и применённые патчи — в [JavaCPP preset 1.5.14](https://github.com/bytedeco/javacpp-presets/tree/1.5.14/ffmpeg), включая [cppbuild.sh](https://github.com/bytedeco/javacpp-presets/blob/1.5.14/ffmpeg/cppbuild.sh). Условия сторонних библиотек этой сборки сохраняются отдельно от лицензии Java-обёрток.

Полные тексты лицензий, copyright notices, карточки голосов и ссылки на соответствующие исходники находятся в [docs/speech/licenses](https://github.com/kreeeeeel/boltunya/tree/main/docs/speech/licenses). При упаковке эта папка копируется в `resources/legal/speech`; отдельно установленные речевые компоненты сохраняют её в своём каталоге `legal`. Манифест перечисляет копируемые файлы, а `provenance.json` содержит исходный URL, SHA-256 и размер каждого upstream документа.

Нативный CPU runtime sherpa-onnx и веса речевых моделей скачиваются отдельно по явному действию пользователя; они не встроены в EXE. Политика установки и закреплённые артефакты описаны в [документации](https://github.com/kreeeeeel/boltunya/blob/main/docs/speech/runtime-ru.md) и [каталоге](https://github.com/kreeeeeel/boltunya/blob/main/backend/src/main/resources/speech/catalog.json).

- Код sherpa-onnx — [Apache-2.0](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/LICENSE); ONNX Runtime — [MIT](https://github.com/microsoft/onnxruntime/blob/main/LICENSE).
- Опциональная TTS-сборка runtime содержит eSpeak NG; голосовые архивы Piper также содержат `espeak-ng-data`. Лицензия фонемизатора — GPL-3.0-or-later: [закреплённые исходники и параметры подключения](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/cmake/espeak-ng-for-piper.cmake), [текст GPLv3](https://github.com/csukuangfj/espeak-ng/blob/ed530aa113046142eb5115cf2fc9157854d0ffe1/COPYING). Лицензия Apache-2.0 проекта sherpa-onnx не заменяет условия включённого eSpeak NG.
- OpenAI Whisper, включая исходные веса, опубликован под [MIT](https://github.com/openai/whisper/blob/main/LICENSE); каталог использует ONNX-артефакты официальных релизов sherpa-onnx.
- Общая метка MIT репозитория Piper не заменяет происхождение и ограничения конкретного голоса. В [карточке Ирины](https://huggingface.co/rhasspy/piper-voices/blob/main/ru/ru_RU/irina/medium/MODEL_CARD) лицензия датасета указана как неизвестная. [Карточка Руслана](https://huggingface.co/rhasspy/piper-voices/blob/main/ru/ru_RU/ruslan/medium/MODEL_CARD) указывает CC-BY-NC-SA-4.0, включая ограничение некоммерческого использования. [Карточка Amy](https://huggingface.co/rhasspy/piper-voices/blob/main/en/en_US/amy/low/MODEL_CARD) отсылает к условиям соответствующего датасета Mimic3. Эти голоса не объявляются безусловно пригодными для любого коммерческого применения или перепаковки.

При распространении бинарников сохраняются полные тексты применимых лицензий и copyright notices. Для LGPL/GPL-компонентов необходимо обеспечить доступ к соответствующим исходникам, параметрам сборки и патчам, а также соблюдать условия замены и отладки библиотек; ссылки выше обозначают происхождение и не подменяют эти требования. Замена classifier или перепаковка optional runtime/моделей требует повторной проверки их состава и условий.
