import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {LanguageSettings} from '@/modules/settings/LanguageSettings/LanguageSettings';
import ru from '@/shared/i18n/ru.json';

const changeLocale = vi.fn();
vi.mock('@/shared/theme/Preferences', () => ({usePreferences: () => ({locale: 'ru', changeLocale})}));

it('uses a single radio selection with native language names and preserves the selected locale', () => {
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <LanguageSettings />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    expect(screen.getAllByRole('radio')).toHaveLength(4);
    expect(screen.getByRole('radio', {name: 'Русский'})).toBeChecked();
    fireEvent.click(screen.getByRole('radio', {name: 'Қазақша'}));
    expect(changeLocale).toHaveBeenCalledWith('kk');
    expect(screen.getByRole('radio', {name: 'Русский'})).toBeChecked();
});
