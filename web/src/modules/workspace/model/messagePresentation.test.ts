import {describe, expect, it} from 'vitest';
import {isEmojiMessage, messageDay} from '@/modules/workspace/model/messagePresentation';

describe('Telegram message presentation', () => {
    it('recognizes small emoji-only messages, including joined families and flags', () => {
        for (const text of ['🙂', '😂😂', '👨‍👩‍👧', '🇰🇿', '1️⃣', '👍🏽']) {
            expect(isEmojiMessage(text)).toBe(true);
        }
        for (const text of ['', '12', 'Привет 🙂', '😂😂😂😂']) {
            expect(isEmojiMessage(text)).toBe(false);
        }
    });
    it('groups dates using the application time zone', () => {
        expect(messageDay(Date.UTC(2026, 9, 15, 22) / 1000)).toBe('2026-10-16');
    });
});
