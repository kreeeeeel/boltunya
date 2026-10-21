import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor, within} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {useState} from 'react';
import {SettingsCenter} from '@/modules/settings/SettingsCenter/SettingsCenter';
import {workspaceState} from '@tests/fixtures/workspace';
import ru from '@/shared/i18n/ru.json';
const mocks = vi.hoisted(() => ({settings: vi.fn(), updateChatMemory: vi.fn(), updateGlobalMemory: vi.fn()}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: {
        settings: {
            settings: mocks.settings,
        },
        memory: {
            updateChatMemory: mocks.updateChatMemory,
            updateGlobalMemory: mocks.updateGlobalMemory,
        },
    },
}));
vi.mock('@/modules/settings/ModelPromptEditor/ModelPromptEditor', () => ({ModelPromptEditor: () => null}));
it('preserves section drafts and success feedback when refreshed settings arrive', async () => {
    const onClose = vi.fn();
    mocks.settings.mockResolvedValue({ok: true});
    mocks.updateChatMemory.mockResolvedValue({ok: true});
    mocks.updateGlobalMemory.mockResolvedValue({ok: true});
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
        expect(mocks.settings).toHaveBeenCalledWith(
            expect.objectContaining({
                settingsUpdate: expect.objectContaining({
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
    mocks.settings.mockRejectedValue(new Error('Cannot save'));
    mocks.updateChatMemory.mockRejectedValue(new Error('Cannot save'));
    mocks.updateGlobalMemory.mockRejectedValue(new Error('Cannot save'));
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
    mocks.settings.mockResolvedValue({ok: true});
    mocks.updateChatMemory.mockResolvedValue({ok: true});
    mocks.updateGlobalMemory.mockResolvedValue({ok: true});
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
        expect(mocks.settings).toHaveBeenCalledWith(
            expect.objectContaining({
                settingsUpdate: expect.objectContaining({
                    proactiveEnabled: true,
                    proactiveIntervalMinutes: 120,
                }),
            }),
        ),
    );
});

it('preserves the opt-in formatting draft across navigation and saves without closing', async () => {
    mocks.settings.mockResolvedValue({ok: true});
    const close = vi.fn();
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <SettingsCenter
                    settings={workspaceState.settings}
                    initialSection="replies"
                    onClose={close}
                    onRefresh={async () => {}}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    expect(screen.getByRole('switch', {name: ru.formatReplies})).not.toBeChecked();
    fireEvent.click(screen.getByRole('switch', {name: ru.formatReplies}));
    fireEvent.click(screen.getByRole('combobox', {name: ru.replyInterruption.title}));
    fireEvent.click(await screen.findByRole('option', {name: ru.replyInterruption.options.finish}));
    fireEvent.click(screen.getByRole('menuitem', {name: ru.settingsCenter.sections.application}));
    fireEvent.click(screen.getByRole('menuitem', {name: ru.settingsCenter.sections.replies}));
    expect(screen.getByRole('switch', {name: ru.formatReplies})).toBeChecked();
    expect(screen.getByRole('combobox', {name: ru.replyInterruption.title})).toHaveTextContent(
        ru.replyInterruption.options.finish,
    );
    const region = screen.getByRole('region', {name: ru.settingsCenter.sections.replies});
    fireEvent.click(within(region).getByRole('button', {name: ru.save}));
    await waitFor(() =>
        expect(mocks.settings).toHaveBeenCalledWith(
            expect.objectContaining({
                settingsUpdate: expect.objectContaining({
                    formatReplies: true,
                    replyProcessingMode: 'finish',
                }),
            }),
        ),
    );
    expect(await within(region).findByRole('button', {name: ru.saved})).toBeVisible();
    expect(screen.queryByRole('alert')).toBeNull();
    expect(close).not.toHaveBeenCalled();
});
