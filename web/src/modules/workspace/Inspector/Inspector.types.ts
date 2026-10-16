import type {Chat} from '@/shared/api/client';

export interface InspectorProps {
    readonly chat: Chat;
    readonly onConfigure: () => void;
    readonly onEnable: (enabled: boolean) => void;
    readonly onClose: () => void;
    readonly onCollectContext: () => void;
    readonly onDeleted: () => void;
}
