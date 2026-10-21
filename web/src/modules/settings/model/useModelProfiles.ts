import {useCallback, useEffect, useState} from 'react';
import {api, dataOrThrow, errorMessage} from '@/shared/api/client';
import type {components} from '@/shared/api/schema';
export type ModelProfile = components['schemas']['ModelProfile'];
export type ModelProfileUpdate = components['schemas']['ModelProfileUpdate'];
export type ModelProviderPreset = components['schemas']['ModelProviderPreset'];
export function useModelProfiles() {
    const [profiles, setProfiles] = useState<ModelProfile[]>([]);
    const [presets, setPresets] = useState<ModelProviderPreset[]>([]);
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(true);
    const refresh = useCallback(async () => {
        try {
            const report = dataOrThrow(await api.GET('/api/model-profiles'));
            setProfiles(report.profiles);
            setPresets(report.presets);
            setError('');
        } catch (cause) {
            setError(errorMessage(cause));
        } finally {
            setLoading(false);
        }
    }, []);
    useEffect(() => {
        queueMicrotask(() => void refresh());
        const changed = () => void refresh();
        window.addEventListener('aigram-model-profiles-changed', changed);
        return () => window.removeEventListener('aigram-model-profiles-changed', changed);
    }, [refresh]);
    return {profiles, presets, error, loading, refresh};
}
