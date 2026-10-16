import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {beforeEach, expect, it, vi} from 'vitest';
import {CachePanel} from '@/modules/workspace/CachePanel/CachePanel';
import ru from '@/shared/i18n/ru.json';
const mocks = vi.hoisted(() => ({GET: vi.fn(), POST: vi.fn()}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: mocks,
}));
const stats = {
    totalBytes: 10 * 1024 * 1024,
    fileCount: 2,
    photoBytes: 4 * 1024 * 1024,
    videoBytes: 6 * 1024 * 1024,
    audioBytes: 0,
    documentBytes: 0,
};
function mount() {
    return render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <CachePanel />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
}
beforeEach(() => {
    mocks.GET.mockReset().mockResolvedValue({data: stats, response: new Response()});
    mocks.POST.mockReset();
});
it('shows actual cache size and replaces it with returned remaining statistics after clearing', async () => {
    mocks.POST.mockResolvedValue({
        data: {...stats, totalBytes: 0, fileCount: 0, photoBytes: 0, videoBytes: 0},
        response: new Response(),
    });
    mount();
    expect(await screen.findByText('10 МБ · файлов: 2')).toBeVisible();
    fireEvent.click(screen.getByRole('button', {name: ru.storage.clear}));
    expect(await screen.findByText('0 МБ · файлов: 0')).toBeVisible();
    expect(mocks.POST).toHaveBeenCalledWith('/api/cache/clear');
    expect(screen.getByRole('button', {name: ru.storage.clear})).toBeDisabled();
});
it('preserves the displayed size and allows retry when cleanup fails', async () => {
    mocks.POST.mockRejectedValue(new Error('Файл используется'));
    mount();
    await screen.findByText('10 МБ · файлов: 2');
    fireEvent.click(screen.getByRole('button', {name: ru.storage.clear}));
    expect(await screen.findByText('Файл используется')).toBeVisible();
    await waitFor(() => expect(screen.getByRole('button', {name: ru.storage.clear})).toBeEnabled());
    expect(screen.getByText('10 МБ · файлов: 2')).toBeVisible();
});
