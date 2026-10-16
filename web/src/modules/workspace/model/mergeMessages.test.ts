import {describe, expect, it} from 'vitest';

import {mergeMessages} from '@/modules/workspace/model/mergeMessages';
import type {Message} from '@/shared/api/client';

describe('History pagination', () => {
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
});
