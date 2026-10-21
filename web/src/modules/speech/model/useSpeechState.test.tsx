import {act, renderHook} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {useSpeechState} from '@/modules/speech/model/useSpeechState';
import {speechState} from '@tests/fixtures/speech';

const mocks = vi.hoisted(() => ({
    speechState: vi.fn(),
    installSpeechAsset: vi.fn(),
    cancelSpeechAsset: vi.fn(),
}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: {speech: mocks},
}));

it('polls only an active download and starts installation only after the explicit action', async () => {
    vi.useFakeTimers();
    mocks.speechState
        .mockResolvedValueOnce({
            ...speechState,
            downloads: [
                {
                    assetId: 'whisper-tiny',
                    phase: 'downloading',
                    downloadedBytes: 25,
                    totalBytes: 100,
                    error: '',
                },
            ],
        })
        .mockResolvedValue(speechState);
    mocks.installSpeechAsset.mockResolvedValue({ok: true});
    try {
        const view = renderHook(() => useSpeechState());
        await act(async () => {
            await vi.advanceTimersByTimeAsync(0);
        });
        expect(view.result.current.state?.downloads[0].downloadedBytes).toBe(25);
        expect(mocks.installSpeechAsset).not.toHaveBeenCalled();
        await act(async () => {
            await vi.advanceTimersByTimeAsync(1000);
        });
        expect(view.result.current.state?.downloads).toEqual([]);
        await act(async () => {
            await vi.advanceTimersByTimeAsync(5000);
        });
        expect(mocks.speechState).toHaveBeenCalledTimes(2);
        await act(async () => {
            await view.result.current.install('whisper-tiny');
        });
        expect(mocks.installSpeechAsset).toHaveBeenCalledExactlyOnceWith({assetId: 'whisper-tiny'});
        view.unmount();
    } finally {
        vi.useRealTimers();
    }
});
