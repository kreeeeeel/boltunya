import {describe, expect, it} from 'vitest';

import {inlineSegments, prepareEntities, safeLink} from '@/modules/workspace/RichText/formatMessage';

describe('Telegram message formatting', () => {
    it('keeps UTF16 offsets and overlapping inline styles after emoji', () => {
        const text = '🙂 Привет';
        const segments = inlineSegments(text, [
            {offset: 3, length: 6, type: 'bold'},
            {offset: 4, length: 3, type: 'italic'},
        ]);
        expect(segments.map((segment) => segment.text).join('')).toBe(text);
        expect(
            segments.find((segment) => segment.text === 'рив')?.entities.map((entity) => entity.type),
        ).toEqual(['bold', 'italic']);
    });
    it('detects plain URLs without linking code or duplicating Telegram links', () => {
        const text = 'https://example.com';
        expect(prepareEntities(text)).toHaveLength(1);
        expect(prepareEntities(text, [{offset: 0, length: text.length, type: 'code'}])).toHaveLength(1);
        expect(prepareEntities(text, [{offset: 0, length: text.length, type: 'url'}])).toHaveLength(1);
    });
    it('rejects executable URL schemes and supports Telegram links', () => {
        expect(safeLink('javascript:alert(1)')).toBeUndefined();
        expect(safeLink('data:text/html,test')).toBeUndefined();
        expect(safeLink('t.me/example')).toBe('https://t.me/example');
        expect(safeLink('https://example.com')).toBe('https://example.com/');
    });
});
