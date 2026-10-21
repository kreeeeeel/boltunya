import {expect, it} from 'vitest';
import type {Message} from '@/shared/api/client';
import {applyContextExclusion} from '@/modules/messages/model/applyContextExclusion';
const messages = ['1000000000000000000', '1000000000000000001', '1000000000000000002'].map(
    (id) => ({id, chatId: '42', text: 'text', kind: 'text', date: 1, outgoing: false}) as Message,
);
it('updates all paginated messages with exact integer precision and preserves other chats', () => {
    const other = {...messages[0], chatId: '43'};
    const result = applyContextExclusion([...messages, other], '42', {
        scope: 'through',
        messageId: messages[1].id,
        excluded: true,
    });
    expect(result.map((message) => !!message.contextExcluded)).toEqual([true, true, false, false]);
    expect(result[3]).toBe(other);
    expect(messages[0].contextExcluded).toBeUndefined();
    expect(
        applyContextExclusion(result, '42', {scope: 'from', messageId: messages[1].id, excluded: false}).map(
            (message) => !!message.contextExcluded,
        ),
    ).toEqual([true, false, false, false]);
    expect(
        applyContextExclusion(result, '42', {reset: true}).every((message) => !message.contextExcluded),
    ).toBe(true);
});
