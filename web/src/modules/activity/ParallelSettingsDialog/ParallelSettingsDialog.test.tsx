import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {ParallelSettingsDialog} from '@/modules/activity/ParallelSettingsDialog/ParallelSettingsDialog';
import ru from '@/shared/i18n/ru.json';
const mocks = vi.hoisted(() => ({settings: vi.fn()}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: {
        settings: {
            settings: mocks.settings,
        },
    },
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
it('saves the global limit with a qualitative load estimate and no hardware request', async () => {
    mocks.settings.mockResolvedValue({ok: true});
    const close = vi.fn();
    mount(close);
    fireEvent.click(screen.getByRole('radio', {name: '4'}));
    expect(screen.getByText(ru.events.parallel.levels.veryHigh)).toBeVisible();
    fireEvent.click(screen.getByRole('button', {name: ru.save}));
    await waitFor(() =>
        expect(mocks.settings).toHaveBeenCalledWith({
            settingsUpdate: {maxParallelTasks: 4},
        }),
    );
    expect(await screen.findByRole('button', {name: ru.saved})).toBeVisible();
    expect(close).not.toHaveBeenCalled();
});
it('explains the estimate without inventing RAM or VRAM measurements', () => {
    mount();
    expect(screen.getByText(ru.events.parallel.estimateHint)).toBeVisible();
    expect(screen.queryByText((content) => content.includes('VRAM'))).toBeNull();
});
