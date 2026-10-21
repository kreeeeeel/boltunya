import type {MessageEditEntity as ContractMessageEditEntity, MessageEditRequest} from '@aigram/api-client';

export type MessageEditFormat = Exclude<ContractMessageEditEntity['kind'], 'existing'>;
export type MessageEditEntity = Required<ContractMessageEditEntity>;
export type MessageEditDraft = Pick<MessageEditRequest, 'text'> & {entities: MessageEditEntity[]};

export interface MessageEditEditorProps {
    initialDraft: MessageEditDraft;
    draftId: string;
    onChange: (draft: MessageEditDraft) => void;
    onError: (message: string) => void;
    ariaLabel: string;
    disabled?: boolean;
    autoFocus?: boolean;
    compact?: boolean;
}

export type MessageEditMark = Omit<MessageEditEntity, 'offset' | 'length'> & {entityId: string};
