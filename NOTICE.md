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
| next-intl | MIT — https://github.com/amannn/next-intl |
| openapi-typescript / openapi-fetch | MIT — https://github.com/openapi-ts/openapi-typescript |
| jsoup | MIT — https://jsoup.org/ |
| MCP Java SDK | MIT — https://github.com/modelcontextprotocol/java-sdk |
| Noto Sans | SIL Open Font License — https://github.com/notofonts/latin-greek-cyrillic; bundled license: web/src/shared/assets/fonts/OFL.txt |
| Ollama | MIT — https://github.com/ollama/ollama |
| Spring AI / Spring Framework | Apache-2.0 — https://github.com/spring-projects/spring-ai , https://github.com/spring-projects/spring-framework |
| Qwen 3, Qwen 2.5 7B | Apache-2.0 — https://huggingface.co/Qwen |
| Gemma 3 | Gemma Terms of Use — https://ai.google.dev/gemma/terms |
| Eclipse Temurin runtime | GPL-2.0 with Classpath Exception — https://adoptium.net |

Лицензии runtime находятся в его каталоге `legal`, зависимостей — в соответствующих JAR/META-INF и репозиториях. Папка Ollama содержит лицензии её нативных библиотек. Пользовательские API ID / API Hash не включены в исходники или поставку. Приложение не связано с Telegram официально.

- [lottie-web](https://github.com/airbnb/lottie-web), MIT — отображение анимированных Telegram-стикеров TGS.

Погодные данные предоставляет [Open-Meteo](https://open-meteo.com/) по [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Основной поиск обращается к DuckDuckGo и резервной RSS-выдаче Bing; Exa MCP используется только как дополнительный резерв с ключом пользователя; эти внешние сервисы не входят в поставку приложения. Бесплатный доступ Exa имеет ограничения; собственный ключ относится к учётной записи пользователя Exa.

- [Electron](https://github.com/electron/electron), MIT — окно и системный трей. Уведомления Chromium включены в поставку Electron.
- [Google Guice](https://github.com/google/guice), Apache-2.0 — внедрение зависимостей.
- [OSHI](https://github.com/oshi/oshi), MIT — мониторинг ресурсов.
- [React Flow](https://github.com/xyflow/xyflow), MIT — редактор сценариев.
- [Recharts](https://github.com/recharts/recharts), MIT — графики.

Официальные курсы запрашиваются из XML-сервиса Банка России. Датированные новостные заголовки и описания запрашиваются из публичных RSS РБК и ТАСС; сервисы и их содержимое не входят в поставку приложения.
