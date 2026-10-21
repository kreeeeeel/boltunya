import type {Chat, ChatRule, Engine, AppState} from '@/shared/api/client';

export interface RuleDialogProps {
    readonly chat: Chat;
    readonly customStyles: NonNullable<AppState['settings']['customStyles']>;
    readonly engine: Engine | undefined;
    readonly defaultModel: string;
    readonly defaultModelProfileId?: string;
    readonly onClose: () => void;
    readonly onCollectContext: () => void;
    readonly onDeleted: () => void;
    readonly onSave: (rule: ChatRule) => Promise<void>;
}
