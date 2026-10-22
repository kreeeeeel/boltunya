import {afterEach, beforeEach, expect, it, vi} from 'vitest';
import type {SpeechState} from '@aigram/api-client';
import {createSpeechStateStore} from '@/modules/speech/model/speechStateStore';
import {speechState} from '@tests/fixtures/speech';

const activeState: SpeechState = {
    ...speechState,
    downloads: [
        {assetId: 'whisper-tiny', phase: 'downloading', downloadedBytes: 25, totalBytes: 100, error: ''},
    ],
};

let cleanups: (() => void)[];

beforeEach(() => {
    vi.useFakeTimers();
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
    cleanups = [];
});

afterEach(() => {
    cleanups.forEach((cleanup) => cleanup());
    vi.useRealTimers();
    vi.restoreAllMocks();
});

it.each([2, 3])(
    'shares one download request and timer across %s consumers without publishing unchanged snapshots',
    async (consumers) => {
        const read = vi.fn(async () => structuredClone(activeState));
        const store = createSpeechStateStore(read);
        const changed = vi.fn();
        cleanups = Array.from({length: consumers}, () => store.subscribe(() => changed()));
        await vi.advanceTimersByTimeAsync(0);
        const initial = store.getSnapshot();
        expect(read).toHaveBeenCalledTimes(1);
        expect(changed).toHaveBeenCalledTimes(consumers);
        expect(vi.getTimerCount()).toBe(1);

        await vi.advanceTimersByTimeAsync(5000);
        expect(read).toHaveBeenCalledTimes(6);
        expect(store.getSnapshot()).toBe(initial);
        expect(changed).toHaveBeenCalledTimes(consumers);
        expect(vi.getTimerCount()).toBe(1);

        cleanups[0]();
        await vi.advanceTimersByTimeAsync(1000);
        expect(read).toHaveBeenCalledTimes(7);
        cleanups.slice(1).forEach((cleanup) => cleanup());
        expect(vi.getTimerCount()).toBe(0);
        await vi.advanceTimersByTimeAsync(5000);
        expect(read).toHaveBeenCalledTimes(7);
    },
);

it('shares idle state without scheduling periodic requests', async () => {
    const read = vi.fn(async () => speechState);
    const store = createSpeechStateStore(read);
    expect(read).not.toHaveBeenCalled();
    cleanups = Array.from({length: 3}, () => store.subscribe(vi.fn()));
    await vi.advanceTimersByTimeAsync(10000);
    expect(read).toHaveBeenCalledTimes(1);
    expect(vi.getTimerCount()).toBe(0);
    expect(store.getSnapshot().state).toBe(speechState);
});

it('aborts a hidden request, ignores its late result and refreshes once when visible', async () => {
    const stale = Promise.withResolvers<SpeechState>();
    const read = vi
        .fn<(signal: AbortSignal) => Promise<SpeechState>>()
        .mockReturnValueOnce(stale.promise)
        .mockResolvedValue(activeState);
    const store = createSpeechStateStore(read);
    cleanups = [store.subscribe(vi.fn())];
    const signal = read.mock.calls[0][0];

    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden');
    document.dispatchEvent(new Event('visibilitychange'));
    expect(signal.aborted).toBe(true);
    stale.resolve(speechState);
    await vi.advanceTimersByTimeAsync(5000);
    expect(read).toHaveBeenCalledTimes(1);
    expect(store.getSnapshot().state).toBeUndefined();
    expect(vi.getTimerCount()).toBe(0);

    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
    document.dispatchEvent(new Event('visibilitychange'));
    await vi.advanceTimersByTimeAsync(0);
    expect(read).toHaveBeenCalledTimes(2);
    expect(store.getSnapshot().state).toBe(activeState);
    expect(vi.getTimerCount()).toBe(1);
});

it('coalesces explicit reload and mutation events during a flight and rejects its obsolete snapshot', async () => {
    const stale = Promise.withResolvers<SpeechState>();
    const current = Promise.withResolvers<SpeechState>();
    const read = vi
        .fn<(signal: AbortSignal) => Promise<SpeechState>>()
        .mockReturnValueOnce(stale.promise)
        .mockReturnValueOnce(current.promise);
    const store = createSpeechStateStore(read);
    const changed = vi.fn();
    cleanups = [store.subscribe(changed), store.subscribe(vi.fn())];
    store.refresh();
    window.dispatchEvent(new Event('speech-assets-updated'));
    window.dispatchEvent(new Event('speech-assets-updated'));
    expect(read).toHaveBeenCalledTimes(1);

    stale.resolve(activeState);
    await vi.advanceTimersByTimeAsync(0);
    expect(read).toHaveBeenCalledTimes(2);
    expect(changed).not.toHaveBeenCalled();
    expect(store.getSnapshot().state).toBeUndefined();

    current.resolve(speechState);
    await vi.advanceTimersByTimeAsync(0);
    expect(store.getSnapshot().state).toBe(speechState);
    expect(changed).toHaveBeenCalledOnce();
    expect(vi.getTimerCount()).toBe(0);
});

it('keeps a remounted subscription safe from an earlier aborted StrictMode request', async () => {
    const stale = Promise.withResolvers<SpeechState>();
    const read = vi
        .fn<(signal: AbortSignal) => Promise<SpeechState>>()
        .mockReturnValueOnce(stale.promise)
        .mockResolvedValue(speechState);
    const store = createSpeechStateStore(read);
    const unsubscribe = store.subscribe(vi.fn());
    const firstSignal = read.mock.calls[0][0];
    unsubscribe();
    expect(firstSignal.aborted).toBe(true);

    const changed = vi.fn();
    cleanups = [store.subscribe(changed)];
    await vi.advanceTimersByTimeAsync(0);
    const confirmed = store.getSnapshot();
    stale.resolve(activeState);
    await vi.advanceTimersByTimeAsync(5000);
    expect(store.getSnapshot()).toBe(confirmed);
    expect(store.getSnapshot().state).toBe(speechState);
    expect(read).toHaveBeenCalledTimes(2);
    expect(changed).toHaveBeenCalledOnce();
    expect(vi.getTimerCount()).toBe(0);
});

it('recovers a shared read failure when a new consumer subscribes without periodic idle retries', async () => {
    const read = vi
        .fn<(signal: AbortSignal) => Promise<SpeechState>>()
        .mockRejectedValueOnce(new Error('Synthetic read failure'))
        .mockResolvedValue(speechState);
    const store = createSpeechStateStore(read);
    cleanups = [store.subscribe(vi.fn())];
    await vi.advanceTimersByTimeAsync(0);
    await vi.advanceTimersByTimeAsync(5000);
    expect(read).toHaveBeenCalledTimes(1);
    expect(store.getSnapshot().error).toBe('Synthetic read failure');
    expect(vi.getTimerCount()).toBe(0);

    cleanups.push(store.subscribe(vi.fn()));
    await vi.advanceTimersByTimeAsync(0);
    expect(read).toHaveBeenCalledTimes(2);
    expect(store.getSnapshot()).toEqual({state: speechState, error: ''});
    expect(vi.getTimerCount()).toBe(0);
});

it('revalidates an idle catalog for a newly opened consumer while retaining its cached state', async () => {
    const updated = {
        ...speechState,
        assets: speechState.assets.map((asset, index) =>
            index === 0 ? {...asset, title: 'Externally updated synthetic engine'} : asset,
        ),
    };
    const read = vi
        .fn<(signal: AbortSignal) => Promise<SpeechState>>()
        .mockResolvedValueOnce(speechState)
        .mockResolvedValue(updated);
    const store = createSpeechStateStore(read);
    const changed = vi.fn();
    cleanups = [store.subscribe(changed)];
    await vi.advanceTimersByTimeAsync(0);
    const cached = store.getSnapshot();
    await vi.advanceTimersByTimeAsync(5000);
    expect(read).toHaveBeenCalledTimes(1);

    cleanups.push(store.subscribe(vi.fn()));
    expect(read).toHaveBeenCalledTimes(2);
    expect(store.getSnapshot()).toBe(cached);
    await vi.advanceTimersByTimeAsync(0);
    expect(store.getSnapshot().state).toBe(updated);
    expect(changed).toHaveBeenCalledTimes(2);
    expect(vi.getTimerCount()).toBe(0);
});

it('replaces a scheduled download poll with one shared revalidation for new consumers', async () => {
    const revalidation = Promise.withResolvers<SpeechState>();
    const read = vi
        .fn<(signal: AbortSignal) => Promise<SpeechState>>()
        .mockResolvedValueOnce(activeState)
        .mockReturnValueOnce(revalidation.promise)
        .mockResolvedValue(activeState);
    const store = createSpeechStateStore(read);
    cleanups = [store.subscribe(vi.fn())];
    await vi.advanceTimersByTimeAsync(500);
    expect(read).toHaveBeenCalledTimes(1);
    expect(vi.getTimerCount()).toBe(1);

    cleanups.push(store.subscribe(vi.fn()));
    cleanups.push(store.subscribe(vi.fn()));
    expect(read).toHaveBeenCalledTimes(2);
    expect(vi.getTimerCount()).toBe(0);
    await vi.advanceTimersByTimeAsync(1500);
    expect(read).toHaveBeenCalledTimes(2);

    revalidation.resolve(activeState);
    await vi.advanceTimersByTimeAsync(0);
    expect(vi.getTimerCount()).toBe(1);
    await vi.advanceTimersByTimeAsync(1000);
    expect(read).toHaveBeenCalledTimes(3);
    expect(vi.getTimerCount()).toBe(1);
});
