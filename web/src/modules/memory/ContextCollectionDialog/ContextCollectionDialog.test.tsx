import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {beforeEach, expect, it, vi} from 'vitest';
import {ContextCollectionDialog} from '@/modules/memory/ContextCollectionDialog/ContextCollectionDialog';
import ru from '@/shared/i18n/ru.json';

const mocks = vi.hoisted(() => ({GET: vi.fn(), POST: vi.fn()}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: mocks,
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
    mocks.GET.mockReset().mockResolvedValue({data: document, response: new Response()});
    mocks.POST.mockReset().mockImplementation(async () => {
        const result = {
            data: {
                ...document,
                status: 'queued',
                collection: {
                    active: true,
                    status: 'queued',
                    queuedChats: 2,
                    queuePosition: 2,
                    processedMessages: 0,
                },
            },
            response: new Response(),
        };
        mocks.GET.mockResolvedValue(result);
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
            expect(mocks.POST).toHaveBeenCalledWith('/api/memory/{chatId}', {
                params: {path: {chatId: '42'}},
                body: {rebuild, immediate: true},
            }),
        );
        expect(await screen.findByText(ru.memory.status.queued)).toBeVisible();
        expect(screen.queryByRole('button', {name: ru.context.start})).not.toBeInTheDocument();
        expect(screen.getByRole('button', {name: ru.context.cancel})).toBeEnabled();
    },
);

it('explains disabled memory and does not silently enable it or start a job', async () => {
    mocks.GET.mockResolvedValue({data: {...document, status: 'disabled'}, response: new Response()});
    mount();
    expect(await screen.findByText(ru.context.disabled)).toBeVisible();
    expect(screen.getByRole('button', {name: ru.context.start})).toBeDisabled();
    expect(mocks.POST).not.toHaveBeenCalled();
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
    mocks.GET.mockResolvedValue({data: active, response: new Response()});
    mocks.POST.mockImplementation(async () => {
        const result = {data: document, response: new Response()};
        mocks.GET.mockResolvedValue(result);
        return result;
    });
    mount();
    expect(await screen.findByText('Просмотрено сообщений: 540')).toBeVisible();
    expect(screen.getByText('Диалогов в очереди: 3')).toBeVisible();
    expect(screen.queryByRole('button', {name: ru.context.start})).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', {name: ru.context.cancel}));
    await waitFor(() =>
        expect(mocks.POST).toHaveBeenCalledWith('/api/memory/{chatId}/cancel', {
            params: {path: {chatId: '42'}},
        }),
    );
    expect(await screen.findByRole('button', {name: ru.context.start})).toBeEnabled();
});

it('keeps a failed request retryable and displays the API error', async () => {
    mocks.POST.mockRejectedValue(new Error('Модель недоступна'));
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
        expect(mocks.POST).toHaveBeenCalledWith('/api/memory/refresh', {
            body: {rebuild: true, immediate: true},
        }),
    );
});
