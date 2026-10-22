import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {SpeechAssets} from '@/modules/speech/SpeechAssets/SpeechAssets';
import {speechState} from '@tests/fixtures/speech';
import ru from '@/shared/i18n/ru.json';

it.each([
    ['SPEECH_INSTALL_FAILED', ru.voice.downloadFailed],
    ['Connection lost', 'Connection lost'],
])(
    'renders progress and localizes the known download error %s without automatic installs',
    (error, message) => {
        const state = structuredClone(speechState);
        state.assets[1].installed = false;
        state.assets[2].installed = false;
        state.downloads = [
            {assetId: 'whisper-tiny', phase: 'downloading', downloadedBytes: 25, totalBytes: 100, error: ''},
            {
                assetId: 'piper-ru-irina',
                phase: 'error',
                downloadedBytes: 0,
                totalBytes: 0,
                error,
            },
        ];
        const install = vi.fn();
        const cancel = vi.fn();
        render(
            <NextIntlClientProvider locale="ru" messages={ru}>
                <ThemeProvider>
                    <SpeechAssets
                        state={state}
                        error=""
                        busy=""
                        reload={vi.fn()}
                        install={install}
                        cancel={cancel}
                    />
                </ThemeProvider>
            </NextIntlClientProvider>,
        );
        expect(install).not.toHaveBeenCalled();
        expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '25');
        expect(screen.getByText(ru.voice.installed)).toBeVisible();
        expect(screen.getByRole('alert')).toHaveTextContent(message);
        fireEvent.click(
            screen.getByRole('button', {name: ru.voice.cancelAsset.replace('{name}', state.assets[1].title)}),
        );
        expect(cancel).toHaveBeenCalledWith('whisper-tiny');
        fireEvent.click(
            screen.getByRole('button', {
                name: ru.voice.installAsset.replace('{name}', state.assets[2].title),
            }),
        );
        expect(install).toHaveBeenCalledWith('piper-ru-irina');
    },
);

it('labels imported voices without inventing download actions or empty source links', () => {
    const asset = {
        ...speechState.assets[2],
        id: 'custom-voice',
        title: 'Мой голос',
        userProvided: true,
        sourceUrl: '',
        license: 'Local license',
    };
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <SpeechAssets
                    state={{assets: [asset], downloads: []}}
                    error=""
                    busy=""
                    reload={vi.fn()}
                    install={vi.fn()}
                    cancel={vi.fn()}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    expect(screen.getByText(ru.voice.imported, {exact: true})).toBeVisible();
    expect(screen.getByText('Local license', {exact: true})).toBeVisible();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', {name: ru.voice.download})).not.toBeInTheDocument();
});

it('keeps search and language filters independent per tab without changing the selected model', async () => {
    const state = {
        ...speechState,
        assets: [
            ...speechState.assets,
            {...speechState.assets[2], id: 'english-voice', title: 'English Voice', languages: ['en']},
        ],
    };
    const select = vi.fn();
    function surface(kind: 'runtime' | 'recognition' | 'synthesis') {
        return (
            <NextIntlClientProvider locale="ru" messages={ru}>
                <ThemeProvider>
                    <SpeechAssets
                        state={state}
                        error=""
                        busy=""
                        reload={vi.fn()}
                        install={vi.fn()}
                        cancel={vi.fn()}
                        kind={kind}
                        selected={{synthesis: 'piper-ru-irina'}}
                        onSelect={select}
                    />
                </ThemeProvider>
            </NextIntlClientProvider>
        );
    }
    const view = render(surface('synthesis'));
    const search = screen.getByRole('textbox', {name: ru.voice.searchModels});
    fireEvent.change(search, {target: {value: 'PIPER'}});
    expect(screen.queryByText('English Voice', {exact: true})).not.toBeInTheDocument();
    fireEvent.change(search, {target: {value: ''}});
    fireEvent.click(screen.getByRole('combobox', {name: ru.voice.languageFilter}));
    fireEvent.click(await screen.findByRole('option', {name: 'английский'}));
    expect(screen.getByText('English Voice', {exact: true})).toBeVisible();
    expect(screen.queryByText(speechState.assets[2].title, {exact: true})).not.toBeInTheDocument();
    expect(select).not.toHaveBeenCalled();
    view.rerender(surface('runtime'));
    expect(screen.getByText(speechState.assets[0].title, {exact: true})).toBeVisible();
    expect(screen.queryByRole('combobox', {name: ru.voice.languageFilter})).not.toBeInTheDocument();
    view.rerender(surface('synthesis'));
    expect(screen.getByText('English Voice', {exact: true})).toBeVisible();
    expect(screen.queryByText(speechState.assets[2].title, {exact: true})).not.toBeInTheDocument();
});

it('filters installed voices without installing or changing the selected voice', () => {
    const pending = {...speechState.assets[2], id: 'pending-voice', title: 'Pending voice', installed: false};
    const install = vi.fn();
    const select = vi.fn();
    const view = render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <SpeechAssets
                    state={{assets: [...speechState.assets, pending], downloads: []}}
                    error=""
                    busy=""
                    reload={vi.fn()}
                    install={install}
                    cancel={vi.fn()}
                    kind="synthesis"
                    selected={{synthesis: 'piper-ru-irina'}}
                    onSelect={select}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    expect(view.container.querySelectorAll('[data-qa="speech-asset"]')).toHaveLength(2);
    fireEvent.click(screen.getByRole('switch', {name: ru.voice.installedOnly}));
    expect(view.container.querySelectorAll('[data-qa="speech-asset"]')).toHaveLength(1);
    expect(screen.queryByText('Pending voice')).toBeNull();
    expect(screen.getByText(speechState.assets[2].title)).toBeVisible();
    expect(install).not.toHaveBeenCalled();
    expect(select).not.toHaveBeenCalled();
});
