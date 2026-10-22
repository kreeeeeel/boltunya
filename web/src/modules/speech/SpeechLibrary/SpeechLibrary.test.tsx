import {Box, ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor, within} from '@testing-library/react';
import type {Ref} from 'react';
import {expect, it, vi, beforeEach} from 'vitest';
import {SpeechLibrary} from '@/modules/speech/SpeechLibrary/SpeechLibrary';
import type {SpeechPreviewHandle} from '@/modules/speech/SpeechPreview/SpeechPreview';
import {workspaceState} from '@tests/fixtures/workspace';
import {speechState} from '@tests/fixtures/speech';
import ru from '@/shared/i18n/ru.json';

const mocks = vi.hoisted(() => ({save: vi.fn(), check: vi.fn(), refresh: vi.fn(), install: vi.fn()}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: {settings: {settings: mocks.save}},
}));
vi.mock('@/modules/speech/model/useSpeechState', () => ({
    useSpeechState: () => ({
        state: {
            ...speechState,
            assets: [
                ...speechState.assets,
                {...speechState.assets[2], id: 'second-voice', title: 'Второй голос'},
            ],
        },
        error: '',
        busy: '',
        reload: vi.fn(),
        install: mocks.install,
        cancel: vi.fn(),
    }),
}));
vi.mock('@/modules/speech/model/useSpeechStorage', () => ({
    useSpeechStorage: () => ({desktop: false, browse: undefined, reload: vi.fn(), error: '', busy: false}),
}));
vi.mock('@/modules/speech/SpeechPreview/SpeechPreview', async () => {
    const {useImperativeHandle} = await import('react');
    return {
        SpeechPreview: ({ref, modelId}: {ref: Ref<SpeechPreviewHandle>; modelId: string}) => {
            useImperativeHandle(ref, () => ({check: mocks.check}));
            return <Box data-testid="preview-model">{modelId}</Box>;
        },
    };
});
beforeEach(() => {
    vi.clearAllMocks();
    mocks.save.mockResolvedValue({ok: true});
    HTMLElement.prototype.scrollIntoView = vi.fn();
});
function setup() {
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <SpeechLibrary settings={workspaceState.settings} onRefresh={mocks.refresh} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
}

it('exposes the catalog as a page and changes the default only on an explicit choose action', async () => {
    setup();
    expect(screen.getByRole('heading', {name: ru.voice.libraryTitle})).toBeVisible();
    expect(screen.getAllByRole('tab')).toHaveLength(3);
    expect(screen.getByRole('button', {name: ru.voice.refreshList})).toBeVisible();
    expect(mocks.install).not.toHaveBeenCalled();
    expect(mocks.save).not.toHaveBeenCalled();
    fireEvent.click(
        screen.getByRole('button', {name: ru.voice.selectAsset.replace('{name}', 'Второй голос')}),
    );
    await waitFor(() =>
        expect(mocks.save).toHaveBeenCalledWith({settingsUpdate: {voiceSynthesisModel: 'second-voice'}}),
    );
    await waitFor(() => expect(mocks.refresh).toHaveBeenCalledOnce());
    expect(screen.queryByTestId('preview-model')).not.toBeInTheDocument();
});

it('opens an alternative voice preview separately and closes it without changing the global voice', async () => {
    setup();
    expect(screen.queryByTestId('preview-model')).not.toBeInTheDocument();
    screen.getAllByRole('tab').forEach((tab) => expect(tab.querySelector('svg')).toBeNull());
    fireEvent.click(
        screen.getByRole('button', {name: ru.voice.previewAsset.replace('{name}', 'Второй голос')}),
    );
    await waitFor(() => expect(mocks.check).toHaveBeenCalledWith('second-voice'));
    const dialog = screen.getByRole('dialog', {name: ru.voice.previewTitle});
    expect(screen.getByTestId('preview-model')).toHaveTextContent('second-voice');
    expect(mocks.save).not.toHaveBeenCalled();
    fireEvent.click(within(dialog).getByRole('button', {name: ru.close}));
    await waitFor(() => expect(screen.queryByTestId('preview-model')).not.toBeInTheDocument());
});
