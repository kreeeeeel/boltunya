import type {components} from '@/shared/api/schema';

export type MessageDeletionResult = components['schemas']['MessageDeletionResult'];

export interface MessageDeletionDialogProps {
    readonly chatId: string;
    readonly messageId: string;
    readonly onClose: () => void;
    readonly onDeleted?: (result: MessageDeletionResult) => Promise<void>;
}
