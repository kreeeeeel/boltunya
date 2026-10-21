import type {MessageEditOptions} from '@aigram/api-client';
import type {MessageEditDraft} from '@/modules/messages/MessageEditEditor/MessageEditEditor.types';

export function messageEditLength(text: string): number {
    return Array.from(text).length;
}

export function messageEditDraft(options: MessageEditOptions): MessageEditDraft {
    return {
        text: options.text,
        entities: options.entities.map((entity) => ({
            ...entity,
            sourceIndex: entity.sourceIndex ?? null,
            typeHint: entity.typeHint ?? '',
            url: entity.url ?? '',
            language: entity.language ?? '',
        })),
    };
}

export function editDraftFingerprint(draft: MessageEditDraft): string {
    return JSON.stringify({
        text: draft.text,
        entities: draft.entities
            .map((entity) => ({
                offset: entity.offset,
                length: entity.length,
                kind: entity.kind,
                sourceIndex: entity.sourceIndex,
                typeHint: entity.typeHint,
                url: entity.url,
                language: entity.language,
            }))
            .sort(
                (left, right) =>
                    left.offset - right.offset ||
                    left.length - right.length ||
                    left.kind.localeCompare(right.kind) ||
                    (left.sourceIndex ?? -1) - (right.sourceIndex ?? -1) ||
                    left.typeHint.localeCompare(right.typeHint) ||
                    left.url.localeCompare(right.url) ||
                    left.language.localeCompare(right.language),
            ),
    });
}
