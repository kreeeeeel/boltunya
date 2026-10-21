# Dependency injection

The Kotlin base package is `ru.aigram`. Sources and tests belong to the `backend` module.

Spring Boot 4.1.1 manages constructor-injected singleton components. `Main` supplies one launch configuration, opens the context and explicitly starts `ApplicationRuntime`. `AigramApplication` scans the components. Feature-specific configurations provide external clients, callbacks and resources using `@Bean`; services never request the container themselves. Guice and its module have been removed.

Deferred Jakarta `Provider<T>` dependencies retain lazy access across Telegram updates, replies and memory. Interface injection resolves the same singleton implementation: the reply coordinator also implements the memory work queue, and both summarizer interfaces share one instance. There is no application service locator or replacement container.

`ApplicationRuntime` owns the profile lock and ordered startup/shutdown. Spring closes it with the context; closeable services are idempotent so subsequent bean destruction is safe. Spring also releases already-created resources if context creation fails before the runtime exists. Creating a context alone does not authorize Telegram, start monitoring or run inference. The default `%LOCALAPPDATA%/Aigram` profile and existing sessions are unchanged.

Run `gradlew.bat :backend:check`. Spring context tests resolve the graph, inspect service identities, deferred injection, saved startup preferences and repeated close without sending Telegram messages or calling a model. The bundled runtime includes `java.instrument` and is checked by isolated native Electron smoke scenarios.

The desktop shell remains in `desktop` and the web interface in `web`. `scripts/build.ps1` produces `complie/Aigram.exe`. The old private HTTP bridge remains temporarily until the next [contract-first migration stage](../migration/en.md).

