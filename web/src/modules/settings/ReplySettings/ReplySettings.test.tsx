import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen} from '@testing-library/react';
import {expect, it, vi} from 'vitest';

import {ReplySettings} from '@/modules/settings/ReplySettings/ReplySettings';
import {createSettingsDraft} from '@/modules/settings/model/settingsDraft';
import {workspaceState} from '@tests/fixtures/workspace';
import ru from '@/shared/i18n/ru.json';

it('keeps reply controls separate while preserving proactive preferences in the shared draft', () => {
    const onUpdate = vi.fn();
    const value = {
        ...createSettingsDraft(workspaceState.settings),
        proactiveEnabled: true,
        proactiveIntervalMinutes: 45,
    };
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ReplySettings value={value} onUpdate={onUpdate} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    expect(screen.queryByRole('switch', {name: ru.proactive.title})).toBeNull();
    fireEvent.click(screen.getByRole('switch', {name: ru.showTyping}));
    expect(onUpdate).toHaveBeenCalledWith(
        expect.objectContaining({
            proactiveEnabled: true,
            proactiveIntervalMinutes: 45,
            showTyping: false,
        }),
    );
});
