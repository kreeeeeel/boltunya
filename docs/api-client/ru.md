# API-контракт и клиенты

[English version](en.md)

Единственный исходный контракт — [`backend/contract/api/schema/api.yaml`](../../backend/contract/api/schema/api.yaml). Он описывает 60 путей, 74 операции и 114 именованных схем. Операции находятся в `schema/operations/`, общие модели и перечисления — в `schema/models/`. Файлы связаны относительными ссылками.

Spring Boot управляет зависимостями и встроенным servlet-сервером. Контроллеры по функциям реализуют все восемь сгенерированных интерфейсов Spring MVC; компоненты используют сервисы приложения, а stateless extension-функции верхнего уровня связывают generated DTO с domain-моделями без Spring-бинов. Предыдущий сервер и роутер `ru.aigram.http` удалены. Веб-интерфейс вызывает generated-операции по функциям из `@aigram/api-client`; workspace alias использует SDK, созданный из единого контракта. Прежние транспорт `openapi-fetch`, отдельный генератор типов и промежуточный schema-файл удалены.

Шесть отдельных входных схем сохраняют прежние значения по умолчанию для правил чата, параметров и возможностей подключённой модели, промптов, оформления и хранения событий. В ответах все необходимые поля остаются обязательными. Kotlin-клиенты передают соответствующую модель `*Input`. Имена операций, URL и JSON-полей сохранены; PATCH по-прежнему различает отсутствие поля и недопустимый явный `null`. Значения по умолчанию, включая пустые вложенные объекты, создаёт генератор. Проверяемый маркер `x-kotlin-empty-default` компенсирует потерю таких метаданных в закреплённой версии генератора.

Добавлена операция `POST /api/engine/restart`. Она перезапускает только процесс Ollama, которым управляет Aigram; для внешнего сервиса возвращается типизированный конфликт. Двоичные загрузки принимают произвольный Content-Type, включая WebP; ответы сервера сохраняют потоковую передачу ресурсов и byte ranges.

## Модули

| Gradle-проект                         | Назначение                                                                                                 | Результат                                                            |
| ------------------------------------- | ---------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------- |
| `:backend:contract:api`               | Проверка и сборка контракта; генерация интерфейсов и DTO через `kotlin-spring` для Spring Boot 4/Jackson 3 | `build/bundled/api.yaml`, `build/generated/openapi`, JVM-библиотека  |
| `:backend:contract:client:openapi`    | Генерация Kotlin-транспорта `jvm-spring-webclient`, моделей и восьми групп API                             | `build/generated/openapi`, JVM-библиотека                            |
| `:backend:contract:client`            | Написанные вручную `AigramClientFactory` и обёртка `AigramClient` с управлением ресурсами                  | JVM-библиотека, экспортирующая зависимость на сгенерированный клиент |
| `:backend:contract:client:typescript` | Генерация Fetch SDK, компиляция JavaScript и деклараций, упаковка и проверка установки потребителем        | `build/generated/openapi`, `build/dist`, `build/package`             |

Все три генератора используют OpenAPI Generator **7.25.0**. JVM-модули собираются на JDK **21** и Kotlin **2.3.21**. Для TypeScript-модуля нужен Node.js **22 или новее**; TypeScript **5.9.3** закреплён в собственном lockfile.

Доступны группы `activity`, `chats`, `media`, `memory`, `models`, `settings`, `system` и `telegram`. Идентификаторы, объявленные строками, остаются строками, в том числе Telegram ID за пределами безопасного целого JavaScript. Именованные перечисления сохраняют значения JSON. Модель ошибки в TypeScript называется `ApiError`, чтобы избежать конфликта имён языка; формат JSON-ошибки от этого не меняется.

## Проверка и генерация

Команды выполняются из корня репозитория в PowerShell. На других платформах используйте `./gradlew`.

```powershell
.\gradlew.bat :backend:contract:api:validateSource
.\gradlew.bat :backend:contract:api:bundleContract
.\gradlew.bat :backend:contract:api:openApiGenerate :backend:contract:client:openapi:openApiGenerate :backend:contract:client:typescript:generateClient
```

`bundleContract` зависит от проверки исходников. Задача создаёт единый производный документ с локальными ссылками на компоненты. Это сохраняет типы вложенных моделей при передаче дерева внешних ссылок в OpenAPI Generator 7.25.0. Каждый генератор проверяет и использует один и тот же bundle. Исходное дерево схем тоже объявлено входом Gradle: изменение подключённого файла делает генерацию неактуальной.

Компиляция и проверка библиотек контракта и устанавливаемого SDK:

```powershell
.\gradlew.bat :backend:contract:api:check :backend:contract:client:check :backend:contract:client:typescript:check
```

Цепочка TypeScript: `validateContract` → `generateClient` → `npmCi` → `typecheckClient` → `compileClient` → `packageClient` → `consumerSmoke`. Генерация очищает предыдущий результат. `npmCi` устанавливает закреплённый компилятор с отключёнными lifecycle-скриптами. Smoke-тест устанавливает готовый архив в изолированный временный npm-проект без обращения к реестру, компилирует настоящие типизированные импорты и проверяет сгенерированные запросы через подставную реализацию Fetch.

Исправления сгенерированного кода вносятся в схему, настройки генератора или сохранённые шаблоны. Файлы внутри `build/` можно пересоздать.

### Веб-интерфейс в монорепозитории

Production-запросы UI вызывают generated-клиенты по функциям через workspace alias `@aigram/api-client`, который указывает на `backend/contract/client/typescript/build/generated/openapi/src/index.ts`. Сборка компилирует эти исходники в веб-приложение; отдельный архив SDK предназначен и для внешних потребителей.

Gradle-задача `buildWeb` зависит от `:backend:contract:client:typescript:generateClient`, а `scripts/build.ps1` генерирует клиент перед npm-проверками. Для самостоятельной работы с вебом сначала запустите канонический генератор из корня:

```powershell
.\gradlew.bat :backend:contract:client:typescript:generateClient --no-daemon
npm.cmd run check --prefix web
```

Предварительная проверка `sdk:check` сообщает об отсутствующем SDK перед dev, build, typecheck и unit-тестами. Она не создаёт отдельную схему или клиент только из типов. Docker-сборка веба запускает тот же Gradle-генератор на JDK 21 перед Node.js-этапом.

## Пакет TypeScript

Сборка только распространяемого пакета:

```powershell
.\gradlew.bat :backend:contract:client:typescript:packageClient -PappVersion=2.6.30
```

Пакет называется `@aigram/api-client`. Его версия берётся из корневого свойства `appVersion`, по умолчанию для разработки — `2.6.0`. Исходные `package.json` и lockfile сохраняют эту версию; релизная сборка задаёт версию производного пакета без изменения исходников.

- `backend/contract/client/typescript/build/dist/` содержит ESM JavaScript, декларации `.d.ts`, метаданные пакета и уведомления о лицензиях шаблонов.
- `backend/contract/client/typescript/build/package/manifest.json` содержит фактические имя архива, версию, integrity и список файлов от npm.
- В том же каталоге находится `aigram-api-client-<version>.tgz`.
- Скрипт упаковки приложения копирует его в `complie/Aigram-API-Client.tgz`. Workflow релиза прикладывает архив и его SHA-256 вместе с приложением. Публикация в npm-реестр не выполняется.

Установка собранного или скачанного архива:

```shell
npm install ./Aigram-API-Client.tgz
```

### Браузер

Явно заданный пустой `basePath` направляет запросы на текущий origin. Существующая cookie API-сессии отправляется согласно политике `credentials` Fetch:

```typescript
import { ChatsApi, Configuration } from "@aigram/api-client";

const chats = new ChatsApi(
    new Configuration({
        basePath: "",
        credentials: "same-origin",
    }),
);

const messages = await chats.history({
    chatId: "-1009007199254740993555",
    before: "9007199254740993999",
});
```

Если `basePath` не передан, возникает `RequiredError` до вызова Fetch. SDK не определяет адрес через `window`. В конфигурацию можно передать `fetchApi`, заголовки и middleware. Ответы проходят без преобразования моделей во время выполнения: сохраняются неизвестные свойства, nullable-поля и строковые ID. Сгенерированные типы при этом описывают объявленный API.

### Node.js и смена токена

Сессия в контракте использует cookie `tiho`. Браузер работает со своей cookie jar и `credentials`; потребитель Node.js передаёт cookie явно. Параметр генератора `apiKey` не реализует cookie-авторизацию для этой схемы.

Актуальный токен можно получать перед каждым запросом через middleware:

```typescript
import { Configuration, SystemApi } from "@aigram/api-client";

export function createApi(basePath: string, sessionToken: () => string) {
    return new SystemApi(
        new Configuration({
            basePath,
            middleware: [
                {
                    pre: async ({ url, init }) => {
                        const headers = new Headers(init.headers);
                        headers.set("Cookie", `tiho=${sessionToken()}`);
                        return { url, init: { ...init, headers } };
                    },
                },
            ],
        }),
    );
}
```

Получение сессии остаётся задачей приложения или интеграции. Токены нельзя помещать в URL и журналы. При HTTP-ошибке исходный ответ доступен через `ResponseError.response`, при ошибке транспорта причина находится в `FetchError.cause`. SDK не добавляет политику повторных запросов. Smoke-тест проверяет однократное выполнение неуспешного запроса.

## Kotlin-клиент

Внутри этой Gradle-сборки подключается `project(":backend:contract:client")`. Публичная фабрика — [`AigramClientFactory.create(URI, () -> String)`](../../backend/contract/client/src/main/kotlin/ru/aigram/client/configuration/AigramClientFactory.kt). Она требует HTTP- или HTTPS-origin без пользовательских данных, дополнительного пути, query-параметров и fragment.

Фабрика — явная точка входа библиотеки. Потребитель может зарегистрировать её в своём DI-контейнере; модуль не создаёт bean клиента приложения автоматически. Бинарные операции с медиа используют `ByteArray` для байтов ответа.

Буфер ответов фабрики ограничен **16 МиБ**, включая медиа. Обычные фотографии больше стандартных 256 КиБ WebClient поддерживаются без безлимитного выделения памяти. Для больших файлов можно передать собственный `WebClient` с подходящим ограничением кодеков в generated API-группу; потоковая загрузка больших файлов выходит за рамки этой обёртки. Существующий просмотрщик приложения использует отдельный транспорт.

`AigramClient` предоставляет `activity`, `chats`, `media`, `memory`, `models`, `settings`, `system` и `telegram`. Сгенерированные операции возвращают Reactor `Mono`. Обёртка владеет JDK HTTP-транспортом и должна закрываться. Пример блокирующего вызова для CLI:

```kotlin
import ru.aigram.client.configuration.AigramClientFactory
import ru.aigram.client.generated.model.Message
import java.net.URI

fun history(origin: URI, token: () -> String): List<Message>? =
    AigramClientFactory().create(origin, token).use { client ->
        client.chats.history(
            chatId = "-1009007199254740993555",
            before = "9007199254740993999",
        ).block()
    }
```

Реактивный потребитель может продолжить цепочку `Mono` и закрыть клиент после её завершения. Функция получения сессии вызывается при каждой подписке/запросе, поэтому смена токена не требует нового клиента. Некорректное значение сессии отклоняется до отправки. Фабрика отключает перенаправления и не добавляет автоматические повторы. HTTP-ошибки передаются по правилам WebClient через `WebClientResponseException`. Отсутствующее обязательное тело ответа вызывает `IllegalStateException` вместо подстановки выдуманного пустого результата.

### Совместимость Jackson

Серверный контракт генерируется для Spring Boot 4/Jackson 3. Генератор Kotlin WebClient сейчас использует поддерживаемую ветку шаблонов `useSpringBoot3` с сериализаторами Jackson 2 на Spring Framework 7. Это настройка совместимости шаблона, а не перевод приложения на Spring Boot 3.

Обёртка явно устанавливает `Jackson2JsonEncoder` и `Jackson2JsonDecoder` с mapper сгенерированного SDK. Серверные DTO и клиентские модели находятся в разных пакетах. При настройке транспорта эти кодеки нужно сохранить: стандартный кодек Jackson 3 из Boot 4 для клиентских моделей Jackson 2 может изменить сериализацию.

## Материалы для сопровождения

Настройки генераторов описаны в документации [`kotlin-spring`](https://openapi-generator.tech/docs/generators/kotlin-spring/), [`kotlin`](https://openapi-generator.tech/docs/generators/kotlin/) и [`typescript-fetch`](https://openapi-generator.tech/docs/generators/typescript-fetch/). Настройка кодеков и фильтров WebClient приведена в [справочнике Spring](https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-builder.html).

Изменения шаблонов и исходные лицензии отмечены в уведомлениях для [серверного контракта](../../backend/contract/api/templates/NOTICE.md), [Kotlin-клиента](../../backend/contract/client/openapi/templates/NOTICE.md) и [Fetch runtime](../../backend/contract/client/typescript/templates/NOTICE.md).
