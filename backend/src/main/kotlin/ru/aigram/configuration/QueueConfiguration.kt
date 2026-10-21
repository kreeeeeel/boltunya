package ru.aigram.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.aigram.automation.PersonalReplyGenerator
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.automation.ReplyDeliveryPolicy
import ru.aigram.automation.activity.ActivityHistoryStore
import ru.aigram.automation.activity.ReplyTraceScope
import ru.aigram.automation.proactive.ProactiveCoordinator
import ru.aigram.automation.proactive.ProactiveLedger
import ru.aigram.diagnostics.DiagnosticLog
import ru.aigram.resources.ResourceCatalog
import ru.aigram.storage.ActivityFeedbackStore
import ru.aigram.storage.AutomatedReplyStore
import ru.aigram.storage.ContextExclusionStore
import ru.aigram.storage.SettingsStore
import ru.aigram.telegram.TelegramGateway
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class QueueConfiguration {
    @Bean
    fun replyCoordinator(
        settings: SettingsStore,
        telegram: TelegramGateway,
        automated: AutomatedReplyStore,
        generator: PersonalReplyGenerator,
        tracing: ReplyTraceScope,
        diagnostics: DiagnosticLog,
        feedback: ActivityFeedbackStore,
        delivery: ReplyDeliveryPolicy,
        exclusions: ContextExclusionStore,
        history: ActivityHistoryStore,
    ): ReplyCoordinator = ReplyCoordinator(
        settings, telegram, automated, tracing, diagnostics, feedback, delivery, exclusions, history,
        generate = generator::reply,
    )

    @Bean
    fun proactiveCoordinator(
        settings: SettingsStore,
        telegram: TelegramGateway,
        replies: ReplyCoordinator,
        ledger: ProactiveLedger,
        clock: Clock,
        resources: ResourceCatalog,
        diagnostics: DiagnosticLog,
        generator: PersonalReplyGenerator,
    ): ProactiveCoordinator = ProactiveCoordinator(
        settings,
        telegram,
        replies,
        ledger,
        clock,
        resources,
        diagnostics,
        generator::initiate,
    )
}
