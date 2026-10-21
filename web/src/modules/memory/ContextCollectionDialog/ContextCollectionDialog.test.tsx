import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {beforeEach, expect, it, vi} from 'vitest';
import {ContextCollectionDialog} from '@/modules/memory/ContextCollectionDialog/ContextCollectionDialog';
import ru from '@/shared/i18n/ru.json';
const mocks = vi.hoisted(() => ({
    getChatMemory: vi.fn(),
    getGlobalMemory: vi.fn(),
    refreshChatMemory: vi.fn(),
    refreshChangedMemory: vi.fn(),
    cancelChatMemoryCollection: vi.fn(),
    cancelGlobalMemoryCollection: vi.fn(),
}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: {
        memory: {
            getChatMemory: mocks.getChatMemory,
            getGlobalMemory: mocks.getGlobalMemory,
            refreshChatMemory: mocks.refreshChatMemory,
            refreshChangedMemory: mocks.refreshChangedMemory,
            cancelChatMemoryCollection: mocks.cancelChatMemoryCollection,
            cancelGlobalMemoryCollection: mocks.cancelGlobalMemoryCollection,
        },
    },
}));
const document = {
    chatId: '42',
    notes: '',
    summary: 'Текущая память',
    revision: '1',
    updatedAt: 0,
    processedMessages: 120,
    backfillComplete: true,
    status: 'ready',
    error: '',
    trackedChats: 1,
    relativePath: 'memory/100/42/MEMORY.md',
};
function mount(chatId = '42') {
    return render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ContextCollectionDialog chatId={chatId} title="Аня" onClose={vi.fn()} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
}
beforeEach(() => {
    mocks.getChatMemory.mockReset().mockResolvedValue(document);
    mocks.getGlobalMemory.mockReset().mockResolvedValue(document);
    mocks.refreshChatMemory.mockReset().mockImplementation(async () => {
        const result = {
            ...document,
            status: 'queued',
            collection: {
                active: true,
                status: 'queued',
                queuedChats: 2,
                queuePosition: 2,
                processedMessages: 0,
            },
        };
        mocks.getChatMemory.mockResolvedValue(result);
        mocks.getGlobalMemory.mockResolvedValue(result);
        return result;
    });
    mocks.refreshChangedMemory.mockReset().mockImplementation(async () => {
        const result = {
            ...document,
            status: 'queued',
            collection: {
                active: true,
                status: 'queued',
                queuedChats: 2,
                queuePosition: 2,
                processedMessages: 0,
            },
        };
        mocks.getChatMemory.mockResolvedValue(result);
        mocks.getGlobalMemory.mockResolvedValue(result);
        return result;
    });
    mocks.cancelChatMemoryCollection.mockReset().mockImplementation(async () => {
        const result = {
            ...document,
            status: 'queued',
            collection: {
                active: true,
                status: 'queued',
                queuedChats: 2,
                queuePosition: 2,
                processedMessages: 0,
            },
        };
        mocks.getChatMemory.mockResolvedValue(result);
        mocks.getGlobalMemory.mockResolvedValue(result);
        return result;
    });
    mocks.cancelGlobalMemoryCollection.mockReset().mockImplementation(async () => {
        const result = {
            ...document,
            status: 'queued',
            collection: {
                active: true,
                status: 'queued',
                queuedChats: 2,
                queuePosition: 2,
                processedMessages: 0,
            },
        };
        mocks.getChatMemory.mockResolvedValue(result);
        mocks.getGlobalMemory.mockResolvedValue(result);
        return result;
    });
});
it.each([false, true])(
    'requests prioritized collection with rebuild=%s and shows queue status',
    async (rebuild) => {
        mount();
        await screen.findByText('Просмотрено сообщений: 120');
        if (rebuild) {
            fireEvent.click(screen.getByRole('radio', {name: (name) => name.startsWith(ru.context.rebuild)}));
        }
        fireEvent.click(screen.getByRole('button', {name: ru.context.start}));
        await waitFor(() =>
            expect(mocks.refreshChatMemory).toHaveBeenCalledWith({
                chatId: '42',
                memoryRefreshRequest: {rebuild, immediate: true},
            }),
        );
        expect(await screen.findByText(ru.memory.status.queued)).toBeVisible();
        expect(screen.queryByRole('button', {name: ru.context.start})).not.toBeInTheDocument();
        expect(screen.getByRole('button', {name: ru.context.cancel})).toBeEnabled();
    },
);
it('explains disabled memory and does not silently enable it or start a job', async () => {
    mocks.getChatMemory.mockResolvedValue({...document, status: 'disabled'});
    mocks.getGlobalMemory.mockResolvedValue({...document, status: 'disabled'});
    mount();
    expect(await screen.findByText(ru.context.disabled)).toBeVisible();
    expect(screen.getByRole('button', {name: ru.context.start})).toBeDisabled();
    expect(mocks.refreshChatMemory).not.toHaveBeenCalled();
});
it('restores an active collection on opening and cancels it without editing saved notes', async () => {
    const active = {
        ...document,
        collection: {
            active: true,
            status: 'processing',
            queuedChats: 3,
            queuePosition: 0,
            processedMessages: 540,
        },
    };
    mocks.getChatMemory.mockResolvedValue(active);
    mocks.getGlobalMemory.mockResolvedValue(active);
    mocks.refreshChatMemory.mockImplementation(async () => {
        const result = document;
        mocks.getChatMemory.mockResolvedValue(result);
        mocks.getGlobalMemory.mockResolvedValue(result);
        return result;
    });
    mocks.refreshChangedMemory.mockImplementation(async () => {
        const result = document;
        mocks.getChatMemory.mockResolvedValue(result);
        mocks.getGlobalMemory.mockResolvedValue(result);
        return result;
    });
    mocks.cancelChatMemoryCollection.mockImplementation(async () => {
        const result = document;
        mocks.getChatMemory.mockResolvedValue(result);
        mocks.getGlobalMemory.mockResolvedValue(result);
        return result;
    });
    mocks.cancelGlobalMemoryCollection.mockImplementation(async () => {
        const result = document;
        mocks.getChatMemory.mockResolvedValue(result);
        mocks.getGlobalMemory.mockResolvedValue(result);
        return result;
    });
    mount();
    expect(await screen.findByText('Просмотрено сообщений: 540')).toBeVisible();
    expect(screen.getByText('Диалогов в очереди: 3')).toBeVisible();
    expect(screen.queryByRole('button', {name: ru.context.start})).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', {name: ru.context.cancel}));
    await waitFor(() =>
        expect(mocks.cancelChatMemoryCollection).toHaveBeenCalledWith({
            chatId: '42',
        }),
    );
    expect(await screen.findByRole('button', {name: ru.context.start})).toBeEnabled();
});
it('keeps a failed request retryable and displays the API error', async () => {
    mocks.refreshChatMemory.mockRejectedValue(new Error('Модель недоступна'));
    mocks.refreshChangedMemory.mockRejectedValue(new Error('Модель недоступна'));
    mocks.cancelChatMemoryCollection.mockRejectedValue(new Error('Модель недоступна'));
    mocks.cancelGlobalMemoryCollection.mockRejectedValue(new Error('Модель недоступна'));
    mount();
    await screen.findByText('Просмотрено сообщений: 120');
    fireEvent.click(screen.getByRole('button', {name: ru.context.start}));
    expect(await screen.findByRole('alert')).toHaveTextContent('Модель недоступна');
    expect(screen.getByRole('button', {name: ru.context.start})).toBeEnabled();
});
it('queues a complete global rebuild through the shared memory endpoint', async () => {
    mount('');
    await screen.findByText(ru.context.globalHint);
    fireEvent.click(screen.getByRole('radio', {name: (name) => name.startsWith(ru.context.rebuild)}));
    fireEvent.click(screen.getByRole('button', {name: ru.context.start}));
    await waitFor(() =>
        expect(mocks.refreshChangedMemory).toHaveBeenCalledWith({
            memoryRefreshRequest: {rebuild: true, immediate: true},
        }),
    );
});
