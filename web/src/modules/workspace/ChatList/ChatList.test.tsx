import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {act, fireEvent, render, screen, waitFor} from '@testing-library/react';
import {describe, expect, it, vi} from 'vitest';

import {ChatList} from '@/modules/workspace/ChatList/ChatList';
import {testChats} from '@tests/fixtures/chats';
import ru from '@/shared/i18n/ru.json';

describe('Chat selection', () => {
    it('temporarily replaces the preview with Telegram activity without changing the last message', () => {
        const chat = {
            ...testChats[0],
            preview: 'Последнее сообщение',
            typingUsers: [{id: 'user:1', name: 'Аня', action: 'recordVoice' as const}],
        };
        const view = render(
            <NextIntlClientProvider locale="ru" messages={ru}>
                <ThemeProvider>
                    <ChatList chats={[chat]} selected={null} onSelect={vi.fn()} />
                </ThemeProvider>
            </NextIntlClientProvider>,
        );
        expect(screen.getByText('записывает голосовое…')).toBeVisible();
        expect(screen.queryByText('Последнее сообщение')).toBeNull();
        expect(chat.preview).toBe('Последнее сообщение');
        view.unmount();
    });
    it('loads contacts in pages and searches beyond the displayed page', async () => {
        const chats = Array.from({length: 95}, (_, index) => ({
            ...testChats[0],
            id: String(index),
            title: `Контакт ${index}`,
        }));
        const view = render(
            <NextIntlClientProvider locale="ru" messages={ru}>
                <ThemeProvider>
                    <ChatList chats={chats} selected={null} onSelect={vi.fn()} />
                </ThemeProvider>
            </NextIntlClientProvider>,
        );
        expect(view.container.querySelectorAll('[data-qa="chat-row"]')).toHaveLength(40);
        const scroll = view.container.querySelector('[data-qa="chat-list-scroll"]')!;
        Object.defineProperties(scroll, {
            scrollHeight: {configurable: true, value: 3040},
            clientHeight: {configurable: true, value: 500},
            scrollTop: {configurable: true, writable: true, value: 2540},
        });
        fireEvent.scroll(scroll);
        await waitFor(() => expect(view.container.querySelectorAll('[data-qa="chat-row"]')).toHaveLength(80));
        fireEvent.scroll(scroll);
        await waitFor(() => expect(view.container.querySelectorAll('[data-qa="chat-row"]')).toHaveLength(95));
        fireEvent.change(screen.getByRole('textbox', {name: ru.search}), {target: {value: 'Контакт 94'}});
        expect(await screen.findByRole('button', {name: 'Контакт 94'})).toBeInTheDocument();
        await waitFor(() => expect(view.container.querySelectorAll('[data-qa="chat-row"]')).toHaveLength(1));
        fireEvent.change(screen.getByRole('textbox', {name: ru.search}), {target: {value: ''}});
        await waitFor(() => expect(view.container.querySelectorAll('[data-qa="chat-row"]')).toHaveLength(40));
    });

    it('distinguishes outgoing previews and group authors from private incoming messages', () => {
        const view = render(
            <NextIntlClientProvider locale="ru" messages={ru}>
                <ThemeProvider>
                    <ChatList
                        chats={[
                            {...testChats[0], preview: 'Мой ответ', lastMessageOutgoing: true},
                            {
                                ...testChats[1],
                                preview: 'Личное сообщение',
                                lastMessageSender: 'Марк',
                                kind: 'private',
                            },
                            {
                                ...testChats[2],
                                preview: 'Сообщение группы',
                                lastMessageSender: 'Лиза',
                                kind: 'group',
                            },
                        ]}
                        selected={null}
                        onSelect={vi.fn()}
                    />
                </ThemeProvider>
            </NextIntlClientProvider>,
        );
        const previews = view.container.querySelectorAll('[data-qa="chat-preview"]');
        expect(previews[0]).toHaveTextContent('Вы: Мой ответ');
        expect(screen.getByText('Личное сообщение')).toBeInTheDocument();
        expect(previews[2]).toHaveTextContent('Лиза: Сообщение группы');
        expect(view.container.querySelectorAll('[data-qa="chat-preview-author"]')).toHaveLength(2);
        expect(screen.queryByText('Марк: Личное сообщение')).not.toBeInTheDocument();
    });

    it('shows loading placeholders without an incorrect empty state', () => {
        render(
            <NextIntlClientProvider locale="ru" messages={ru}>
                <ThemeProvider>
                    <ChatList chats={[]} selected={null} loading onSelect={vi.fn()} />
                </ThemeProvider>
            </NextIntlClientProvider>,
        );
        expect(screen.getByRole('status', {name: ru.loadingChats})).toBeInTheDocument();
        expect(screen.queryByText(ru.emptyChats)).not.toBeInTheDocument();
        expect(screen.getByRole('tab', {name: ru.all})).toHaveAttribute('aria-disabled', 'true');
    });
    it('selects a contact without showing automation checkboxes', () => {
        const onSelect = vi.fn();
        render(
            <NextIntlClientProvider locale="ru" messages={ru}>
                <ThemeProvider>
                    <ChatList chats={testChats} selected={null} onSelect={onSelect} />
                </ThemeProvider>
            </NextIntlClientProvider>,
        );
        fireEvent.click(screen.getByRole('button', {name: 'Лиза'}));
        expect(onSelect).toHaveBeenCalledExactlyOnceWith('43');
        expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
    });
    it('filters chats without changing automation settings', async () => {
        render(
            <NextIntlClientProvider locale="ru" messages={ru}>
                <ThemeProvider>
                    <ChatList chats={testChats} selected={null} onSelect={vi.fn()} />
                </ThemeProvider>
            </NextIntlClientProvider>,
        );
        fireEvent.change(screen.getByRole('textbox', {name: ru.search}), {target: {value: 'Лиза'}});
        expect(screen.getByText('Лиза')).toBeInTheDocument();
        await waitFor(() => expect(screen.queryByText('Марк Волков')).not.toBeInTheDocument());
    });

    it('debounces rapid input and shows an illustrated result only after the latest query settles', async () => {
        vi.useFakeTimers();
        try {
            const view = render(
                <NextIntlClientProvider locale="ru" messages={ru}>
                    <ThemeProvider>
                        <ChatList chats={testChats} selected={null} onSelect={vi.fn()} />
                    </ThemeProvider>
                </NextIntlClientProvider>,
            );
            const search = screen.getByRole('textbox', {name: ru.search});
            fireEvent.change(search, {target: {value: 'Лиза'}});
            await act(async () => {
                vi.advanceTimersByTime(200);
            });
            fireEvent.change(search, {target: {value: 'Никого с таким именем'}});
            await act(async () => {
                vi.advanceTimersByTime(249);
            });
            expect(screen.getByRole('button', {name: 'Марк Волков'})).toBeVisible();
            expect(view.container.querySelector('[data-qa="chat-list-skeleton"]')).toBeNull();
            await act(async () => {
                vi.advanceTimersByTime(1);
            });
            expect(screen.queryByRole('button', {name: 'Лиза'})).toBeNull();
            const empty = view.container.querySelector('[data-qa="chat-list-empty"]');
            expect(empty).toHaveTextContent(ru.noResults);
            expect(empty).toHaveTextContent(ru.chatSearchEmptyHint);
            expect(empty?.querySelector('img')).toHaveAttribute(
                'src',
                '/assets/illustrations/conversations.png',
            );
        } finally {
            vi.useRealTimers();
        }
    });
});
