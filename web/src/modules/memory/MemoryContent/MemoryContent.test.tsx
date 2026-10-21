import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen} from '@testing-library/react';
import {expect, it, vi} from 'vitest';

import {MemoryContent} from '@/modules/memory/MemoryContent/MemoryContent';
import ru from '@/shared/i18n/ru.json';

const open = vi.hoisted(() => vi.fn());
vi.mock('@/shared/desktop/bridge', () => ({desktopBridge: () => ({openStorageLocation: open})}));

it.each(['42', ''])(
    'keeps refresh, rebuild, file reload and the allowlisted Explorer action independent for %s',
    async (chatId) => {
        const memory = {
            document: {
                chatId: '42',
                notes: 'Manual notes',
                summary: 'Facts',
                revision: 'one',
                updatedAt: 0,
                processedMessages: 100,
                backfillComplete: true,
                status: 'ready' as const,
                error: '',
                trackedChats: 1,
                relativePath: 'memory/100/42/MEMORY.md',
            },
            notes: 'Unsaved notes',
            setNotes: vi.fn(),
            error: '',
            busy: false,
            reload: vi.fn(),
            save: vi.fn(),
            refresh: vi.fn(),
            cancel: vi.fn(),
        };
        render(
            <NextIntlClientProvider locale="ru" messages={ru}>
                <ThemeProvider>
                    <MemoryContent memory={memory} chatId={chatId} />
                </ThemeProvider>
            </NextIntlClientProvider>,
        );
        fireEvent.click(screen.getByRole('button', {name: ru.memory.refresh}));
        expect(memory.refresh).toHaveBeenLastCalledWith();
        fireEvent.click(screen.getByRole('button', {name: ru.memory.rebuild}));
        expect(memory.refresh).toHaveBeenLastCalledWith(true);
        fireEvent.click(screen.getByRole('button', {name: ru.memory.reload}));
        expect(memory.reload).toHaveBeenCalledOnce();
        fireEvent.click(screen.getByRole('button', {name: ru.storageLocations.open}));
        expect(open).toHaveBeenCalledWith('memory');
        expect(memory.save).not.toHaveBeenCalled();
        expect(screen.getByRole('textbox')).toHaveValue('Unsaved notes');
    },
);
