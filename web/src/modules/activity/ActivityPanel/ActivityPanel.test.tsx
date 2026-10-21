import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {expect, it, vi} from 'vitest';

import {ActivityPanel} from '@/modules/activity/ActivityPanel/ActivityPanel';
import ru from '@/shared/i18n/ru.json';
import {historyPage} from '@tests/fixtures/activityHistory';

const mocks = vi.hoisted(() => ({PATCH: vi.fn(), GET: vi.fn()}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: mocks,
}));

it('enables the assistant from the empty events description', async () => {
    mocks.PATCH.mockResolvedValue({data: {ok: true}, response: new Response()});
    mocks.GET.mockResolvedValue({data: historyPage(), response: new Response()});
    const refresh = vi.fn();
    const view = render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ActivityPanel entries={[]} assistantPaused onRefresh={refresh} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    fireEvent.click(await screen.findByRole('button', {name: ru.events.enableAi}));
    await waitFor(() => expect(mocks.PATCH).toHaveBeenCalledWith('/api/settings', {body: {paused: false}}));
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
