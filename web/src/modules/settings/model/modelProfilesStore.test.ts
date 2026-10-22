import {afterEach, beforeEach, expect, it, vi} from 'vitest';
import type {ModelProfileReport} from '@aigram/api-client';
import {createModelProfilesStore} from '@/modules/settings/model/modelProfilesStore';
import {modelProfiles, providerPreset} from '@tests/fixtures/modelProfiles';

const report: ModelProfileReport = {profiles: modelProfiles, presets: [providerPreset]};
const emptyReport: ModelProfileReport = {...report, profiles: []};
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

function visibility(value: 'visible' | 'hidden') {
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue(value);
    document.dispatchEvent(new Event('visibilitychange'));
}

it.each([2, 3])(
    'shares one read across %s consumers and retains unchanged snapshot identity without idle polling',
    async (consumers) => {
        const read = vi.fn(async () => structuredClone(report));
        const store = createModelProfilesStore(read);
        const changed = vi.fn();
        cleanups = Array.from({length: consumers}, () => store.subscribe(() => changed()));
        await vi.advanceTimersByTimeAsync(0);
        expect(read).toHaveBeenCalledTimes(1);
        expect(changed).toHaveBeenCalledTimes(consumers);
        const initial = store.getSnapshot();

        await store.refresh();
        expect(read).toHaveBeenCalledTimes(2);
        expect(store.getSnapshot()).toBe(initial);
        expect(store.getSnapshot().profiles).toBe(initial.profiles);
        expect(changed).toHaveBeenCalledTimes(consumers);
        await vi.advanceTimersByTimeAsync(10000);
        expect(read).toHaveBeenCalledTimes(2);
        expect(vi.getTimerCount()).toBe(0);
    },
);

it('revalidates for a new consumer while preserving cached data and lets other new consumers join that flight', async () => {
    const next = Promise.withResolvers<ModelProfileReport>();
    const read = vi
        .fn<(signal: AbortSignal) => Promise<ModelProfileReport>>()
        .mockResolvedValueOnce(report)
        .mockReturnValueOnce(next.promise);
    const store = createModelProfilesStore(read);
    cleanups = [store.subscribe(vi.fn())];
    await vi.advanceTimersByTimeAsync(0);
    const cached = store.getSnapshot();
    cleanups.push(store.subscribe(vi.fn()), store.subscribe(vi.fn()));
    await vi.advanceTimersByTimeAsync(0);
    expect(read).toHaveBeenCalledTimes(2);
    expect(store.getSnapshot()).toBe(cached);
    next.resolve(emptyReport);
    await vi.advanceTimersByTimeAsync(0);
    expect(store.getSnapshot().profiles).toEqual([]);
    expect(read).toHaveBeenCalledTimes(2);
});

it('replaces an obsolete queued revalidation with one fresh read after external change events', async () => {
    const read = vi.fn(async () => report);
    const store = createModelProfilesStore(read);
    cleanups = [store.subscribe(vi.fn())];
    await vi.advanceTimersByTimeAsync(0);
    const cached = store.getSnapshot();
    read.mockResolvedValue(emptyReport);
    cleanups.push(store.subscribe(vi.fn()));
    window.dispatchEvent(new Event('aigram-model-profiles-changed'));
    window.dispatchEvent(new Event('aigram-model-profiles-changed'));
    window.dispatchEvent(new Event('aigram-model-profiles-changed'));
    expect(store.getSnapshot()).toBe(cached);
    await vi.advanceTimersByTimeAsync(0);
    expect(read).toHaveBeenCalledTimes(2);
    expect(store.getSnapshot().profiles).toEqual([]);
    expect(store.getSnapshot().error).toBe('');
});

it('does not start a queued read after the final unsubscribe and removes its global listeners', async () => {
    const read = vi.fn(async () => report);
    const store = createModelProfilesStore(read);
    const unsubscribe = store.subscribe(vi.fn());
    unsubscribe();
    await store.refresh();
    window.dispatchEvent(new Event('aigram-model-profiles-changed'));
    visibility('visible');
    await vi.advanceTimersByTimeAsync(10000);
    expect(read).not.toHaveBeenCalled();
    expect(vi.getTimerCount()).toBe(0);

    cleanups = [store.subscribe(vi.fn())];
    await vi.advanceTimersByTimeAsync(0);
    expect(read).toHaveBeenCalledTimes(1);
    expect(store.getSnapshot().profiles).toBe(report.profiles);
});

it('defers hidden initial reads and change events until one shared revalidation on visibility return', async () => {
    visibility('hidden');
    const read = vi.fn(async () => report);
    const store = createModelProfilesStore(read);
    cleanups = Array.from({length: 3}, () => store.subscribe(vi.fn()));
    window.dispatchEvent(new Event('aigram-model-profiles-changed'));
    await store.refresh();
    await vi.advanceTimersByTimeAsync(10000);
    expect(read).not.toHaveBeenCalled();
    expect(store.getSnapshot().loading).toBe(true);
    expect(vi.getTimerCount()).toBe(0);
    visibility('visible');
    await vi.advanceTimersByTimeAsync(0);
    expect(read).toHaveBeenCalledTimes(1);
    expect(store.getSnapshot().loading).toBe(false);
});

it.each(['resolve', 'reject'] as const)(
    'aborts a hidden read and ignores its late %s after a fresh visible response',
    async (outcome) => {
        const stale = Promise.withResolvers<ModelProfileReport>();
        const read = vi
            .fn<(signal: AbortSignal) => Promise<ModelProfileReport>>()
            .mockReturnValueOnce(stale.promise)
            .mockResolvedValue(emptyReport);
        const store = createModelProfilesStore(read);
        cleanups = [store.subscribe(vi.fn())];
        await vi.advanceTimersByTimeAsync(0);
        const signal = read.mock.calls[0][0];
        visibility('hidden');
        expect(signal.aborted).toBe(true);
        window.dispatchEvent(new Event('aigram-model-profiles-changed'));
        await vi.advanceTimersByTimeAsync(10000);
        expect(read).toHaveBeenCalledTimes(1);
        visibility('visible');
        await vi.advanceTimersByTimeAsync(0);
        const confirmed = store.getSnapshot();
        if (outcome === 'resolve') {
            stale.resolve(report);
        } else {
            stale.reject(new Error('Obsolete synthetic read failure'));
        }
        await vi.advanceTimersByTimeAsync(0);
        expect(store.getSnapshot()).toBe(confirmed);
        expect(store.getSnapshot().profiles).toEqual([]);
        expect(store.getSnapshot().error).toBe('');
        expect(read).toHaveBeenCalledTimes(2);
    },
);

it('rejects a late aborted response after the final consumer leaves and another consumer subscribes', async () => {
    const stale = Promise.withResolvers<ModelProfileReport>();
    const read = vi
        .fn<(signal: AbortSignal) => Promise<ModelProfileReport>>()
        .mockReturnValueOnce(stale.promise)
        .mockResolvedValue(emptyReport);
    const store = createModelProfilesStore(read);
    const unsubscribe = store.subscribe(vi.fn());
    await vi.advanceTimersByTimeAsync(0);
    const signal = read.mock.calls[0][0];
    unsubscribe();
    expect(signal.aborted).toBe(true);
    cleanups = [store.subscribe(vi.fn())];
    await vi.advanceTimersByTimeAsync(0);
    const confirmed = store.getSnapshot();
    stale.resolve(report);
    await vi.advanceTimersByTimeAsync(0);
    expect(store.getSnapshot()).toBe(confirmed);
    expect(store.getSnapshot().profiles).toEqual([]);
    expect(read).toHaveBeenCalledTimes(2);
});

it('coalesces explicit refreshes and external events and resolves only after the required post-mutation response', async () => {
    const stale = Promise.withResolvers<ModelProfileReport>();
    const fresh = Promise.withResolvers<ModelProfileReport>();
    const read = vi
        .fn<(signal: AbortSignal) => Promise<ModelProfileReport>>()
        .mockReturnValueOnce(stale.promise)
        .mockReturnValueOnce(fresh.promise);
    const store = createModelProfilesStore(read);
    cleanups = [store.subscribe(vi.fn()), store.subscribe(vi.fn())];
    await vi.advanceTimersByTimeAsync(0);
    let settled = 0;
    const first = store.refresh().then(() => {
        settled += 1;
    });
    const second = store.refresh().then(() => {
        settled += 1;
    });
    window.dispatchEvent(new Event('aigram-model-profiles-changed'));
    expect(read).toHaveBeenCalledTimes(1);
    stale.resolve(report);
    await vi.advanceTimersByTimeAsync(0);
    expect(read).toHaveBeenCalledTimes(2);
    expect(store.getSnapshot().profiles).toEqual([]);
    expect(store.getSnapshot().loading).toBe(true);
    expect(settled).toBe(0);
    fresh.resolve(emptyReport);
    await Promise.all([first, second]);
    expect(settled).toBe(2);
    expect(store.getSnapshot().loading).toBe(false);
    expect(store.getSnapshot().profiles).toEqual([]);
    expect(read).toHaveBeenCalledTimes(2);
});

it('settles a failed mandatory fresh read without retrying forever and recovers when a new consumer opens', async () => {
    const stale = Promise.withResolvers<ModelProfileReport>();
    const read = vi
        .fn<(signal: AbortSignal) => Promise<ModelProfileReport>>()
        .mockReturnValueOnce(stale.promise)
        .mockRejectedValueOnce(new Error('Synthetic profile read failed'))
        .mockResolvedValue(emptyReport);
    const store = createModelProfilesStore(read);
    cleanups = [store.subscribe(vi.fn())];
    await vi.advanceTimersByTimeAsync(0);
    const pending = store.refresh();
    stale.resolve(report);
    await pending;
    expect(store.getSnapshot().profiles).toEqual([]);
    expect(store.getSnapshot().error).toBe('Synthetic profile read failed');
    expect(store.getSnapshot().loading).toBe(false);
    await vi.advanceTimersByTimeAsync(10000);
    expect(read).toHaveBeenCalledTimes(2);
    expect(vi.getTimerCount()).toBe(0);
    cleanups.push(store.subscribe(vi.fn()));
    await vi.advanceTimersByTimeAsync(0);
    expect(read).toHaveBeenCalledTimes(3);
    expect(store.getSnapshot().error).toBe('');
});
