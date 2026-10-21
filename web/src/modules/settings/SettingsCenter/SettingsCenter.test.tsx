import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor, within} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {useState} from 'react';
import {SettingsCenter} from '@/modules/settings/SettingsCenter/SettingsCenter';
import {workspaceState} from '@tests/fixtures/workspace';
import ru from '@/shared/i18n/ru.json';
const mocks = vi.hoisted(() => ({PATCH: vi.fn()}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: mocks,
}));
vi.mock('@/modules/settings/ModelPromptEditor/ModelPromptEditor', () => ({ModelPromptEditor: () => null}));
it('preserves section drafts and success feedback when refreshed settings arrive', async () => {
    const onClose = vi.fn();
    mocks.PATCH.mockResolvedValue({data: {ok: true}, response: new Response()});
    const original = {
        ...workspaceState.settings,
        customStyles: [{id: 'style-1', emoji: '🧊', name: 'Спокойный', instructions: 'Коротко и спокойно.'}],
    };
    function Example() {
        const [settings, setSettings] = useState(original);
        return (
            <NextIntlClientProvider locale="ru" messages={ru}>
                <ThemeProvider>
                    <SettingsCenter
                        settings={settings}
                        initialSection="styles"
                        onClose={onClose}
                        onRefresh={async () => setSettings(structuredClone(original))}
                    />
                </ThemeProvider>
            </NextIntlClientProvider>
        );
    }
    render(<Example />);
    fireEvent.change(screen.getByRole('textbox', {name: ru.styleName}), {target: {value: 'Сдержанный'}});
    fireEvent.click(screen.getByRole('menuitem', {name: ru.settingsCenter.sections.prompt}));
    fireEvent.change(screen.getByRole('textbox', {name: ru.globalPrompt}), {
        target: {value: 'Без канцелярита.'},
    });
    fireEvent.click(screen.getByRole('menuitem', {name: ru.settingsCenter.sections.styles}));
    expect(screen.getByRole('textbox', {name: ru.styleName})).toHaveValue('Сдержанный');
    const region = screen.getByRole('region', {name: ru.settingsCenter.sections.styles});
    fireEvent.click(within(region).getByRole('button', {name: ru.save}));
    await waitFor(() =>
        expect(mocks.PATCH).toHaveBeenCalledWith(
            '/api/settings',
            expect.objectContaining({
                body: expect.objectContaining({
                    globalPrompt: 'Без канцелярита.',
                    customStyles: [expect.objectContaining({name: 'Сдержанный'})],
                }),
            }),
        ),
    );
    expect(await within(region).findByRole('button', {name: ru.saved})).toBeVisible();
    expect(onClose).not.toHaveBeenCalled();
    fireEvent.change(screen.getByRole('textbox', {name: ru.styleName}), {target: {value: 'Новый'}});
    expect(within(region).queryByRole('button', {name: ru.saved})).toBeNull();
});

it('shows a save failure once in the active section while preserving visited drafts', async () => {
    mocks.PATCH.mockRejectedValue(new Error('Cannot save'));
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <SettingsCenter settings={workspaceState.settings} onClose={vi.fn()} onRefresh={vi.fn()} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    fireEvent.click(screen.getByRole('menuitem', {name: ru.settingsCenter.sections.replies}));
    fireEvent.click(screen.getByRole('button', {name: ru.save}));
    expect(await screen.findByRole('alert')).toHaveTextContent('Cannot save');
    expect(screen.getAllByText('Cannot save')).toHaveLength(1);
    fireEvent.click(screen.getByRole('menuitem', {name: ru.settingsCenter.sections.prompt}));
    expect(screen.getAllByText('Cannot save')).toHaveLength(1);
});

it('keeps conversation support in a separate section and saves its draft after navigation', async () => {
    mocks.PATCH.mockResolvedValue({data: {ok: true}, response: new Response()});
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <SettingsCenter settings={workspaceState.settings} onClose={vi.fn()} onRefresh={vi.fn()} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    fireEvent.click(screen.getByRole('menuitem', {name: ru.settingsCenter.sections.replies}));
    expect(screen.queryByRole('switch', {name: ru.proactive.title})).toBeNull();
    fireEvent.click(screen.getByRole('menuitem', {name: ru.settingsCenter.sections.proactive}));
    fireEvent.click(screen.getByRole('switch', {name: ru.proactive.title}));
    fireEvent.change(screen.getByRole('spinbutton', {name: ru.proactive.interval}), {target: {value: '2'}});
    fireEvent.click(screen.getByRole('menuitem', {name: ru.settingsCenter.sections.replies}));
    const region = screen.getByRole('region', {name: ru.settingsCenter.sections.replies});
    fireEvent.click(within(region).getByRole('button', {name: ru.save}));
    await waitFor(() =>
        expect(mocks.PATCH).toHaveBeenCalledWith(
            '/api/settings',
            expect.objectContaining({
                body: expect.objectContaining({proactiveEnabled: true, proactiveIntervalMinutes: 120}),
            }),
        ),
    );
});
