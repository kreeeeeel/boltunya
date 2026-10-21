import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {SpeechAssetCard} from '@/modules/speech/SpeechAssetCard/SpeechAssetCard';
import {speechState} from '@tests/fixtures/speech';
import ru from '@/shared/i18n/ru.json';

it('keeps download progress and cancellation inside one button without a second progress block', () => {
    const cancel = vi.fn();
    const asset = {...speechState.assets[2], installed: false};
    const view = render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <SpeechAssetCard
                    asset={asset}
                    download={{
                        assetId: asset.id,
                        phase: 'downloading',
                        downloadedBytes: 25,
                        totalBytes: 100,
                        error: '',
                    }}
                    missing={[]}
                    busy={false}
                    onInstall={vi.fn()}
                    onCancel={cancel}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    const button = screen.getByRole('button', {name: ru.voice.cancelAsset.replace('{name}', asset.title)});
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '25');
    expect(button).toContainElement(screen.getByRole('progressbar'));
    expect(view.container.querySelectorAll('[role="progressbar"]')).toHaveLength(1);
    expect(screen.getByText('25%', {exact: true})).toBeVisible();
    fireEvent.click(button);
    expect(cancel).toHaveBeenCalledOnce();
});

it('offers explicit choose and real preview actions only when a voice and its dependencies are ready', () => {
    const choose = vi.fn();
    const preview = vi.fn();
    function surface(missing: string[], selected = false) {
        return (
            <NextIntlClientProvider locale="ru" messages={ru}>
                <ThemeProvider>
                    <SpeechAssetCard
                        asset={speechState.assets[2]}
                        missing={missing}
                        busy={false}
                        selected={selected}
                        onInstall={vi.fn()}
                        onCancel={vi.fn()}
                        onSelect={choose}
                        onPreview={preview}
                    />
                </ThemeProvider>
            </NextIntlClientProvider>
        );
    }
    const view = render(surface(['Runtime']));
    expect(
        screen.getByRole('button', {
            name: ru.voice.selectAsset.replace('{name}', speechState.assets[2].title),
        }),
    ).toBeDisabled();
    expect(
        screen.getByRole('button', {
            name: ru.voice.previewAsset.replace('{name}', speechState.assets[2].title),
        }),
    ).toBeDisabled();
    view.rerender(surface([]));
    expect(
        screen.getByRole('button', {
            name: ru.voice.selectAsset.replace('{name}', speechState.assets[2].title),
        }),
    ).toHaveClass('g-button_size_l');
    fireEvent.click(
        screen.getByRole('button', {
            name: ru.voice.selectAsset.replace('{name}', speechState.assets[2].title),
        }),
    );
    fireEvent.click(
        screen.getByRole('button', {
            name: ru.voice.previewAsset.replace('{name}', speechState.assets[2].title),
        }),
    );
    expect(choose).toHaveBeenCalledOnce();
    expect(preview).toHaveBeenCalledOnce();
    view.rerender(surface([], true));
    expect(
        screen.getByRole('button', {
            name: ru.voice.selectedAsset.replace('{name}', speechState.assets[2].title),
        }),
    ).toBeDisabled();
});
