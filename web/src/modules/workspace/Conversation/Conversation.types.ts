import type {Chat, Message} from '@/shared/api/client';

export interface ConversationProps {
    readonly chat: Chat | undefined;
    readonly messages: Message[];
    readonly generating: boolean;
    readonly error: string;
    readonly loading?: boolean;
    readonly onConfigure: () => void;
    readonly onInspector: () => void;
    readonly onMemory: () => void;
    readonly onSend: (text: string) => Promise<void>;
    readonly onDraft: () => Promise<string>;
    readonly onBack: () => void;
    readonly onEarlier: () => Promise<boolean>;
}
