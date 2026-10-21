import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {MessageBubble} from '@/modules/workspace/Conversation/MessageBubble';
import type {Message} from '@/shared/api/client';
import ru from '@/shared/i18n/ru.json';

vi.mock('@/modules/workspace/MessageMedia/MessageMedia', () => ({MessageMedia: () => null}));

const message: Message = {
    id: '123',
    chatId: '42',
    text: 'A short message',
    outgoing: false,
    date: 1792064000,
    kind: 'text',
    contextExcluded: true,
};

function mount(onMenuOpenChange = vi.fn()) {
    render(
        <NextIntlClientProvider
            locale="ru"
            messages={{...ru, chatUi: {messageActions: 'Действия с сообщением'}}}
        >
            <ThemeProvider>
                <MessageBubble message={message} onMenuOpenChange={onMenuOpenChange} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    return onMenuOpenChange;
}

it('opens actions from the message row and reports menu lifetime without mutating the message', async () => {
    const copy = vi.fn(async () => {});
    Object.defineProperty(navigator, 'clipboard', {configurable: true, value: {writeText: copy}});
    const changed = mount();
    fireEvent.contextMenu(screen.getByRole('group', {name: 'Действия с сообщением'}), {
        clientX: 400,
        clientY: 180,
    });
    expect(changed).toHaveBeenCalledWith('123', true);
    fireEvent.click(await screen.findByRole('menuitem', {name: ru.messageMenu.copyText}));
    await waitFor(() => expect(copy).toHaveBeenCalledWith(message.text));
    await waitFor(() => expect(changed).toHaveBeenCalledWith('123', false));
    expect(screen.getByText(message.text)).toBeVisible();
    expect(screen.getByText(ru.messageMenu.excluded).closest('.g-label')).not.toBeNull();
});

it('supports opening message actions with the keyboard context-menu shortcut', async () => {
    const changed = mount();
    const row = screen.getByRole('group', {name: 'Действия с сообщением'});
    row.focus();
    fireEvent.keyDown(row, {key: 'F10', shiftKey: true});
    expect(await screen.findByRole('menuitem', {name: ru.messageMenu.copyText})).toBeVisible();
    expect(changed).toHaveBeenCalledWith('123', true);
});
