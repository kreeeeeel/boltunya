import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {ParallelSettingsDialog} from '@/modules/activity/ParallelSettingsDialog/ParallelSettingsDialog';
import ru from '@/shared/i18n/ru.json';
const mocks = vi.hoisted(() => ({GET: vi.fn(), PATCH: vi.fn()}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: mocks,
}));
function mount(onClose = vi.fn()) {
    return render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ParallelSettingsDialog value={1} onClose={onClose} onRefresh={async () => {}} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
}
it('saves the global limit and shows current measurements separately from its qualitative estimate', async () => {
    mocks.GET.mockResolvedValue({
        data: [
            {
                appMemoryBytes: 1024 ** 3,
                ollamaMemoryBytes: 2 * 1024 ** 3,
                gpuMemoryUsedBytes: 4 * 1024 ** 3,
                gpuMemoryTotalBytes: 12 * 1024 ** 3,
            },
        ],
        response: new Response(),
    });
    mocks.PATCH.mockResolvedValue({data: {ok: true}, response: new Response()});
    const close = vi.fn();
    mount(close);
    expect(await screen.findByText('Приложение и Ollama сейчас: 3 ГиБ RAM')).toBeVisible();
    expect(screen.getByText('GPU сейчас: 4 ГиБ / 12 ГиБ VRAM')).toBeVisible();
    fireEvent.click(screen.getByRole('radio', {name: '4'}));
    expect(screen.getByText(ru.events.parallel.levels.veryHigh)).toBeVisible();
    fireEvent.click(screen.getByRole('button', {name: ru.save}));
    await waitFor(() =>
        expect(mocks.PATCH).toHaveBeenCalledWith('/api/settings', {body: {maxParallelTasks: 4}}),
    );
    expect(await screen.findByRole('button', {name: ru.saved})).toBeVisible();
    expect(close).not.toHaveBeenCalled();
});
it('does not invent RAM or VRAM readings when metrics are missing', async () => {
    mocks.GET.mockResolvedValue({data: [], response: new Response()});
    mount();
    expect(await screen.findByText(ru.events.parallel.metricsUnavailable)).toBeVisible();
    expect(screen.queryByText((content) => content.includes('VRAM'))).toBeNull();
});
