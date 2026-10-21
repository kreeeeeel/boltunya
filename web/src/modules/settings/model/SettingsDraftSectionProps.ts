import type {SettingsDraft} from '@/modules/settings/model/settingsDraft';

export interface SettingsDraftSectionProps {
    readonly value: SettingsDraft;
    readonly onUpdate: (value: SettingsDraft) => void;
}
