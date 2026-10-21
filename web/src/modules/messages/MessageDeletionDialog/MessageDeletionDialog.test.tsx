import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {beforeEach, expect, it, vi} from 'vitest';

import {MessageDeletionDialog} from '@/modules/messages/MessageDeletionDialog/MessageDeletionDialog';
import ru from '@/shared/i18n/ru.json';

const mocks = vi.hoisted(() => ({GET: vi.fn(), DELETE: vi.fn()}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: mocks,
}));

beforeEach(() => {
    vi.clearAllMocks();
    mocks.GET.mockResolvedValue({
        data: {canDeleteForSelf: true, canDeleteForEveryone: true},
        response: new Response(),
    });
    mocks.DELETE.mockResolvedValue({
        data: {chatId: '42', messageId: '123', deletedAt: 100},
        response: new Response(),
    });
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
    expect(mocks.DELETE).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('radio', {name: ru.messageDelete.everyone}));
    fireEvent.click(screen.getByRole('button', {name: ru.messageDelete.confirmEveryone}));
    await waitFor(() =>
        expect(mocks.DELETE).toHaveBeenCalledWith('/api/chats/{chatId}/messages/{messageId}', {
            params: {path: {chatId: '42', messageId: '123'}},
            body: {scope: 'everyone'},
        }),
    );
    expect(onDeleted).toHaveBeenCalledWith({chatId: '42', messageId: '123', deletedAt: 100});
    await waitFor(() => expect(onClose).toHaveBeenCalledOnce());
});

it('offers only scopes authorized by Telegram and cancellation performs no mutation', async () => {
    mocks.GET.mockResolvedValue({
        data: {canDeleteForSelf: false, canDeleteForEveryone: true},
        response: new Response(),
    });
    const {onClose} = mount();
    expect(await screen.findByRole('radio', {name: ru.messageDelete.everyone})).toBeChecked();
    expect(screen.queryByRole('radio', {name: ru.messageDelete.self})).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', {name: ru.cancel}));
    expect(onClose).toHaveBeenCalledOnce();
    expect(mocks.DELETE).not.toHaveBeenCalled();
});

it('does not offer confirmation when neither scope is available or permissions failed to load', async () => {
    mocks.GET.mockResolvedValue({
        data: {canDeleteForSelf: false, canDeleteForEveryone: false},
        response: new Response(),
    });
    mount();
    expect(await screen.findByText(ru.messageDelete.unavailable)).toBeVisible();
    expect(screen.queryByRole('radio')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', {name: ru.messageDelete.confirmSelf})).not.toBeInTheDocument();
    expect(mocks.DELETE).not.toHaveBeenCalled();
});

it('keeps confirmation open on failure without changing the visible message', async () => {
    mocks.DELETE.mockRejectedValue(new Error('Права на удаление изменились'));
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
    expect(mocks.DELETE).toHaveBeenCalledOnce();
    expect(onClose).not.toHaveBeenCalled();
});
