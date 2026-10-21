import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {render, screen} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {PromptBudgetNotice} from '@/modules/settings/PromptBudgetNotice/PromptBudgetNotice';
import {syntheticPromptBudget} from '@tests/fixtures/promptBudget';
import ru from '@/shared/i18n/ru.json';

const state = vi.hoisted(() => ({automatic: true}));
vi.mock('@/modules/settings/model/usePromptBudget', () => ({
    usePromptBudget: () => ({
        report: {...syntheticPromptBudget({}), automaticContext: state.automatic},
        error: '',
    }),
}));

it('explains automatic context sizing only while no explicit limit is selected', () => {
    const content = (
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <PromptBudgetNotice request={{}} />
            </ThemeProvider>
        </NextIntlClientProvider>
    );
    const view = render(content);
    expect(screen.getByText(ru.promptBudget.automatic)).toBeVisible();
    state.automatic = false;
    view.rerender(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <PromptBudgetNotice request={{}} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    expect(screen.queryByText(ru.promptBudget.automatic)).toBeNull();
});
