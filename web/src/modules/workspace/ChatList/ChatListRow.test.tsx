import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {render, screen} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {ChatListRow} from '@/modules/workspace/ChatList/ChatListRow';
import {workspaceState} from '@tests/fixtures/workspace';
import ru from '@/shared/i18n/ru.json';

it.each([false, true])('shows the Telegram read state for the last outgoing message: %s', (read) => {
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ChatListRow
                    chat={{...workspaceState.chats[0], lastMessageOutgoing: true, lastMessageRead: read}}
                    selected={false}
                    timeFormat={new Intl.DateTimeFormat('ru', {hour: '2-digit', minute: '2-digit'})}
                    onSelect={vi.fn()}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    expect(
        screen.getByRole('img', {name: read ? ru.assistantBar.messageRead : ru.assistantBar.messageSent}),
    ).toBeVisible();
});

it('does not show an outgoing read receipt for an incoming message', () => {
    const view = render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ChatListRow
                    chat={{...workspaceState.chats[0], lastMessageOutgoing: false, lastMessageRead: true}}
                    selected={false}
                    timeFormat={new Intl.DateTimeFormat('ru', {hour: '2-digit', minute: '2-digit'})}
                    onSelect={vi.fn()}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    expect(view.container.querySelector('[data-qa="chat-read-status"]')).toBeNull();
});
