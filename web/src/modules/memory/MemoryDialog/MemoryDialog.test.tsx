import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {act, fireEvent, render, screen, waitFor} from '@testing-library/react';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {MemoryDialog} from '@/modules/memory/MemoryDialog/MemoryDialog';
import ru from '@/shared/i18n/ru.json';
const mocks = vi.hoisted(() => ({
    getChatMemory: vi.fn(),
    getGlobalMemory: vi.fn(),
    updateChatMemory: vi.fn(),
    updateGlobalMemory: vi.fn(),
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
            updateChatMemory: mocks.updateChatMemory,
            updateGlobalMemory: mocks.updateGlobalMemory,
            refreshChatMemory: mocks.refreshChatMemory,
            refreshChangedMemory: mocks.refreshChangedMemory,
            cancelChatMemoryCollection: mocks.cancelChatMemoryCollection,
            cancelGlobalMemoryCollection: mocks.cancelGlobalMemoryCollection,
        },
    },
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
    mocks.getChatMemory.mockReset().mockResolvedValue(document);
    mocks.getGlobalMemory.mockReset().mockResolvedValue(document);
    mocks.updateChatMemory.mockReset();
    mocks.updateGlobalMemory.mockReset();
    mocks.refreshChatMemory.mockReset();
    mocks.refreshChangedMemory.mockReset();
    mocks.cancelChatMemoryCollection.mockReset();
    mocks.cancelGlobalMemoryCollection.mockReset();
});
describe('Memory editor', () => {
    it('edits owner notes with their revision and keeps the generated summary read-only', async () => {
        mocks.updateChatMemory.mockResolvedValue({
            ...document,
            notes: 'Не обращаться на вы',
            revision: 'new-revision',
        });
        mocks.updateGlobalMemory.mockResolvedValue({
            ...document,
            notes: 'Не обращаться на вы',
            revision: 'new-revision',
        });
        mount();
        await screen.findByText(document.summary);
        const input = screen.getByRole('textbox', {name: ru.memory.notes});
        fireEvent.change(input, {target: {value: 'Не обращаться на вы'}});
        fireEvent.click(screen.getByRole('button', {name: ru.memory.save}));
        await waitFor(() =>
            expect(mocks.updateChatMemory).toHaveBeenCalledWith({
                chatId: '42',
                memoryNotesUpdate: {notes: 'Не обращаться на вы', revision: 'revision'},
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
            mocks.getChatMemory.mockResolvedValue({
                ...document,
                notes: 'Изменено вне приложения',
                summary: 'Свежая сводка',
            });
            mocks.getGlobalMemory.mockResolvedValue({
                ...document,
                notes: 'Изменено вне приложения',
                summary: 'Свежая сводка',
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
