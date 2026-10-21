import {useCallback, useEffect, useState} from 'react';
import {api, errorMessage} from '@/shared/api/client';
import type * as Contract from '@aigram/api-client';
export type ModelProfile = Contract.ModelProfile;
export type ModelProfileUpdate = Contract.ModelProfileUpdate;
export type ModelProviderPreset = Contract.ModelProviderPreset;
export function useModelProfiles() {
    const [profiles, setProfiles] = useState<ModelProfile[]>([]);
    const [presets, setPresets] = useState<ModelProviderPreset[]>([]);
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(true);
    const refresh = useCallback(async () => {
        try {
            const report = await api.models.modelProfiles();
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
