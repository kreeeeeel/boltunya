import {expect, it} from 'vitest';
import {
    editDraftFingerprint,
    messageEditDraft,
    messageEditLength,
} from '@/modules/messages/MessageEditDialog/messageEditDraft';

it('counts Telegram characters by code point while retaining UTF16 entity offsets', () => {
    expect(messageEditLength('🙂🙂🙂🙂')).toBe(4);
    expect('🙂🙂🙂🙂'.length).toBe(8);
});

it('does not mark the draft dirty when the editor emits identical entity data in a different property order', () => {
    const initial = messageEditDraft({
        text: 'Bold',
        entities: [{offset: 0, length: 4, kind: 'existing', sourceIndex: 2, typeHint: 'bold'}],
        revision: 'a'.repeat(64),
        kind: 'text',
        maxLength: 4096,
    });
    const entity = initial.entities[0];
    const emitted = {
        text: initial.text,
        entities: [
            {
                kind: entity.kind,
                sourceIndex: entity.sourceIndex,
                typeHint: entity.typeHint,
                url: entity.url,
                language: entity.language,
                offset: entity.offset,
                length: entity.length,
            },
        ],
    };

    expect(JSON.stringify(initial)).not.toBe(JSON.stringify(emitted));
    expect(editDraftFingerprint(emitted)).toBe(editDraftFingerprint(initial));
});

it('normalizes optional display fields without losing original opaque entity references or whitespace', () => {
    const draft = messageEditDraft({
        text: ' 🙂 User ',
        entities: [
            {kind: 'existing', offset: 4, length: 4, sourceIndex: 7, typeHint: 'mentionname'},
            {kind: 'bold', offset: 4, length: 4},
        ],
        revision: 'a'.repeat(64),
        kind: 'text',
        maxLength: 4096,
    });

    expect(draft.text).toBe(' 🙂 User ');
    expect(draft.entities[0]).toEqual({
        kind: 'existing',
        offset: 4,
        length: 4,
        sourceIndex: 7,
        typeHint: 'mentionname',
        url: '',
        language: '',
    });
    expect(editDraftFingerprint({...draft, entities: [...draft.entities].reverse()})).toBe(
        editDraftFingerprint(draft),
    );
});
