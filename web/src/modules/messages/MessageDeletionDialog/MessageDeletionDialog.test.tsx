import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {beforeEach, expect, it, vi} from 'vitest';
import {MessageDeletionDialog} from '@/modules/messages/MessageDeletionDialog/MessageDeletionDialog';
import ru from '@/shared/i18n/ru.json';
const mocks = vi.hoisted(() => ({messageDeletionOptions: vi.fn(), deleteMessage: vi.fn()}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: {
        chats: {
            messageDeletionOptions: mocks.messageDeletionOptions,
            deleteMessage: mocks.deleteMessage,
        },
    },
}));
beforeEach(() => {
    vi.clearAllMocks();
    mocks.messageDeletionOptions.mockResolvedValue({canDeleteForSelf: true, canDeleteForEveryone: true});
    mocks.deleteMessage.mockResolvedValue({chatId: '42', messageId: '123', deletedAt: 100});
});
function mount(onDeleted = vi.fn(async () => {}), onClose = vi.fn()) {
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <MessageDeletionDialog chatId="42" messageId="123" onClose={onClose} onDeleted={onDeleted} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    return {onDeleted, onClose};
}
it('defaults to the narrower permitted scope and never deletes before explicit confirmation', async () => {
    const {onDeleted, onClose} = mount();
    expect(await screen.findByRole('radio', {name: ru.messageDelete.self})).toBeChecked();
    expect(mocks.deleteMessage).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('radio', {name: ru.messageDelete.everyone}));
    fireEvent.click(screen.getByRole('button', {name: ru.messageDelete.confirmEveryone}));
    await waitFor(() =>
        expect(mocks.deleteMessage).toHaveBeenCalledWith({
            chatId: '42',
            messageId: '123',
            messageDeletionRequest: {scope: 'everyone'},
        }),
    );
    expect(onDeleted).toHaveBeenCalledWith({chatId: '42', messageId: '123', deletedAt: 100});
    await waitFor(() => expect(onClose).toHaveBeenCalledOnce());
});
it('offers only scopes authorized by Telegram and cancellation performs no mutation', async () => {
    mocks.messageDeletionOptions.mockResolvedValue({canDeleteForSelf: false, canDeleteForEveryone: true});
    const {onClose} = mount();
    expect(await screen.findByRole('radio', {name: ru.messageDelete.everyone})).toBeChecked();
    expect(screen.queryByRole('radio', {name: ru.messageDelete.self})).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', {name: ru.cancel}));
    expect(onClose).toHaveBeenCalledOnce();
    expect(mocks.deleteMessage).not.toHaveBeenCalled();
});
it('does not offer confirmation when neither scope is available or permissions failed to load', async () => {
    mocks.messageDeletionOptions.mockResolvedValue({canDeleteForSelf: false, canDeleteForEveryone: false});
    mount();
    expect(await screen.findByText(ru.messageDelete.unavailable)).toBeVisible();
    expect(screen.queryByRole('radio')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', {name: ru.messageDelete.confirmSelf})).not.toBeInTheDocument();
    expect(mocks.deleteMessage).not.toHaveBeenCalled();
});
it('keeps confirmation open on failure without changing the visible message', async () => {
    mocks.deleteMessage.mockRejectedValue(new Error('Права на удаление изменились'));
    const {onDeleted, onClose} = mount();
    fireEvent.click(await screen.findByRole('button', {name: ru.messageDelete.confirmSelf}));
    expect(await screen.findByRole('alert')).toHaveTextContent('Права на удаление изменились');
    expect(onDeleted).not.toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.getByRole('button', {name: ru.messageDelete.confirmSelf})).toBeEnabled();
});
it('distinguishes a successful Telegram mutation from a failed refresh and cannot delete twice', async () => {
    const onDeleted = vi.fn(async () => {
        throw new Error('Refresh failed');
    });
    const {onClose} = mount(onDeleted);
    fireEvent.click(await screen.findByRole('button', {name: ru.messageDelete.confirmSelf}));
    expect(await screen.findByRole('alert')).toHaveTextContent(ru.messageDelete.refreshFailed);
    expect(screen.getByText(ru.messageDelete.completed)).toBeVisible();
    expect(screen.queryByRole('button', {name: ru.messageDelete.confirmSelf})).not.toBeInTheDocument();
    expect(mocks.deleteMessage).toHaveBeenCalledOnce();
    expect(onClose).not.toHaveBeenCalled();
});
