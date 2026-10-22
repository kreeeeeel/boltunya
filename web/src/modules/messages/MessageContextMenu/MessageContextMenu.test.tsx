import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {useState} from 'react';
import {MessageContextMenu} from '@/modules/messages/MessageContextMenu/MessageContextMenu';
import ru from '@/shared/i18n/ru.json';
import type {Message} from '@/shared/api/client';
const mocks = vi.hoisted(() => ({
    messageLink: vi.fn(),
    messageDeletionOptions: vi.fn(),
    excludeMessageContext: vi.fn(),
    restoreChatContext: vi.fn(),
    deleteMessage: vi.fn(),
}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: {
        chats: {
            messageLink: mocks.messageLink,
            excludeMessageContext: mocks.excludeMessageContext,
            restoreChatContext: mocks.restoreChatContext,
            messageDeletionOptions: mocks.messageDeletionOptions,
            deleteMessage: mocks.deleteMessage,
        },
    },
}));
const message: Message = {
    id: 'opaque-telegram-message-id',
    chatId: '42',
    date: 0,
    outgoing: false,
    kind: 'text',
    text: 'Обычный текст',
};
function mount(
    onExcludedChanged = vi.fn(async () => {}),
    target = message,
    onEditRequest?: (value: Message) => void,
    onDraftRequest?: (output: 'text' | 'voice') => Promise<void>,
) {
    function Example() {
        const [position, setPosition] = useState<{
            x: number;
            y: number;
        } | null>({x: 100, y: 120});
        return (
            <NextIntlClientProvider locale="ru" messages={ru}>
                <ThemeProvider>
                    <MessageContextMenu
                        message={target}
                        position={position}
                        onClose={() => setPosition(null)}
                        onExcludedChanged={onExcludedChanged}
                        onEditRequest={onEditRequest}
                        onDraftRequest={onDraftRequest}
                    />
                </ThemeProvider>
            </NextIntlClientProvider>
        );
    }
    return render(<Example />);
}

it.each(['text', 'voice'] as const)(
    'offers an explicit %s draft in the prepare-reply flyout',
    async (output) => {
        const generate = vi.fn(async () => {});
        mount(undefined, message, undefined, generate);
        const trigger = await screen.findByRole('menuitem', {name: ru.messageMenu.draft});
        fireEvent.mouseEnter(trigger);
        expect(generate).not.toHaveBeenCalled();
        fireEvent.click(
            await screen.findByRole('menuitem', {
                name: output === 'text' ? ru.voice.generateText : ru.voice.generateVoice,
            }),
        );
        await waitFor(() => expect(generate).toHaveBeenCalledWith(output));
    },
);

it('opens editing only after the owner explicitly selects the action', async () => {
    const edit = vi.fn();
    const own = {...message, outgoing: true};
    mount(undefined, own, edit);
    expect(edit).not.toHaveBeenCalled();
    fireEvent.click(await screen.findByRole('menuitem', {name: ru.messageEdit.action}));
    expect(edit).toHaveBeenCalledWith(own);
});

it.each([
    {...message, outgoing: false},
    {...message, outgoing: true, deleted: true},
    {...message, outgoing: true, kind: 'sticker'},
])('does not offer editing for an incoming deleted or unsupported message', async (value) => {
    mount(undefined, value, vi.fn());
    await screen.findByRole('menuitem', {name: ru.messageMenu.copyText});
    expect(screen.queryByRole('menuitem', {name: ru.messageEdit.action})).toBeNull();
});
it('copies the genuine Telegram link fetched on demand without inventing an opaque ID URL', async () => {
    const copy = vi.fn(async () => {});
    Object.defineProperty(navigator, 'clipboard', {configurable: true, value: {writeText: copy}});
    mocks.messageLink.mockResolvedValue({url: 'https://t.me/c/100/321'});
    mocks.messageDeletionOptions.mockResolvedValue({url: 'https://t.me/c/100/321'});
    mount();
    expect(mocks.messageLink).not.toHaveBeenCalled();
    fireEvent.click(await screen.findByRole('menuitem', {name: ru.messageMenu.copyLink}));
    await waitFor(() => expect(copy).toHaveBeenCalledWith('https://t.me/c/100/321'));
    expect(mocks.messageLink).toHaveBeenCalledWith({
        chatId: '42',
        messageId: message.id,
    });
});
it('uses a nested range action and updates the visible state only after persistence', async () => {
    mocks.excludeMessageContext.mockResolvedValue({ok: true});
    const refreshed = vi.fn(async () => {});
    mount(refreshed);
    fireEvent.mouseEnter(await screen.findByRole('menuitem', {name: ru.messageMenu.exclude}));
    fireEvent.click(await screen.findByRole('menuitem', {name: ru.messageMenu.scopes.through}));
    await waitFor(() =>
        expect(mocks.excludeMessageContext).toHaveBeenCalledWith({
            chatId: '42',
            messageId: message.id,
            contextExclusionUpdate: {scope: 'through', excluded: true},
        }),
    );
    await waitFor(() =>
        expect(refreshed).toHaveBeenCalledWith({messageId: message.id, scope: 'through', excluded: true}),
    );
});
it('keeps context flags unchanged when persistence fails and explains the error', async () => {
    mocks.excludeMessageContext.mockRejectedValue(new Error('Сохранение недоступно'));
    const refreshed = vi.fn(async () => {});
    mount(refreshed);
    fireEvent.mouseEnter(await screen.findByRole('menuitem', {name: ru.messageMenu.exclude}));
    fireEvent.click(await screen.findByRole('menuitem', {name: ru.messageMenu.scopes.message}));
    expect(await screen.findByRole('alert')).toHaveTextContent('Сохранение недоступно');
    expect(refreshed).not.toHaveBeenCalled();
});
it('opens a separate deletion confirmation while keeping the context menu closed', async () => {
    mocks.messageLink.mockResolvedValue({canDeleteForSelf: true, canDeleteForEveryone: false});
    mocks.messageDeletionOptions.mockResolvedValue({canDeleteForSelf: true, canDeleteForEveryone: false});
    mocks.restoreChatContext.mockClear();
    mocks.deleteMessage.mockClear();
    mount();
    fireEvent.click(await screen.findByRole('menuitem', {name: ru.messageMenu.delete}));
    expect(await screen.findByRole('dialog', {name: ru.messageDelete.title})).toBeVisible();
    expect(await screen.findByRole('button', {name: ru.messageDelete.confirmSelf})).toBeEnabled();
    await waitFor(() =>
        expect(screen.queryByRole('menuitem', {name: ru.messageMenu.copyText})).not.toBeInTheDocument(),
    );
    expect(mocks.restoreChatContext).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', {name: ru.cancel}));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
});
