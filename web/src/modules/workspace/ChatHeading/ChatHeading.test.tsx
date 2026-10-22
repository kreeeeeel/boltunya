import {render, screen} from '@testing-library/react';
import {NextIntlClientProvider} from 'next-intl';
import {ThemeProvider} from '@gravity-ui/uikit';
import {expect, it} from 'vitest';
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

it('keeps the private header focused on the participant name without a username or copy controls', () => {
    const view = mount({kind: 'private', username: 'anya'});
    expect(screen.getByText('Аня')).toBeVisible();
    expect(screen.queryByText('@anya')).toBeNull();
    expect(screen.queryByRole('button', {name: ru.chatCopy.username})).toBeNull();
    view.unmount();
    mount({kind: 'private', username: ''});
    expect(screen.queryByText((text) => text.startsWith('@'))).toBeNull();
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

it('shows incoming typing activity and restores the compact name-only header afterwards', () => {
    const view = mount({
        kind: 'private',
        username: 'anya',
        typingUsers: [{id: 'user:1', name: 'Аня', action: 'typing'}],
    });
    expect(screen.getByRole('status')).toHaveTextContent('печатает…');
    expect(screen.queryByText('@anya')).toBeNull();
    view.unmount();
    mount({kind: 'private', username: 'anya', typingUsers: []});
    expect(screen.queryByText('@anya')).toBeNull();
    expect(screen.getByText('Аня')).toBeVisible();
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
