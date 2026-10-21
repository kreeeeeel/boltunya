import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {ProfileMenu} from '@/modules/settings/ProfileMenu/ProfileMenu';
import {workspaceState} from '@tests/fixtures/workspace';
import ru from '@/shared/i18n/ru.json';

it('offers one settings entry and account actions without duplicate section shortcuts', async () => {
    const onSettings = vi.fn();
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ProfileMenu
                    telegram={workspaceState.telegram}
                    onSettings={onSettings}
                    onLogout={vi.fn()}
                    onQuit={vi.fn()}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    fireEvent.click(screen.getByRole('button', {name: ru.profileMenu}));
    expect((await screen.findAllByRole('menuitem')).map((item) => item.textContent).filter(Boolean)).toEqual([
        ru.settings,
        ru.logout,
        ru.quit,
    ]);
    fireEvent.click(screen.getByRole('menuitem', {name: ru.settings}));
    expect(onSettings).toHaveBeenCalledWith('application');
});
