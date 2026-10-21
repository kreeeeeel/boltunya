import type {
    ModelProfile,
    ModelProfileUpdate,
    ModelProviderPreset,
} from '@/modules/settings/model/useModelProfiles';
export function profileDraft(profile: ModelProfile | ModelProviderPreset): ModelProfileUpdate {
    if ('options' in profile) {
        return {
            name: profile.name,
            provider: profile.provider,
            preset: profile.preset,
            baseUrl: profile.baseUrl,
            model: profile.model,
            enabled: profile.enabled,
            options: {...profile.options},
            capabilities: {...profile.capabilities},
            clearApiKey: false,
        };
    }
    return {
        name: profile.name,
        provider: profile.provider,
        preset: profile.id as ModelProfileUpdate['preset'],
        baseUrl: profile.baseUrl,
        model: profile.model,
        enabled: true,
        options: {...profile.defaultsOptions},
        capabilities: {...profile.defaultCapabilities},
        clearApiKey: false,
    };
}
export function profilePayload(value: ModelProfileUpdate, key: string): ModelProfileUpdate {
    const draft = {...value};
    delete draft.apiKey;
    return {...draft, ...(key.trim() ? {apiKey: key.trim(), clearApiKey: false} : {})};
}
