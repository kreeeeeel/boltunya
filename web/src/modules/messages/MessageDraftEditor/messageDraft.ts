import type {MessageEntity, Text} from '@aigram/api-client';
import type {
    MessageEditDraft,
    MessageEditEntity,
    MessageEditFormat,
} from '@/modules/messages/MessageEditEditor/MessageEditEditor.types';

const editableFormats: MessageEditFormat[] = [
    'bold',
    'italic',
    'underline',
    'strike',
    'spoiler',
    'code',
    'pre',
    'precode',
    'texturl',
    'quote',
    'expandablequote',
];

export function editableMessage(message: Text): MessageEditDraft {
    return {
        text: message.text,
        entities: (message.entities ?? []).map((entity, index): MessageEditEntity => {
            const known = editableFormats.find((format) => format === entity.type);
            return {
                kind: known ?? 'existing',
                sourceIndex: known ? null : index,
                typeHint: known ? '' : entity.type,
                offset: entity.offset,
                length: entity.length,
                url: entity.url ?? '',
                language: entity.language ?? '',
            };
        }),
    };
}

export function outgoingMessage(draft: MessageEditDraft): Text {
    return {
        text: draft.text,
        entities: draft.entities.map(
            (entity): MessageEntity => ({
                offset: entity.offset,
                length: entity.length,
                type: entity.kind === 'existing' ? entity.typeHint : entity.kind,
                ...(entity.url ? {url: entity.url} : {}),
                ...(entity.language ? {language: entity.language} : {}),
            }),
        ),
    };
}
