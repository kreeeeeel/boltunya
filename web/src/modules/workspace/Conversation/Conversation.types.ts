import type {Activity, Chat, Message, MessageEntity, Reply} from '@/shared/api/client';
import type {ContextExclusionChange} from '@/modules/messages/model/ContextExclusionChange';
import type {MessageDeletionResult} from '@/modules/messages/MessageDeletionDialog/MessageDeletionDialog.types';

export interface ConversationProps {
    readonly chat: Chat | undefined;
    readonly modelLabel?: string;
    readonly onMediaSent?: () => Promise<void>;
    readonly onContextChanged?: (change: ContextExclusionChange) => Promise<void>;
    readonly onMessageDeleted?: (result: MessageDeletionResult) => Promise<void>;
    readonly onMessageUpdated?: (message: Message) => void;
    readonly messages: Message[];
    readonly activeReply?: Activity;
    readonly onRefresh: () => Promise<void>;
    readonly error: string;
    readonly loading?: boolean;
    readonly onConfigure: () => void;
    readonly onInspector: () => void;
    readonly onMemory: () => void;
    readonly onSend: (text: string, entities?: MessageEntity[]) => Promise<void>;
    readonly onDraft: (messageId?: string) => Promise<Reply>;
    readonly onBack: () => void;
    readonly onEarlier: () => Promise<boolean>;
}
