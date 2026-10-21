# Dependency injection

The Kotlin base package is `ru.aigram`. Sources and tests belong to the `backend` module.

The container is Google Guice 7.0.0 with `jakarta.inject`. `Main` creates the launch configuration and injector, then requests `ApplicationRuntime`. Services use `@Inject constructor` for constructor injection. `ApplicationModule` registers application services in `Singleton` scope: stores, models, Telegram and the reply queue share instances within one application launch.

`@Provides` handles external configuration, HTTP clients and callback integrations. A deferred `Provider<ReplyCoordinator>` in the Telegram gateway breaks the cycle between receiving updates and sending replies. Services never request the container themselves. `requireExplicitBindings()` detects missing registrations during container creation.

`PersonalReplyGenerator` prepares personal conversation context. `ApplicationRuntime` owns service startup and shutdown. The `%LOCALAPPDATA%/Tiho` directory remains compatible with existing Telegram sessions; its name is unrelated to the Kotlin package.

Run `gradlew.bat :backend:check`. `ApplicationModuleTest` resolves the complete graph and checks shared instances without authenticating with Telegram or sending messages.

The desktop shell is a separate Electron TypeScript module in `desktop`. Kotlin provides local integrations and HTTP APIs. `scripts/build.ps1` produces a single portable `complie/Aigram.exe`.

