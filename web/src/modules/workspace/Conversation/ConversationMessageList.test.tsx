import {ThemeProvider, Text} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {render} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import type {Message} from '@/shared/api/client';
import {ConversationMessageList} from '@/modules/workspace/Conversation/ConversationMessageList';
import ru from '@/shared/i18n/ru.json';

const mocks = vi.hoisted(() => ({text: vi.fn()}));
vi.mock('@/modules/workspace/MessageMedia/MessageMedia', () => ({MessageMedia: () => null}));
vi.mock('@/modules/workspace/RichText/RichText', () => ({
    RichText: ({text}: {text: string}) => {
        mocks.text(text);
        return <Text>{text}</Text>;
    },
}));

it('does not rerender history on unrelated parent updates and limits an edit to the message and its grouping neighbours', () => {
    const messages: Message[] = Array.from({length: 200}, (_, index) => ({
        id: String(index + 1),
        chatId: '42',
        outgoing: index % 2 === 0,
        text: 'Synthetic message ' + index,
        date: 1792064000 + index,
        kind: 'text',
    }));
    const menu = vi.fn();
    const draft = vi.fn().mockResolvedValue(undefined);
    function renderList(value: Message[]) {
        return (
            <NextIntlClientProvider locale="ru" messages={ru}>
                <ThemeProvider>
                    <ConversationMessageList
                        messages={value}
                        group={false}
                        today="2026-10-15"
                        onMenuOpenChange={menu}
                        onDraftRequest={draft}
                    />
                </ThemeProvider>
            </NextIntlClientProvider>
        );
    }
    const view = render(renderList(messages));
    expect(mocks.text).toHaveBeenCalledTimes(200);
    view.rerender(renderList(messages));
    expect(mocks.text).toHaveBeenCalledTimes(200);
    const updated = messages.map((message) =>
        message.id === '100' ? {...message, text: 'Edited synthetic message'} : message,
    );
    view.rerender(renderList(updated));
    expect(mocks.text).toHaveBeenCalledTimes(203);
    expect(mocks.text.mock.calls.slice(200).map(([text]) => text)).toEqual([
        'Synthetic message 98',
        'Edited synthetic message',
        'Synthetic message 100',
    ]);
});
