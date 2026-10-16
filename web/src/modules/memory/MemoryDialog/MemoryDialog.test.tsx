import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {act, fireEvent, render, screen, waitFor} from '@testing-library/react';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {MemoryDialog} from '@/modules/memory/MemoryDialog/MemoryDialog';
import ru from '@/shared/i18n/ru.json';

const mocks = vi.hoisted(() => ({GET: vi.fn(), PATCH: vi.fn(), POST: vi.fn()}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: mocks,
}));

const document = {
    chatId: '42',
    notes: '# Мои заметки',
    summary: 'Друг со школы, любит чай.',
    revision: 'revision',
    updatedAt: 1000,
    processedMessages: 720,
    backfillComplete: false,
    status: 'queued',
    error: '',
    trackedChats: 1,
    relativePath: 'memory/100/42/MEMORY.md',
};
function mount() {
    return render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <MemoryDialog chatId="42" title="Друг" onClose={vi.fn()} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
}
beforeEach(() => {
    mocks.GET.mockReset().mockResolvedValue({data: document, response: new Response()});
    mocks.PATCH.mockReset();
    mocks.POST.mockReset();
});

describe('Memory editor', () => {
    it('edits owner notes with their revision and keeps the generated summary read-only', async () => {
        mocks.PATCH.mockResolvedValue({
            data: {...document, notes: 'Не обращаться на вы', revision: 'new-revision'},
            response: new Response(),
        });
        mount();
        await screen.findByText(document.summary);
        const input = screen.getByRole('textbox', {name: ru.memory.notes});
        fireEvent.change(input, {target: {value: 'Не обращаться на вы'}});
        fireEvent.click(screen.getByRole('button', {name: ru.memory.save}));
        await waitFor(() =>
            expect(mocks.PATCH).toHaveBeenCalledWith('/api/memory/{chatId}', {
                params: {path: {chatId: '42'}},
                body: {notes: 'Не обращаться на вы', revision: 'revision'},
            }),
        );
        expect(screen.getByText(document.summary)).toBeVisible();
        expect(screen.getAllByRole('textbox')).toHaveLength(1);
    });

    it('updates a background summary without replacing an unsaved notes draft', async () => {
        vi.useFakeTimers();
        const view = mount();
        try {
            await act(async () => {
                await Promise.resolve();
            });
            const input = screen.getByRole('textbox', {name: ru.memory.notes});
            fireEvent.change(input, {target: {value: 'Несохранённая заметка'}});
            mocks.GET.mockResolvedValue({
                data: {...document, notes: 'Изменено вне приложения', summary: 'Свежая сводка'},
                response: new Response(),
            });
            await act(async () => {
                await vi.advanceTimersByTimeAsync(5001);
            });
            expect(input).toHaveValue('Несохранённая заметка');
            expect(screen.getByText('Свежая сводка')).toBeVisible();
        } finally {
            view.unmount();
            vi.useRealTimers();
        }
    });
});
