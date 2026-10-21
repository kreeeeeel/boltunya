import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {render, screen} from '@testing-library/react';
import {expect, it, vi} from 'vitest';

import {ActivityCard} from '@/modules/activity/ActivityCard/ActivityCard';
import {testActivity} from '@tests/fixtures/activity';
import ru from '@/shared/i18n/ru.json';

it('keeps a completed memory result in its process block with its completion time', () => {
    const view = render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ActivityCard
                    entry={{
                        ...testActivity[2],
                        kind: 'memory',
                        status: 'ready',
                        phase: 'ready',
                        finishedAt: 1792152010000,
                        feedbackAvailable: false,
                    }}
                    busy={false}
                    onOpen={vi.fn()}
                    onCancel={vi.fn()}
                    onPriority={vi.fn()}
                    onRefresh={vi.fn()}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    const result = view.container.querySelector('[data-qa="reply-process"]');
    expect(result).toHaveTextContent(ru.events.memoryReady);
    expect(result).toHaveTextContent(
        new Date(1792152010000).toLocaleString('ru', {dateStyle: 'medium', timeStyle: 'short'}),
    );
    expect(screen.queryByRole('progressbar')).toBeNull();
});
