package ru.aigram.configuration

import org.springframework.ai.chat.model.ChatModel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.aigram.ai.DownloadWriter
import ru.aigram.ai.EngineManager
import ru.aigram.ai.EngineProcessLocator
import ru.aigram.ai.MessageContextFormatter
import ru.aigram.ai.ModelCatalog
import ru.aigram.ai.ModelInference
import ru.aigram.ai.OllamaDeviceSelector
import ru.aigram.ai.OllamaInstallation
import ru.aigram.ai.OllamaModelInventory
import ru.aigram.ai.OllamaRuntime
import ru.aigram.ai.PersonalStyleBuilder
import ru.aigram.ai.PromptBuilder
import ru.aigram.ai.RegistryBlobDownloader
import ru.aigram.ai.RegistryModelInstaller
import ru.aigram.ai.model.ModelResponseStream
import ru.aigram.ai.model.SpringAiModelFactory
import ru.aigram.ai.profile.ProfileContextBudget
import ru.aigram.ai.prompt.PromptTokenEstimator
import ru.aigram.diagnostics.DiagnosticLog
import ru.aigram.resources.ResourceCatalog
import ru.aigram.storage.ModelMeasurementStore
import ru.aigram.storage.SettingsStore
import ru.aigram.storage.StorageLocations
import java.net.http.HttpClient
import java.nio.file.Path
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class AiConfiguration {
    @Bean
    fun registryModelInstaller(
        models: ModelCatalog,
        http: HttpClient,
        downloader: RegistryBlobDownloader,
    ): RegistryModelInstaller = RegistryModelInstaller(models = models, http = http, downloader = downloader)

    @Bean
    fun ollamaRuntime(configuration: ApplicationConfiguration): OllamaRuntime = OllamaRuntime(configuration.ollamaUrl)

    @Bean
    fun ollamaInstallation(directory: Path): OllamaInstallation = OllamaInstallation(directory)

    @Bean
    fun ollamaModelInventory(): OllamaModelInventory = OllamaModelInventory()

    @Bean
    fun storageLocations(
        configuration: ApplicationConfiguration,
        installation: OllamaInstallation,
        resources: ResourceCatalog,
    ): StorageLocations = StorageLocations(configuration, installation, resources)

    @Bean
    fun chatModel(factory: SpringAiModelFactory, configuration: ApplicationConfiguration): ChatModel =
        factory.create(configuration.ollamaUrl)

    @Bean
    fun modelResponseStream(): ModelResponseStream = ModelResponseStream()

    @Bean
    fun promptBuilder(
        clock: Clock,
        personalStyle: PersonalStyleBuilder,
        authors: MessageContextFormatter,
        tokens: PromptTokenEstimator,
        budget: ProfileContextBudget,
    ): PromptBuilder = PromptBuilder(clock, personalStyle, authors, tokens, budget)

    @Bean
    fun engineManager(
        directory: Path,
        runtime: OllamaRuntime,
        models: ModelCatalog,
        installer: RegistryModelInstaller,
        writer: DownloadWriter,
        processes: EngineProcessLocator,
        measurements: ModelMeasurementStore,
        inference: ModelInference,
        installation: OllamaInstallation,
        inventory: OllamaModelInventory,
        diagnostics: DiagnosticLog,
        devices: OllamaDeviceSelector,
        settings: SettingsStore,
    ): EngineManager = EngineManager(
        directory, runtime, models, installer, writer, processes, measurements, inference, installation,
        inventory, diagnostics, devices,
        automaticEnabled = { !settings.get().paused },
    )
}
