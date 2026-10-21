import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen} from '@testing-library/react';
import {useState} from 'react';
import {expect, it, vi} from 'vitest';
import {ProactiveSettings} from '@/modules/settings/ProactiveSettings/ProactiveSettings';
import {createSettingsDraft} from '@/modules/settings/model/settingsDraft';
import {workspaceState} from '@tests/fixtures/workspace';
import ru from '@/shared/i18n/ru.json';
it('defaults proactive conversation to off and stores the selected interval in minutes', () => {
    const changed = vi.fn();
    function Form() {
        const [value, setValue] = useState(createSettingsDraft(workspaceState.settings));
        return (
            <ProactiveSettings
                value={value}
                onUpdate={(next) => {
                    changed(next);
                    setValue(next);
                }}
            />
        );
    }
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <Form />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    expect(screen.getByRole('switch', {name: ru.proactive.title})).not.toBeChecked();
    expect(screen.getByRole('spinbutton', {name: ru.proactive.interval})).toBeDisabled();
    fireEvent.click(screen.getByRole('switch', {name: ru.proactive.title}));
    fireEvent.change(screen.getByRole('spinbutton', {name: ru.proactive.interval}), {target: {value: '2'}});
    expect(changed).toHaveBeenLastCalledWith(
        expect.objectContaining({proactiveEnabled: true, proactiveIntervalMinutes: 120}),
    );
});
