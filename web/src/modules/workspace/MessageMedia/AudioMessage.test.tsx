import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {afterEach, expect, it, vi} from 'vitest';
import type {Message} from '@/shared/api/client';
import {AudioMessage} from '@/modules/workspace/MessageMedia/AudioMessage';
import ru from '@/shared/i18n/ru.json';

const message: Message = {
    id: '9007199254740993',
    chatId: '42',
    text: '',
    outgoing: true,
    date: 1,
    kind: 'voice',
    media: {
        fileId: 7,
        mimeType: 'audio/ogg',
        fileName: 'voice.ogg',
        width: 0,
        height: 0,
        size: 100,
        thumbnailFileId: 0,
        duration: 5,
        waveform: [31, 85, 127],
        cacheVersion: 2,
    },
};

function element(value: Message) {
    return (
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <AudioMessage message={value} />
            </ThemeProvider>
        </NextIntlClientProvider>
    );
}

afterEach(() => vi.restoreAllMocks());

it('uses the current media cache revision and resets failed playback when the source changes', () => {
    vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {});
    const view = render(element(message));
    const original = view.container.querySelector('audio')!;
    expect(original).toHaveAttribute('src', '/api/media/7?v=2');
    fireEvent.error(original);
    expect(screen.getByText(ru.mediaError)).toBeVisible();

    view.rerender(element({...message, media: {...message.media!, cacheVersion: 3, duration: 8}}));
    const current = view.container.querySelector('audio')!;
    expect(current).not.toBe(original);
    expect(current).toHaveAttribute('src', '/api/media/7?v=3');
    expect(screen.queryByText(ru.mediaError)).toBeNull();
    expect(screen.getByRole('slider', {name: ru.audioPosition})).toHaveAttribute('aria-valuemax', '8');
    expect(HTMLMediaElement.prototype.pause).toHaveBeenCalled();
});

it('reloads failed media before a user retry instead of replaying an unusable audio element', async () => {
    vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {});
    const load = vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(() => {});
    const play = vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue(undefined);
    const view = render(element(message));
    fireEvent.error(view.container.querySelector('audio')!);
    fireEvent.click(screen.getByRole('button', {name: ru.playAudio}));
    await waitFor(() => expect(screen.queryByText(ru.mediaError)).toBeNull());
    expect(load).toHaveBeenCalledTimes(1);
    expect(play).toHaveBeenCalledTimes(1);
});

it('reloads a completed outgoing upload with the same native file id and cache revision', () => {
    vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {});
    const load = vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(function (
        this: HTMLMediaElement,
    ) {
        fireEvent.loadStart(this);
    });
    const view = render(
        element({...message, media: {...message.media!, downloaded: false, uniqueId: 'native-voice'}}),
    );
    const pending = view.container.querySelector('audio')!;
    fireEvent.error(pending);
    expect(screen.getByText(ru.mediaError)).toBeVisible();

    view.rerender(
        element({...message, media: {...message.media!, downloaded: true, uniqueId: 'native-voice'}}),
    );
    const delivered = view.container.querySelector('audio')!;
    expect(delivered).toBe(pending);
    expect(delivered).toHaveAttribute('src', pending.getAttribute('src'));
    expect(load).toHaveBeenCalledTimes(1);
    expect(screen.queryByText(ru.mediaError)).toBeNull();
});

it('keeps the playing audio and current position when the first download completes', () => {
    const pause = vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {});
    const load = vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(() => {});
    const view = render(element({...message, media: {...message.media!, downloaded: false}}));
    const playing = view.container.querySelector('audio')!;
    Object.defineProperty(playing, 'paused', {value: false});
    playing.currentTime = 2;
    fireEvent.play(playing);
    fireEvent.timeUpdate(playing);
    expect(screen.getByRole('button', {name: ru.pauseAudio})).toBeVisible();

    view.rerender(element({...message, media: {...message.media!, downloaded: true}}));
    expect(view.container.querySelector('audio')).toBe(playing);
    expect(playing.currentTime).toBe(2);
    expect(screen.getByRole('slider', {name: ru.audioPosition})).toHaveAttribute('aria-valuenow', '2');
    expect(pause).not.toHaveBeenCalled();
    expect(load).not.toHaveBeenCalled();
});

it('preserves playback when native acknowledgement only fills in the previously unknown file identity', () => {
    const pause = vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {});
    const load = vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(() => {});
    const view = render(element({...message, media: {...message.media!, downloaded: false, uniqueId: ''}}));
    const audio = view.container.querySelector('audio')!;
    Object.defineProperty(audio, 'paused', {value: false});
    audio.currentTime = 2;
    fireEvent.play(audio);
    fireEvent.timeUpdate(audio);

    view.rerender(
        element({...message, media: {...message.media!, downloaded: true, uniqueId: 'native-file'}}),
    );
    expect(view.container.querySelector('audio')).toBe(audio);
    expect(audio.currentTime).toBe(2);
    expect(screen.getByRole('button', {name: ru.pauseAudio})).toBeVisible();
    expect(pause).not.toHaveBeenCalled();
    expect(load).not.toHaveBeenCalled();
});

it('resets playback when two known remote identities reuse the same native file id', () => {
    vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {});
    const load = vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(function (
        this: HTMLMediaElement,
    ) {
        this.currentTime = 0;
        fireEvent.emptied(this);
        fireEvent.loadStart(this);
    });
    const view = render(element({...message, media: {...message.media!, uniqueId: 'first-file'}}));
    const audio = view.container.querySelector('audio')!;
    audio.currentTime = 2;
    fireEvent.play(audio);
    fireEvent.timeUpdate(audio);

    view.rerender(element({...message, media: {...message.media!, uniqueId: 'different-file'}}));
    expect(view.container.querySelector('audio')).toBe(audio);
    expect(load).toHaveBeenCalledTimes(1);
    expect(audio.currentTime).toBe(0);
    expect(screen.getByRole('slider', {name: ru.audioPosition})).toHaveAttribute('aria-valuenow', '0');
    expect(screen.getByRole('button', {name: ru.playAudio})).toBeVisible();
});

it('updates position from real playback and keeps keyboard seeking within the duration', () => {
    vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {});
    const view = render(element(message));
    const audio = view.container.querySelector('audio')!;
    Object.defineProperty(audio, 'readyState', {value: 1});
    audio.currentTime = 4;
    fireEvent.timeUpdate(audio);
    const slider = screen.getByRole('slider', {name: ru.audioPosition});
    expect(slider).toHaveAttribute('aria-valuenow', '4');
    fireEvent.keyDown(slider, {key: 'ArrowRight'});
    expect(audio.currentTime).toBe(5);
    fireEvent.keyDown(slider, {key: 'ArrowLeft'});
    expect(audio.currentTime).toBe(0);
});
