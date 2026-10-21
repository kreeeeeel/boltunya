import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {render, screen} from '@testing-library/react';
import {expect, it, vi} from 'vitest';

import {ActivityDetailsDialog} from '@/modules/activity/ActivityDetailsDialog/ActivityDetailsDialog';
import {activityDetails, testActivity} from '@tests/fixtures/activity';
import ru from '@/shared/i18n/ru.json';

const mocks = vi.hoisted(() => ({details: vi.fn()}));
vi.mock('@/modules/activity/model/useActivityDetails', () => ({useActivityDetails: mocks.details}));

it('does not claim a ready reply was sent when Telegram delivery failed', () => {
    const details = activityDetails(testActivity[3]);
    details.response = 'Готовый ответ, который не был доставлен.';
    mocks.details.mockReturnValue({details, error: '', updateFeedback: vi.fn()});
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ActivityDetailsDialog id="error" onClose={vi.fn()} onRefresh={vi.fn()} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    expect(screen.getByText(ru.events.readyResponse)).toBeVisible();
    expect(screen.queryByText(ru.events.delivered)).toBeNull();
    expect(screen.getByText(details.response)).toBeVisible();
});

it('shows completed memory timings and tokens without empty conversation or prompt blocks', () => {
    const details = activityDetails({
        ...testActivity[2],
        kind: 'memory',
        status: 'ready',
        phase: 'ready',
        inputTokens: 123,
        outputTokens: 45,
        feedbackAvailable: false,
    });
    details.response = '';
    details.trigger = '';
    details.attempts = [];
    mocks.details.mockReturnValue({details, error: '', updateFeedback: vi.fn()});
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ActivityDetailsDialog id="memory" onClose={vi.fn()} onRefresh={vi.fn()} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    expect(screen.getByRole('dialog', {name: ru.events.memoryDetails})).toBeVisible();
    expect(screen.getByText(ru.events.memoryReady)).toBeVisible();
    expect(screen.getByText('123')).toBeVisible();
    expect(screen.getByText('45')).toBeVisible();
    expect(screen.getByText(ru.events.memoryPrivacy)).toBeVisible();
    expect(screen.queryByText(ru.events.trigger)).toBeNull();
    expect(screen.queryByText(ru.events.delivered)).toBeNull();
});
