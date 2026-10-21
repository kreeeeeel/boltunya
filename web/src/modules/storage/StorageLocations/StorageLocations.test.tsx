import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {afterEach, expect, it, vi} from 'vitest';
import {StorageLocations} from '@/modules/storage/StorageLocations/StorageLocations';
import ru from '@/shared/i18n/ru.json';
const mocks = vi.hoisted(() => ({storageLocations: vi.fn()}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: {
        system: {
            storageLocations: mocks.storageLocations,
        },
    },
}));
afterEach(() => {
    delete window.boltunyaDesktop;
});
it('opens known storage IDs through Electron and keeps external locations disabled', async () => {
    const openStorageLocation = vi.fn().mockResolvedValue(undefined);
    window.boltunyaDesktop = {
        state: vi.fn(),
        sync: vi.fn(),
        quit: vi.fn(),
        action: vi.fn(),
        onState: vi.fn(),
        openStorageLocation,
    };
    mocks.storageLocations.mockResolvedValue({
        locations: [
            {
                id: 'memory',
                label: '',
                path: 'C:/profile/memory',
                available: true,
                openable: true,
                kind: 'directory',
                note: '',
            },
            {
                id: 'ollama-models',
                label: '',
                path: '',
                available: true,
                openable: false,
                kind: 'external',
                note: '',
            },
        ],
    });
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <StorageLocations />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    const open = await screen.findByRole('button', {name: 'Открыть: Банк памяти'});
    fireEvent.click(open);
    await waitFor(() => expect(openStorageLocation).toHaveBeenCalledWith('memory'));
    expect(screen.getByRole('button', {name: 'Открыть: Модели Ollama'})).toBeDisabled();
    expect(screen.getByText('C:/profile/memory')).toBeVisible();
});
