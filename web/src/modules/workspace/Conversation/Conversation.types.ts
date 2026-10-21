import type {Activity, Chat, Message} from '@/shared/api/client';
import type {ContextExclusionChange} from '@/modules/messages/model/ContextExclusionChange';
import type {MessageDeletionResult} from '@/modules/messages/MessageDeletionDialog/MessageDeletionDialog.types';

export interface ConversationProps {
    readonly chat: Chat | undefined;
    readonly modelLabel?: string;
    readonly onMediaSent?: () => Promise<void>;
    readonly onContextChanged?: (change: ContextExclusionChange) => Promise<void>;
    readonly onMessageDeleted?: (result: MessageDeletionResult) => Promise<void>;
    readonly messages: Message[];
    readonly activeReply?: Activity;
    readonly onRefresh: () => Promise<void>;
    readonly error: string;
    readonly loading?: boolean;
    readonly onConfigure: () => void;
    readonly onInspector: () => void;
    readonly onMemory: () => void;
    readonly onSend: (text: string) => Promise<void>;
    readonly onDraft: (messageId?: string) => Promise<string>;
    readonly onBack: () => void;
    readonly onEarlier: () => Promise<boolean>;
}
