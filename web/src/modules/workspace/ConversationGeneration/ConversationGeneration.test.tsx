import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {ConversationGeneration} from '@/modules/workspace/ConversationGeneration/ConversationGeneration';
import {testActivity} from '@tests/fixtures/activity';
import ru from '@/shared/i18n/ru.json';

it.each(['error', 'interrupted'])('renders a %s reply in a block with the recovery action', (status) => {
    const retry = vi.fn();
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ConversationGeneration
                    entry={{...testActivity[0], status, detail: 'Модель не ответила.'}}
                    onRefresh={vi.fn()}
                    onRetry={retry}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    const notice = screen.getByRole('alert', {name: ru.events.failed});
    expect(notice).toHaveTextContent('Модель не ответила.');
    expect(notice.querySelector('svg')).not.toBeNull();
    fireEvent.click(screen.getByRole('button', {name: ru.events.retryDraft}));
    expect(retry).toHaveBeenCalledOnce();
});
