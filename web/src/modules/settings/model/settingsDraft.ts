import type {AppState} from '@/shared/api/client';

export function createSettingsDraft(settings: AppState['settings']) {
    return {
        replyDelaySeconds: settings.replyDelaySeconds ?? 3,
        replyIntervalSeconds: settings.replyIntervalSeconds ?? 3,
        showTyping: settings.showTyping ?? true,
        ignoreAssistantTasks: settings.ignoreAssistantTasks ?? true,
        memoryEnabled: settings.memoryEnabled ?? true,
        memoryIntervalHours: settings.memoryIntervalHours ?? 6,
        memoryCollectionScope: settings.memoryCollectionScope ?? 'ai-enabled',
        proactiveEnabled: settings.proactiveEnabled ?? false,
        proactiveIntervalMinutes: settings.proactiveIntervalMinutes ?? 60,
        autoDownloadPhotos: settings.autoDownloadPhotos ?? true,
        autoDownloadVideos: settings.autoDownloadVideos ?? false,
        autoDownloadAudio: settings.autoDownloadAudio ?? false,
        autoDownloadFiles: settings.autoDownloadFiles ?? false,
        autoDownloadMaxMb: settings.autoDownloadMaxMb ?? 20,
        groupReplies: settings.groupReplies ?? 'disabled',
        closeToTray: settings.closeToTray ?? true,
        launchAtLogin: settings.launchAtLogin ?? false,
        resumeOnStartup: settings.resumeOnStartup ?? false,
        ollamaDevice: settings.ollamaDevice ?? 'auto',
        globalPrompt: settings.globalPrompt ?? '',
        customStyles: settings.customStyles ?? [],
        deliveryMode: settings.deliveryMode ?? 'reply',
    };
}

export type SettingsDraft = ReturnType<typeof createSettingsDraft>;
