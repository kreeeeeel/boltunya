# Локальный каталог русских и английских голосов

Для новых загрузок доступны **32 голоса синтеза: 17 Sherpa/Piper и 15 конверсий RVC**, а также два движка и Whisper tiny для распознавания. Уже установленные голоса на других языках и импортированные модели сохраняют исходные ID и настройки. Старые 63 определения остаются в реестре для совместимости; неустановленные иностранные голоса не предлагаются для новых загрузок. Запуск приложения и выбор голоса ничего не скачивают.

## Движки и загрузки

Sherpa/Piper читает текст напрямую. RVC меняет тембр готовой записи: **локальный Piper → WAV → Applio/RVC → WAV → голосовое Opus**. Для русских моделей базой служит Ирина или Руслан, для Joe Bartolozzi — существующая мужская английская модель Ryan. При ошибке конверсии возвращается ошибка, а не обычный базовый голос под именем персонажа.

Отдельный [официальный portable Applio 3.7.0](https://huggingface.co/IAHispano/Applio/tree/e908bdd5bf9f18cede26bbbec724b1a4bc7c2f14/Compiled/Windows) занимает **6 775 972 339 байт (6,31 ГиБ)**; сумма распакованных файлов ZIP64 — **10 386 551 490 байт**. Перед установкой проверяется свободное место на архив плюс запас распаковки 12 ГиБ. В EXE Aigram движок не включён. До установки интерфейс показывает недостающие зависимости и размер загрузки. Кнопка установки голоса ставит движок, базовый голос и веса персонажа в последовательную очередь, каждый со своим прогрессом. Отмена голоса отменяет только его неиспользуемые другими запросами зависимости. При ошибке зависимости родитель получает ошибку, а не остаётся в очереди навсегда.

Адаптер запускает встроенный интерпретатор с [официальной командой core.py infer](https://github.com/IAHispano/Applio/blob/3.7.0/core.py), на CPU с двумя потоками и offline-настройками Hugging Face. Установщик, обновлятор, веб-сервер Applio, облачный TTS и pip не запускаются. При установке сохраняются уведомления Python/зависимостей и копируются [версионированные лицензии](licenses/README.md). ZIP64-распаковка проверяет выход из каталога, ссылки, Windows-алиасы, дубликаты, размер и CRC. До распаковки проверяется SHA-256.

## Что проверено

Настоящая проверка Алисы с закреплёнными Applio и community-моделью прошла: Piper → RVC → Opus → Whisper сохранил смысл произнесённого, а преобразованная запись отличается от базового голоса. Установка в отдельный профиль заняла 420,305 с; синтез записи длительностью 3,260 с — 82,607 с на двух потоках CPU; вся проверка — 508,497 с. Это измерения, а не обещание скорости. Сообщения в Telegram и запросы к чат-модели не отправлялись. Доказательство: `build/speech-verification/rvc-proof-1434426430849561619/proof.json`. Для остальных 14 RVC-голосов проверены метаданные, но не настоящее аудио.

Sherpa/Piper берутся из официального [релиза sherpa](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models) и соответствуют [скриптам экспорта](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/scripts/piper/generate.py). Полное происхождение сохранено в [проверенном реестре](../../backend/src/test/resources/speech/catalog-provenance.json), определения установки — в [catalog.json](../../backend/src/main/resources/speech/catalog.json).

## Персонажи и стримеры

Здесь указаны реальные названия community-моделей; обычные голоса Piper не переименовываются в людей. [Карточка Joxi](licenses/RVC-Joxi-MODEL_CARD.md) просит указывать автора; версии и авторские обозначения архивов сохранены. [Карточка Алисы от mbsoft](licenses/RVC-Alice-MODEL_CARD.md) содержит только общую метку OpenRAIL. Точный текст этой лицензии и разрешения на датасеты не предоставлены. Права на голос, персонажа и коммерческое использование не выводятся из этой метки; связи с правообладателями и их одобрения не заявляются. Веса скачиваются отдельно с исходных площадок.

| Голос / источник | Язык | Файл модели / индекс | Архив, байт | SHA-256 |
| --- | --- | --- | ---: | --- |
| [Алиса · YandexAliceRVC · community](https://huggingface.co/mbsoft/YandexAliceRVC/tree/666ddc3386db4280648cf7de8c0a9ecccccd029e) | ru | `YandexAlice.pth`<br>`added_IVF130_Flat_nprobe_1_YandexAlice_v2.index` | 64,237,755 | `c31600a7a42543fc4a67cca3c0ffa8d92255692d0ac75045fe174b3959b1207a` |
| [Братишкин · Snowie V3](https://huggingface.co/Joxi/RVCv2_models/blob/743af4e5b43dd6afad3f0966a83d806428f768d0/bratishkinoff%20(Twitch%20Streamer)%20(Snowie%20V3).zip) | ru | `bratishkinoff.pth`<br>`added_IVF874_Flat_nprobe_1_bratishkinoff_v2.index` | 118,459,420 | `47e704502f22bc51dfdcbfa9e760a69843966efaab4b4678aaab956be6aae8bf` |
| [Igor Link](https://huggingface.co/Joxi/RVCv2_models/blob/743af4e5b43dd6afad3f0966a83d806428f768d0/IgorLink.zip) | ru | `IgorLink.pth`<br>`added_IVF683_Flat_nprobe_1_IgorLink_v2.index` | 103,828,821 | `581783d4e6329e5558f097014774cd2f9c046aa25d5a9c372292e28f80cb9600` |
| [JesusAVGN · TITAN](https://huggingface.co/Joxi/RVCv2_models/blob/743af4e5b43dd6afad3f0966a83d806428f768d0/JesusAVGN%20(TITAN-Medium).zip) | ru | `HesusAVGN.pth`<br>`added_IVF329_Flat_nprobe_1_HesusAVGN_v2.index` | 96,844,447 | `469d954a8491de483fb2edd4223894592be3ee210c6ede7cdb6a463ad28aaddd` |
| [Joe Bartolozzi · Ov2](https://huggingface.co/Joxi/RVCv2_models/blob/743af4e5b43dd6afad3f0966a83d806428f768d0/Joe%20Bartolozzi%20(Ov2).zip) | en | `JoeBartolozzi_Ov2.pth`<br>`added_IVF1118_Flat_nprobe_1_JoeBartolozzi_Ov2_v2.index` | 193,074,185 | `20055b69a7ec3210995cc408acf3d4032424f3d646c8743185dcf5b2855f7055` |
| [Джонни Сильверхенд · Cyberpunk 2077 · RUS](https://huggingface.co/Joxi/RVCv2_models/blob/743af4e5b43dd6afad3f0966a83d806428f768d0/Johnny%20Silverhand%20RUS%20(Cyberpunk%202077)%20(Ov2).zip) | ru | `Johnny_Silverhend_RUS_Ov2_e80_s4880.pth`<br>`added_IVF1464_Flat_nprobe_1_Johnny_Silverhend_RUS_Ov2_v2.index` | 160,391,186 | `c335caeed619642224210703fb9dd309534058e9bd5c563c8acd4ec2b62ee58a` |
| [Siri RU 2 · Snowie V3](https://huggingface.co/Joxi/RVCv2_models/blob/743af4e5b43dd6afad3f0966a83d806428f768d0/Siri%20RU%202%20(SnowieV3).zip) | ru | `Siri_RUvoice2.pth`<br>`added_IVF307_Flat_nprobe_1_Siri_RUvoice2_v2.index` | 93,135,563 | `0777c55c50e1a59fda4e28bbb56030749b853089a042344f26d3b9681e4713fb` |
| [Стас Ай Как Просто](https://huggingface.co/Joxi/RVCv2_models/blob/743af4e5b43dd6afad3f0966a83d806428f768d0/StasAiKakProsto.zip) | ru | `StasAiKakProsto.pth`<br>`added_IVF969_Flat_nprobe_1_StasAiKakProsto_v2.index` | 122,716,349 | `48b73be77c540b9489d2f9782c4b168dec8e75d7c30273037039150ac9f83065` |
| [Стинт](https://huggingface.co/Joxi/RVCv2_models/blob/743af4e5b43dd6afad3f0966a83d806428f768d0/Stintik.zip) | ru | `Stint.pth`<br>`added_IVF1216_Flat_nprobe_1_Stint_v2.index` | 138,952,516 | `58243daec3467be5807c0eb142cfb8dbaafc3a021c58d1651c7415c55030befb` |
| [T2x2](https://huggingface.co/Joxi/RVCv2_models/blob/743af4e5b43dd6afad3f0966a83d806428f768d0/T2x2.zip) | ru | `T2x2.pth`<br>`added_IVF2731_Flat_nprobe_1_T2x2_v2.index` | 246,713,983 | `29ea2baba5394ba8322cfee726270a6e963e74483aef6200d2d4f9bf92a83d37` |
| [itpedia](https://huggingface.co/Joxi/RVCv2_models/blob/743af4e5b43dd6afad3f0966a83d806428f768d0/itpedia.zip) | ru | `itpedia.pth`<br>`itpedia.index` | 147,662,924 | `819e90bb63269ca66ce2f6026c5ad937353a8d2c567ef16f7038000c19e96cd8` |
| [ivanzolo2004](https://huggingface.co/Joxi/RVCv2_models/blob/743af4e5b43dd6afad3f0966a83d806428f768d0/ivanzolo2004.zip) | ru | `ivanzolo.pth`<br>`ivanzolo.index` | 89,949,679 | `7a58142faebc28ffe7570de29c362225743b26eb07e808d3f89624c3b0254ec6` |
| [Mellsher](https://huggingface.co/Joxi/RVCv2_models/blob/743af4e5b43dd6afad3f0966a83d806428f768d0/Mellsher.zip) | ru | `Mellsher.pth`<br>`added_IVF1372_Flat_nprobe_1_Mellsher_v2.index` | 156,830,799 | `604811a7fbef883a9b98b0fcf73e947b2537b332d67cdc5e9808bc40864035dd` |
| [Wylsacom · Ov2](https://huggingface.co/Joxi/RVCv2_models/blob/743af4e5b43dd6afad3f0966a83d806428f768d0/Wylsacom%20(Ov2).zip) | ru | `Wylsacom_Ov2.pth`<br>`added_IVF2118_Flat_nprobe_1_Wylsacom_Ov2_v2.index` | 209,221,355 | `d9518a9f7e843acbf0d580185f2430a1bb9005fc6437a4ca805f2fd6a0eda0e1` |
| [donk · Team Spirit · Snowie V3](https://huggingface.co/Joxi/RVCv2_models/blob/743af4e5b43dd6afad3f0966a83d806428f768d0/donk%20(Team%20Spirit)%20(Snowie%20V3).zip) | ru | `donk.pth`<br>`added_IVF543_Flat_nprobe_1_donk_v2.index` | 90,062,087 | `90ef7d175a692fb4863b28b06b58186379d0ae6521194b5980c23a4d0643fbd9` |

## Прямой синтез текста

| Голос / источник | Язык | Asset ID |
| --- | --- | --- |
| [Piper · Ирина · Russian](https://huggingface.co/rhasspy/piper-voices/blob/main/ru/ru_RU/irina/medium/MODEL_CARD) | ru | `piper-ru-irina` |
| [Piper · Руслан · Russian](https://huggingface.co/rhasspy/piper-voices/blob/main/ru/ru_RU/ruslan/medium/MODEL_CARD) | ru | `piper-ru-ruslan` |
| [Piper · Amy · English](https://huggingface.co/rhasspy/piper-voices/blob/main/en/en_US/amy/low/MODEL_CARD) | en | `piper-en-amy` |
| [Piper · Денис · Russian (RU)](https://huggingface.co/rhasspy/piper-voices/blob/5041a7a49ae5b3a0f30e6837769eb03fdbb231bd/ru/ru_RU/denis/medium/MODEL_CARD) | ru | `piper-ru-denis` |
| [Piper · Дмитрий · Russian (RU)](https://huggingface.co/rhasspy/piper-voices/blob/5041a7a49ae5b3a0f30e6837769eb03fdbb231bd/ru/ru_RU/dmitri/medium/MODEL_CARD) | ru | `piper-ru-dmitri` |
| [Piper · Lessac · English (US)](https://huggingface.co/rhasspy/piper-voices/blob/5041a7a49ae5b3a0f30e6837769eb03fdbb231bd/en/en_US/lessac/medium/MODEL_CARD) | en | `piper-en-lessac` |
| [Piper · Ryan · English (US)](https://huggingface.co/rhasspy/piper-voices/blob/5041a7a49ae5b3a0f30e6837769eb03fdbb231bd/en/en_US/ryan/medium/MODEL_CARD) | en | `piper-en-ryan` |
| [Piper · Alan · English (GB)](https://huggingface.co/rhasspy/piper-voices/blob/5041a7a49ae5b3a0f30e6837769eb03fdbb231bd/en/en_GB/alan/medium/MODEL_CARD) | en | `piper-en-alan` |
| [Piper · Alba · English (GB)](https://huggingface.co/rhasspy/piper-voices/blob/5041a7a49ae5b3a0f30e6837769eb03fdbb231bd/en/en_GB/alba/medium/MODEL_CARD) | en | `piper-en-alba` |
| [Piper · Cori · English (GB)](https://huggingface.co/rhasspy/piper-voices/blob/5041a7a49ae5b3a0f30e6837769eb03fdbb231bd/en/en_GB/cori/medium/MODEL_CARD) | en | `piper-en-cori` |
| [Piper · Jenny Dioco · English (GB)](https://huggingface.co/rhasspy/piper-voices/blob/5041a7a49ae5b3a0f30e6837769eb03fdbb231bd/en/en_GB/jenny_dioco/medium/MODEL_CARD) | en | `piper-en-jenny-dioco` |
| [Piper · Ljspeech · English (US)](https://huggingface.co/rhasspy/piper-voices/blob/5041a7a49ae5b3a0f30e6837769eb03fdbb231bd/en/en_US/ljspeech/medium/MODEL_CARD) | en | `piper-en-ljspeech` |
| [Piper · Danny · English (US)](https://huggingface.co/rhasspy/piper-voices/blob/5041a7a49ae5b3a0f30e6837769eb03fdbb231bd/en/en_US/danny/low/MODEL_CARD) | en | `piper-en-danny` |
| [Piper · Joe · English (US)](https://huggingface.co/rhasspy/piper-voices/blob/5041a7a49ae5b3a0f30e6837769eb03fdbb231bd/en/en_US/joe/medium/MODEL_CARD) | en | `piper-en-joe` |
| [Piper · Hfc Female · English (US)](https://huggingface.co/rhasspy/piper-voices/blob/5041a7a49ae5b3a0f30e6837769eb03fdbb231bd/en/en_US/hfc_female/medium/MODEL_CARD) | en | `piper-en-hfc-female` |
| [Piper · Hfc Male · English (US)](https://huggingface.co/rhasspy/piper-voices/blob/5041a7a49ae5b3a0f30e6837769eb03fdbb231bd/en/en_US/hfc_male/medium/MODEL_CARD) | en | `piper-en-hfc-male` |
| [GLaDOS · English · Portal voice](https://github.com/dnhkng/GLaDOS) | en | `piper-en-glados` |

Условия датасетов Piper сохраняются: у Руслана CC-BY-NC-SA-4.0, у Ирины лицензия датасета не указана, другие карточки могут иметь свои ограничения. GLaDOS — настоящая английская Portal-модель из официального Sherpa-пакета; MIT исходного ПО не устанавливает отдельно права на модель и датасет. Подробнее — в [уведомлениях](licenses/README.md).

## DonationAlerts и недоступные форматы

[Публичный API DonationAlerts](https://www.donationalerts.com/apidoc) не публикует отдельный универсальный TTS-метод или веса голосов. [Настройка озвучки оповещений](https://www.donationalerts.com/help/text-to-speech-setup) не предоставляет лицензию на экспорт. Локальные модели не выдаются за точные копии всех голосов DonationAlerts. Найденный Minecraft-пакет предназначен для so-vits-svc 4.1, а не RVC v2 или Sherpa TTS; фиктивная совместимая карточка не добавляется. Другой формат требует реального движка и отдельно проверенного пакета.
