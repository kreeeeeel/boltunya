import {ThemeProvider} from '@gravity-ui/uikit';
import {act, fireEvent, render, screen} from '@testing-library/react';
import {NextIntlClientProvider} from 'next-intl';
import {expect, it, vi} from 'vitest';

import {SettingsCenter} from '@/modules/settings/SettingsCenter/SettingsCenter';
import ru from '@/shared/i18n/ru.json';
import {workspaceState} from '@tests/fixtures/workspace';

const mocks = vi.hoisted(() => ({
    getGlobalMemory: vi.fn(),
    updateGlobalMemory: vi.fn(),
    refreshChangedMemory: vi.fn(),
    cancelGlobalMemoryCollection: vi.fn(),
}));

vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: {memory: mocks},
}));

const memory = {
    chatId: '',
    notes: 'Synthetic saved notes',
    summary: 'Synthetic factual summary',
    revision: 'synthetic-revision',
    updatedAt: 1000,
    processedMessages: 120,
    backfillComplete: true,
    status: 'ready',
    error: '',
    trackedChats: 1,
    relativePath: 'memory/100/MEMORY.md',
};

async function settle() {
    await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
    });
}

it('polls only the visible memory section and retains its notes draft while navigating', async () => {
    vi.useFakeTimers();
    mocks.getGlobalMemory.mockReset().mockImplementation(async () => structuredClone(memory));
    mocks.updateGlobalMemory.mockReset();
    mocks.refreshChangedMemory.mockReset();
    mocks.cancelGlobalMemoryCollection.mockReset();
    const view = render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <SettingsCenter
                    settings={workspaceState.settings}
                    initialSection="memory"
                    onClose={vi.fn()}
                    onRefresh={vi.fn()}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );

    try {
        await settle();
        expect(mocks.getGlobalMemory).toHaveBeenCalledTimes(1);
        fireEvent.change(screen.getByRole('textbox', {name: ru.memory.notes}), {
            target: {value: 'Synthetic unsaved draft'},
        });
        fireEvent.click(screen.getByRole('menuitem', {name: ru.settingsCenter.sections.collection}));
        await settle();
        expect(mocks.getGlobalMemory).toHaveBeenCalledTimes(2);

        await act(async () => {
            await vi.advanceTimersByTimeAsync(5001);
        });
        expect(mocks.getGlobalMemory).toHaveBeenCalledTimes(3);

        fireEvent.click(screen.getByRole('menuitem', {name: ru.settingsCenter.sections.application}));
        await settle();
        await act(async () => {
            await vi.advanceTimersByTimeAsync(15000);
        });
        expect(mocks.getGlobalMemory).toHaveBeenCalledTimes(3);

        mocks.getGlobalMemory.mockImplementation(async () => ({
            ...memory,
            summary: 'Synthetic updated summary',
            notes: 'Synthetic external edit',
            revision: 'synthetic-new-revision',
        }));
        fireEvent.click(screen.getByRole('menuitem', {name: ru.settingsCenter.sections.memory}));
        await settle();
        expect(mocks.getGlobalMemory).toHaveBeenCalledTimes(4);
        expect(screen.getByRole('textbox', {name: ru.memory.notes})).toHaveValue('Synthetic unsaved draft');
        expect(screen.getByText('Synthetic updated summary')).toBeVisible();
        expect(mocks.updateGlobalMemory).not.toHaveBeenCalled();
        expect(mocks.refreshChangedMemory).not.toHaveBeenCalled();
        expect(mocks.cancelGlobalMemoryCollection).not.toHaveBeenCalled();

        view.unmount();
        await act(async () => {
            await vi.advanceTimersByTimeAsync(15000);
        });
        expect(mocks.getGlobalMemory).toHaveBeenCalledTimes(4);
    } finally {
        view.unmount();
        vi.useRealTimers();
    }
});
