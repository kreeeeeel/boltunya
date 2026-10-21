import {expect, it} from 'vitest';
import type {Message} from '@/shared/api/client';
import {applyUpdatedMessage} from '@/modules/workspace/model/applyUpdatedMessage';

const original: Message = {
    id: '90071992547409921',
    chatId: '42',
    date: 1,
    outgoing: true,
    kind: 'photo',
    text: 'Old caption',
    automated: true,
    contextExcluded: true,
};

it('updates an old loaded message by chat and opaque identifier while retaining local markers', () => {
    const other = {...original, chatId: '43'};
    const updated = {...original};
    delete updated.automated;
    delete updated.contextExcluded;
    const result = applyUpdatedMessage([original, other], {
        ...updated,
        text: ' New caption ',
        entities: [{offset: 1, length: 3, type: 'bold'}],
        editedAt: 100,
    });

    expect(result[0]).toMatchObject({
        text: ' New caption ',
        automated: true,
        contextExcluded: true,
        editedAt: 100,
    });
    expect(result[1]).toBe(other);
});

it('does not revive a concurrently deleted message and accepts explicit current marker values', () => {
    const result = applyUpdatedMessage([{...original, deleted: true, deletedAt: 99}], {
        ...original,
        text: 'Edited',
        deleted: false,
        contextExcluded: false,
        editedAt: 100,
    });

    expect(result[0]).toMatchObject({
        text: 'Edited',
        deleted: true,
        deletedAt: 99,
        automated: true,
        contextExcluded: false,
    });
    expect(applyUpdatedMessage([], original)).toEqual([]);
});
