import {describe, expect, it} from 'vitest';

import {mergeMessages, mergeRecentMessages} from '@/modules/workspace/model/mergeMessages';
import type {Message} from '@/shared/api/client';

describe('History pagination', () => {
    it('retains newer confirmed edit content when an older snapshot arrives while refreshing read flags', () => {
        const updated: Message = {
            id: '123',
            chatId: '42',
            date: 1,
            outgoing: true,
            kind: 'text',
            text: 'Edited',
            entities: [{offset: 0, length: 6, type: 'bold'}],
            editedAt: 100,
        };
        const stale = {...updated, text: 'Original', entities: [], editedAt: 99, read: true};

        expect(mergeRecentMessages([updated], [stale])[0]).toEqual({...updated, read: true});
        expect(mergeMessages([updated], [{...stale, text: 'Newer edit', editedAt: 101}])[0].text).toBe(
            'Newer edit',
        );
    });

    it('keeps earlier pages, replaces edits and orders large Telegram identifiers safely', () => {
        const message = (id: string, text: string): Message => ({
            id,
            text,
            chatId: '1',
            date: 1,
            outgoing: false,
            kind: 'text',
        });
        const result = mergeMessages(
            [message('90071992547409920', 'old'), message('90071992547409921', 'original')],
            [message('90071992547409921', 'edited'), message('90071992547409922', 'new')],
        );
        expect(result.map((item) => item.text)).toEqual(['old', 'edited', 'new']);
    });

    it('replaces a temporary sent message with its server identifier without losing older pages', () => {
        const message = (id: string, text: string, outgoing = false): Message => ({
            id,
            text,
            chatId: '1',
            date: 1,
            outgoing,
            kind: 'text',
        });
        const earlier = message('100', 'Earlier page');
        const incoming = message('200', 'Hi');
        const pending = message('90071992547409920', 'Reply', true);
        const confirmed = message('300', 'Reply', true);
        expect(mergeRecentMessages([earlier, incoming, pending], [incoming, confirmed])).toEqual([
            earlier,
            incoming,
            confirmed,
        ]);
    });

    it('keeps intentionally repeated text and replaces edits by identifier', () => {
        const message = (id: string, text: string): Message => ({
            id,
            text,
            chatId: '1',
            date: 1,
            outgoing: true,
            kind: 'text',
        });
        const first = message('100', 'Same text');
        const second = message('200', 'Same text');
        expect(mergeRecentMessages([first, second], [first, second])).toHaveLength(2);
        expect(mergeRecentMessages([first, second], [first, {...second, text: 'Edited'}])[1].text).toBe(
            'Edited',
        );
        expect(mergeRecentMessages([first, second], [])).toEqual([first, second]);
    });

    it('never carries history from a different chat into a fresh snapshot', () => {
        const message: Message = {
            id: '1',
            chatId: '1',
            text: 'Hello',
            date: 1,
            outgoing: false,
            kind: 'text',
        };
        expect(mergeRecentMessages([message], [{...message, chatId: '2'}])).toEqual([
            {...message, chatId: '2'},
        ]);
    });

    it('keeps confirmed deletion metadata when an older in-flight snapshot arrives', () => {
        const message: Message = {
            id: '123',
            chatId: '1',
            text: 'Retained text',
            date: 1,
            outgoing: true,
            kind: 'text',
        };
        const deleted = {...message, deleted: true, deletedAt: 100, contextExcluded: true};
        const result = mergeRecentMessages([deleted], [{...message, contextExcluded: false}]);
        expect(result).toEqual([{...message, deleted: true, deletedAt: 100, contextExcluded: false}]);
        expect(mergeMessages([deleted], [message])[0].deletedAt).toBe(100);
        expect(mergeRecentMessages([deleted], [{...message, id: '124'}]).map((item) => item.id)).toEqual([
            '123',
            '124',
        ]);
    });
});
