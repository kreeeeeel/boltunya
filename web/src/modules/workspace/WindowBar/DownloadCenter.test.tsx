import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {beforeEach, expect, it, vi} from 'vitest';
import {DownloadCenter} from '@/modules/workspace/WindowBar/DownloadCenter';
import {api} from '@/shared/api/client';
import {workspaceState} from '@tests/fixtures/workspace';
import {speechState} from '@tests/fixtures/speech';
import ru from '@/shared/i18n/ru.json';

const mocks = vi.hoisted(() => ({state: undefined as unknown}));
vi.mock('@/modules/speech/model/useSpeechState', () => ({
    useSpeechState: () => ({state: mocks.state}),
}));

beforeEach(() => {
    mocks.state = undefined;
});

function renderDownload(engine = workspaceState.engine) {
    const onError = vi.fn();
    const view = render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <DownloadCenter engine={engine} onError={onError} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    return {view, onError};
}

it('stays absent with no active transfer and cancels only the current model download', async () => {
    const {view: idle} = renderDownload();
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    idle.unmount();
    const cancel = vi.spyOn(api.models, 'cancelModelDownload').mockResolvedValue({ok: true});
    renderDownload({...workspaceState.engine, downloading: 'qwen3:1.7b', progress: 38});
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '38');
    fireEvent.click(screen.getByRole('button', {name: ru.cancelDownload}));
    await waitFor(() => expect(cancel).toHaveBeenCalledOnce());
});

it('shows speech downloads across pages and reports cancellation failures', async () => {
    mocks.state = {
        ...speechState,
        downloads: [{assetId: 'piper-ru-irina', phase: 'downloading', downloadedBytes: 10, totalBytes: 40}],
    };
    vi.spyOn(api.speech, 'cancelSpeechAsset').mockRejectedValue(new Error('Synthetic cancellation error'));
    const {onError} = renderDownload();
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '25');
    fireEvent.click(screen.getByRole('button', {name: ru.cancelDownload}));
    await waitFor(() => expect(onError).toHaveBeenCalledWith('Synthetic cancellation error'));
});
