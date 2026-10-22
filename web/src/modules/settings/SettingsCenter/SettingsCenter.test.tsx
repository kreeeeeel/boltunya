import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor, within} from '@testing-library/react';
import {beforeEach, expect, it, vi} from 'vitest';
import {useState} from 'react';
import {SettingsCenter} from '@/modules/settings/SettingsCenter/SettingsCenter';
import {workspaceState} from '@tests/fixtures/workspace';
import ru from '@/shared/i18n/ru.json';
import {speechState} from '@tests/fixtures/speech';
import {speechCompute} from '@tests/fixtures/speechCompute';
import {computeDevices} from '@tests/fixtures/computeDevices';
const mocks = vi.hoisted(() => ({
    settings: vi.fn(),
    updateChatMemory: vi.fn(),
    updateGlobalMemory: vi.fn(),
    speechState: vi.fn(),
    getSpeechComputeStatus: vi.fn(),
}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: {
        settings: {
            settings: mocks.settings,
        },
        speech: {speechState: mocks.speechState, getSpeechComputeStatus: mocks.getSpeechComputeStatus},
        models: {engineDevices: async () => computeDevices},
        memory: {
            updateChatMemory: mocks.updateChatMemory,
            updateGlobalMemory: mocks.updateGlobalMemory,
        },
    },
}));
vi.mock('@/modules/settings/ModelPromptEditor/ModelPromptEditor', () => ({ModelPromptEditor: () => null}));
beforeEach(() => {
    vi.clearAllMocks();
    mocks.getSpeechComputeStatus.mockReset().mockResolvedValue(speechCompute);
});

it('saves CUDA only through the shared footer and keeps the dialog and preview text after persistence', async () => {
    const persisted = {...workspaceState.settings, voiceConversionDevice: 'cpu' as const};
    mocks.speechState.mockResolvedValue(speechState);
    mocks.settings.mockReset().mockImplementation(async ({settingsUpdate}) => {
        Object.assign(persisted, settingsUpdate);
        return persisted;
    });
    function Example() {
        const [settings, setSettings] = useState<typeof workspaceState.settings>(persisted);
        return (
            <NextIntlClientProvider locale="ru" messages={ru}>
                <ThemeProvider>
                    <SettingsCenter
                        settings={settings}
                        initialSection="voice"
                        onClose={vi.fn()}
                        onRefresh={async () => setSettings({...persisted})}
                    />
                </ThemeProvider>
            </NextIntlClientProvider>
        );
    }
    const view = render(<Example />);
    const text = screen.getByRole('textbox', {name: ru.voice.previewText});
    fireEvent.change(text, {target: {value: 'Preview text survives saving'}});
    expect(mocks.getSpeechComputeStatus).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('menuitem', {name: ru.settingsCenter.sections.compute}));
    await screen.findByText(ru.voice.rvcDevice.available);
    fireEvent.click(screen.getByRole('combobox', {name: ru.voice.rvcDevice.label}));
    fireEvent.click(await screen.findByRole('option', {name: ru.voice.rvcDevice.cuda}));
    expect(mocks.settings).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('menuitem', {name: ru.settingsCenter.sections.voice}));
    expect(screen.getByRole('button', {name: ru.voice.previewCheck})).toBeDisabled();
    expect(screen.getByText(ru.voice.rvcDevice.saveBeforePreview)).toBeVisible();
    fireEvent.click(screen.getByRole('button', {name: ru.save}));
    await waitFor(() => expect(mocks.settings).toHaveBeenCalledTimes(1));
    expect(mocks.settings.mock.calls[0][0].settingsUpdate.voiceConversionDevice).toBe('cuda');
    await waitFor(() => expect(screen.getByRole('button', {name: ru.voice.previewCheck})).toBeEnabled());
    expect(screen.getByRole('textbox', {name: ru.voice.previewText})).toHaveValue(
        'Preview text survives saving',
    );
    expect(screen.getByRole('dialog', {name: ru.settings})).toBeVisible();
    expect(mocks.getSpeechComputeStatus).toHaveBeenCalledTimes(1);
    expect(persisted.paused).toBe(true);
    view.unmount();
    render(<Example />);
    fireEvent.click(screen.getByRole('menuitem', {name: ru.settingsCenter.sections.compute}));
    expect(await screen.findByRole('combobox', {name: ru.voice.rvcDevice.label})).toHaveTextContent(
        ru.voice.rvcDevice.cuda,
    );
});
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

it('keeps the voice preferences as a shared draft and saves them without enabling automatic replies', async () => {
    mocks.settings.mockResolvedValue({ok: true});
    mocks.speechState.mockResolvedValue(speechState);
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <SettingsCenter
                    settings={workspaceState.settings}
                    initialSection="voice"
                    onClose={vi.fn()}
                    onRefresh={async () => {}}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    expect(screen.queryByRole('combobox', {name: ru.voice.replyMode})).not.toBeInTheDocument();
    expect(screen.queryByRole('tab')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', {name: ru.voice.importModel})).not.toBeInTheDocument();
    expect(screen.getByRole('switch', {name: ru.voice.recognitionEnabled})).not.toBeChecked();
    fireEvent.click(screen.getByRole('switch', {name: ru.voice.recognitionEnabled}));
    fireEvent.click(screen.getByRole('menuitem', {name: ru.settingsCenter.sections.replies}));
    fireEvent.click(screen.getByRole('combobox', {name: ru.voice.replyMode}));
    fireEvent.click(await screen.findByRole('option', {name: ru.voice.replyModes.mirror}));
    fireEvent.click(screen.getByRole('menuitem', {name: ru.settingsCenter.sections.application}));
    fireEvent.click(screen.getByRole('menuitem', {name: ru.settingsCenter.sections.voice}));
    expect(screen.getByRole('switch', {name: ru.voice.recognitionEnabled})).toBeChecked();
    fireEvent.click(screen.getByRole('button', {name: ru.save}));
    await waitFor(() =>
        expect(mocks.settings).toHaveBeenCalledWith(
            expect.objectContaining({
                settingsUpdate: expect.objectContaining({
                    voiceInputEnabled: true,
                    voiceReplyMode: 'mirror',
                    voiceSynthesisModel: 'piper-ru-irina',
                }),
            }),
        ),
    );
    expect(screen.getByRole('dialog', {name: ru.settings})).toBeVisible();
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

it('preserves dirty settings before navigating to the separate speech library', async () => {
    mocks.settings.mockResolvedValue({ok: true});
    mocks.speechState.mockResolvedValue(speechState);
    const open = vi.fn();
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <SettingsCenter
                    settings={workspaceState.settings}
                    initialSection="voice"
                    onClose={vi.fn()}
                    onRefresh={async () => {}}
                    onOpenSpeechLibrary={open}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    const library = screen.getByRole('button', {name: ru.voice.openLibrary});
    expect(library).toBeEnabled();
    fireEvent.click(screen.getByRole('switch', {name: ru.voice.recognitionEnabled}));
    expect(library).toBeDisabled();
    expect(open).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', {name: ru.save}));
    await waitFor(() => expect(library).toBeEnabled());
    fireEvent.click(library);
    expect(open).toHaveBeenCalledOnce();
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
