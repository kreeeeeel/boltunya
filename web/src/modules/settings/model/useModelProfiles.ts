import {useSyncExternalStore} from 'react';
import type * as Contract from '@aigram/api-client';
import {modelProfilesStore} from '@/modules/settings/model/modelProfilesStore';

export type ModelProfile = Contract.ModelProfile;
export type ModelProfileUpdate = Contract.ModelProfileUpdate;
export type ModelProviderPreset = Contract.ModelProviderPreset;

export function useModelProfiles() {
    const snapshot = useSyncExternalStore(
        modelProfilesStore.subscribe,
        modelProfilesStore.getSnapshot,
        modelProfilesStore.getServerSnapshot,
    );

    return {...snapshot, refresh: modelProfilesStore.refresh};
}
