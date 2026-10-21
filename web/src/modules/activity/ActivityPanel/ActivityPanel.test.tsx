import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {ActivityPanel} from '@/modules/activity/ActivityPanel/ActivityPanel';
import ru from '@/shared/i18n/ru.json';
import {historyPage} from '@tests/fixtures/activityHistory';
const mocks = vi.hoisted(() => ({
    settings: vi.fn(),
    pauseReplyQueue: vi.fn(),
    activityDetails: vi.fn(),
    getMetrics: vi.fn(),
    activityHistorySettings: vi.fn(),
    activityHistory: vi.fn(),
}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: {
        settings: {
            settings: mocks.settings,
        },
        activity: {
            pauseReplyQueue: mocks.pauseReplyQueue,
            activityDetails: mocks.activityDetails,
            activityHistorySettings: mocks.activityHistorySettings,
            activityHistory: mocks.activityHistory,
        },
        system: {
            getMetrics: mocks.getMetrics,
        },
    },
}));
it('enables the assistant from the empty events description', async () => {
    mocks.settings.mockResolvedValue({ok: true});
    mocks.pauseReplyQueue.mockResolvedValue({ok: true});
    mocks.activityDetails.mockResolvedValue(historyPage());
    mocks.getMetrics.mockResolvedValue(historyPage());
    mocks.activityHistorySettings.mockResolvedValue(historyPage());
    mocks.activityHistory.mockResolvedValue(historyPage());
    const refresh = vi.fn();
    const view = render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ActivityPanel entries={[]} assistantPaused onRefresh={refresh} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    fireEvent.click(await screen.findByRole('button', {name: ru.events.enableAi}));
    await waitFor(() =>
        expect(mocks.settings).toHaveBeenCalledWith({
            settingsUpdate: {paused: false},
        }),
    );
    await waitFor(() => expect(refresh).toHaveBeenCalled());
    view.rerender(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ActivityPanel entries={[]} assistantPaused={false} onRefresh={refresh} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    expect(screen.queryByRole('button', {name: ru.events.enableAi})).not.toBeInTheDocument();
});
