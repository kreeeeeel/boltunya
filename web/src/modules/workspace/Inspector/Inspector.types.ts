import type {Chat, AppState} from '@/shared/api/client';

export interface InspectorProps {
    readonly chat: Chat;
    readonly modelLabel?: string;
    readonly customStyles: NonNullable<AppState['settings']['customStyles']>;
    readonly onConfigure: () => void;
    readonly onEnable: (enabled: boolean) => void;
    readonly onClose: () => void;
    readonly onCollectContext: () => void;
    readonly onDeleted: () => void;
}
