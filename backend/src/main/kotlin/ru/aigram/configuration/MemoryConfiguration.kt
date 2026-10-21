package ru.aigram.configuration

import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.aigram.ai.ModelInference
import ru.aigram.ai.OllamaRuntime
import ru.aigram.ai.profile.ModelTargetResolver
import ru.aigram.automation.MemoryWorkQueue
import ru.aigram.diagnostics.DiagnosticLog
import ru.aigram.memory.MemoryCoordinator
import ru.aigram.memory.MemoryProcessor
import ru.aigram.memory.MemoryStore
import ru.aigram.memory.ParticipantRosterStore
import ru.aigram.storage.AutomatedReplyStore
import ru.aigram.storage.ContextExclusionStore
import ru.aigram.storage.SettingsStore
import ru.aigram.telegram.TelegramGateway
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class MemoryConfiguration {
    @Bean
    fun memoryCoordinator(
        store: MemoryStore,
        settings: SettingsStore,
        telegram: TelegramGateway,
        runtime: OllamaRuntime,
        processor: MemoryProcessor,
        inference: ModelInference,
        clock: Clock,
        automated: AutomatedReplyStore,
        queue: ObjectProvider<MemoryWorkQueue>,
        diagnostics: DiagnosticLog,
        targets: ModelTargetResolver,
        roster: ParticipantRosterStore,
        exclusions: ContextExclusionStore,
    ): MemoryCoordinator = MemoryCoordinator(
        store, settings, telegram, runtime, processor, inference, clock, automated, queue,
        diagnostics, targets, roster, exclusions,
    )
}
