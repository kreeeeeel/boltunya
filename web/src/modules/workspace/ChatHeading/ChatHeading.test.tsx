import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {NextIntlClientProvider} from 'next-intl';
import {ThemeProvider} from '@gravity-ui/uikit';
import {expect, it, vi} from 'vitest';
import {ChatHeading} from '@/modules/workspace/ChatHeading/ChatHeading';
import ru from '@/shared/i18n/ru.json';
import type {Chat} from '@/shared/api/client';
import {defaultRule} from '@/shared/api/client';

function mount(values: Partial<Chat>) {
    const chat: Chat = {
        id: '42',
        title: 'Аня',
        preview: '',
        date: 0,
        unread: 0,
        rule: defaultRule,
        ...values,
    };
    return render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ChatHeading chat={chat} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
}

it('shows the real private username below the title and omits an absent username', () => {
    const view = mount({kind: 'private', username: 'anya'});
    expect(screen.getByText('@anya')).toBeVisible();
    view.unmount();
    mount({kind: 'private', username: ''});
    expect(screen.queryByText((text) => text.startsWith('@'))).toBeNull();
});

it('copies the username and Telegram link and reports clipboard failures', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {configurable: true, value: {writeText}});
    mount({kind: 'private', username: 'anya'});
    fireEvent.click(screen.getByRole('button', {name: ru.chatCopy.username}));
    await waitFor(() => expect(writeText).toHaveBeenCalledWith('@anya'));
    fireEvent.click(screen.getByRole('button', {name: ru.chatCopy.link}));
    await waitFor(() => expect(writeText).toHaveBeenCalledWith('https://t.me/anya'));
    writeText.mockRejectedValue(new Error('Clipboard unavailable'));
    fireEvent.click(screen.getByRole('button', {name: ru.chatCopy.link}));
    expect(await screen.findByRole('alert')).toHaveTextContent(ru.copyError);
});

it.each([
    [21, '21 участник'],
    [22, '22 участника'],
    [25, '25 участников'],
])('formats a group with %s members', (count, label) => {
    mount({kind: 'supergroup', username: 'ignored', memberCount: Number(count)});
    expect(screen.getByText(String(label))).toBeVisible();
    expect(screen.queryByText('@ignored')).toBeNull();
});

it('does not invent a member count when Telegram has not provided it', () => {
    mount({kind: 'group', memberCount: null});
    expect(screen.getByText('Группа')).toBeVisible();
    expect(screen.queryByText('0 участников')).toBeNull();
});

it('shows incoming typing activity in the status line and restores the Telegram username afterwards', () => {
    const view = mount({
        kind: 'private',
        username: 'anya',
        typingUsers: [{id: 'user:1', name: 'Аня', action: 'typing'}],
    });
    expect(screen.getByRole('status')).toHaveTextContent('печатает…');
    expect(screen.queryByText('@anya')).toBeNull();
    view.unmount();
    mount({kind: 'private', username: 'anya', typingUsers: []});
    expect(screen.getByText('@anya')).toBeVisible();
});

it('names active group participants and distinguishes uploads from typing', () => {
    mount({
        kind: 'supergroup',
        typingUsers: [
            {id: 'user:1', name: 'Аня', action: 'uploadPhoto'},
            {id: 'user:2', name: 'Марк', action: 'uploadPhoto'},
        ],
    });
    expect(screen.getByRole('status')).toHaveTextContent('Аня и Марк отправляют фото…');
});
