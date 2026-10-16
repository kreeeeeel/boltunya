import {expect, it} from 'vitest';
import type {Message} from '@/shared/api/client';
import {messagesGrouped} from '@/modules/workspace/model/messagesGrouped';

const message: Message = {
    id: '1',
    chatId: '41',
    text: 'Hello',
    kind: 'text',
    date: 1792060000,
    outgoing: false,
    group: true,
    senderId: 'user:10',
    senderName: 'Anna',
};

it('groups only adjacent messages from the same group author, direction, topic and day', () => {
    expect(messagesGrouped(message, {...message, date: message.date + 30})).toBe(true);
    expect(messagesGrouped(message, {...message, senderId: 'user:11'})).toBe(false);
    expect(messagesGrouped(message, {...message, outgoing: true})).toBe(false);
    expect(messagesGrouped(message, {...message, topicId: 2})).toBe(false);
    expect(messagesGrouped(message, {...message, date: message.date + 300})).toBe(false);
    const midnight = Date.UTC(2026, 9, 15, 21) / 1000;
    expect(messagesGrouped({...message, date: midnight - 1}, {...message, date: midnight + 1})).toBe(false);
    expect(messagesGrouped(message)).toBe(false);
});

it('uses names for old history and avoids combining unknown authors', () => {
    expect(messagesGrouped({...message, senderId: ''}, {...message, senderId: ''})).toBe(true);
    expect(
        messagesGrouped(
            {...message, senderId: '', senderName: ''},
            {...message, senderId: '', senderName: ''},
        ),
    ).toBe(false);
    expect(messagesGrouped({...message, outgoing: true}, {...message, outgoing: true, senderId: ''})).toBe(
        true,
    );
});
