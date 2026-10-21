import {expect, it} from 'vitest';
import {editableMessage, outgoingMessage} from '@/modules/messages/MessageDraftEditor/messageDraft';

it('round trips Telegram formatting with exact UTF16 offsets and link metadata', () => {
    const message = {
        text: '🙂 Привет, ссылка',
        entities: [
            {offset: 3, length: 6, type: 'bold'},
            {offset: 11, length: 6, type: 'texturl', url: 'https://example.com'},
        ],
    };
    const editable = editableMessage(message);
    expect(editable.entities[0]).toMatchObject({kind: 'bold', offset: 3, sourceIndex: null});
    expect(outgoingMessage(editable)).toEqual(message);
});

it('retains opaque known display entity metadata without pretending it is a new format', () => {
    const message = {text: '@name', entities: [{offset: 0, length: 5, type: 'mention'}]};
    expect(editableMessage(message).entities[0]).toMatchObject({
        kind: 'existing',
        sourceIndex: 0,
        typeHint: 'mention',
    });
    expect(outgoingMessage(editableMessage(message))).toEqual(message);
});

it('forwards the editor recalculated ranges and preserves exact whitespace', () => {
    const editable = editableMessage({text: '  Word  ', entities: [{offset: 2, length: 4, type: 'italic'}]});
    expect(
        outgoingMessage({
            ...editable,
            text: '🙂   Word  ',
            entities: editable.entities.map((entity) => ({...entity, offset: 5})),
        }),
    ).toEqual({
        text: '🙂   Word  ',
        entities: [{offset: 5, length: 4, type: 'italic'}],
    });
});
