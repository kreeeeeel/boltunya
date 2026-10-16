import {defaultRule} from '@/shared/api/client';
import type {Chat, Message} from '@/shared/api/client';

export const testChats: Chat[] = [
    {
        id: '41',
        title: 'Аня Смирнова',
        preview: 'Супер, тогда до завтра! ☀️',
        date: 1792064100,
        unread: 2,
        rule: {...defaultRule, enabled: true, style: 'friendly'},
    },
    {
        id: '42',
        title: 'Марк Волков',
        preview: 'Пришлю макеты чуть позже',
        date: 1792062300,
        unread: 0,
        rule: {...defaultRule, enabled: true, style: 'business'},
    },
    {
        id: '43',
        title: 'Лиза',
        preview: 'Ты видел новый сезон? 👀',
        date: 1792059300,
        unread: 1,
        rule: {...defaultRule, style: 'warm'},
    },
    {
        id: '44',
        title: 'Денис Козлов',
        preview: 'Спасибо, всё получилось!',
        date: 1792055700,
        unread: 0,
        rule: {...defaultRule, enabled: true, style: 'concise'},
    },
    {
        id: '45',
        title: 'Саша',
        preview: 'Давай на выходных выберемся',
        date: 1792048500,
        unread: 0,
        rule: {...defaultRule},
    },
    {
        id: '46',
        title: 'Мама',
        preview: 'Как твои дела? ❤️',
        date: 1792041300,
        unread: 0,
        rule: {...defaultRule, style: 'warm'},
    },
];

export function testHistory(chatId: string): Message[] {
    const first = chatId === '41';
    const texts = first
        ? [
              ['Привет! Как проходит твоя неделя?', false],
              ['Привет! Довольно насыщенно, но всё хорошо 🙂\nНаконец-то закончил большой проект.', true],
              ['Класс, поздравляю! 🎉\nМожет, завтра выберемся на кофе?', false],
              ['Спасибо! Звучит как отличный план.\nВо сколько тебе удобно?', true],
              ['Давай после двух? В нашей любимой кофейне на углу', false],
              ['Хорошая идея! Я пока не могу подтвердить время — напишу, как определюсь 🙂', true],
              ['Супер, тогда до завтра! ☀️', false],
          ]
        : [
              ['Привет! Как дела?', false],
              ['Привет, всё хорошо! А у тебя? 🙂', true],
              [testChats.find((chat) => chat.id === chatId)?.preview ?? '', false],
          ];
    return texts.map(([text, outgoing], index) => ({
        id: String(index + 1),
        chatId,
        text: String(text),
        outgoing: Boolean(outgoing),
        date: 1792062600 + index * 120,
        kind: 'text',
    }));
}
