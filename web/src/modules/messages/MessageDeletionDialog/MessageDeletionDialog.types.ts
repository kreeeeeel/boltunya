import type * as Contract from '@aigram/api-client';
export type MessageDeletionResult = Contract.MessageDeletionResult;
export interface MessageDeletionDialogProps {
    readonly chatId: string;
    readonly messageId: string;
    readonly onClose: () => void;
    readonly onDeleted?: (result: MessageDeletionResult) => Promise<void>;
}
