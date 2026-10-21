import type {ModelProfile, ModelProfileUpdate} from '@/modules/settings/model/useModelProfiles';

export type ModelProfileDraft = Omit<ModelProfile, 'id' | 'apiKeyConfigured'> &
    Required<Pick<ModelProfileUpdate, 'clearApiKey'>>;
