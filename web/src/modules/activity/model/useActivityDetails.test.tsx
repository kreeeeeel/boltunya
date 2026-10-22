import {act, renderHook} from '@testing-library/react';
import {StrictMode} from 'react';
import {afterEach, beforeEach, expect, it, vi} from 'vitest';
import type {ActivityDetails, ActivityFeedback} from '@aigram/api-client';
import {useActivityDetails} from '@/modules/activity/model/useActivityDetails';
import {activityDetails, testActivity} from '@tests/fixtures/activity';

const mocks = vi.hoisted(() => ({activityDetails: vi.fn()}));

vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: {activity: {activityDetails: mocks.activityDetails}},
}));

const feedback: ActivityFeedback = {rating: 'good', comment: 'Synthetic feedback', updatedAt: 2000};
const details = (id: string, active = false): ActivityDetails => ({
    ...activityDetails(testActivity[active ? 0 : 2]),
    entry: {...testActivity[active ? 0 : 2], id},
});

beforeEach(() => {
    vi.useFakeTimers();
    mocks.activityDetails.mockReset();
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
});

afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
});

async function tick(milliseconds = 0) {
    await act(async () => {
        await vi.advanceTimersByTimeAsync(milliseconds);
    });
}

function visibility(value: 'visible' | 'hidden') {
    act(() => {
        vi.spyOn(document, 'visibilityState', 'get').mockReturnValue(value);
        document.dispatchEvent(new Event('visibilitychange'));
    });
}

it('does not read initially hidden details and resumes active polling only while visible', async () => {
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden');
    mocks.activityDetails.mockResolvedValue(details('job', true));
    const view = renderHook(() => useActivityDetails('job'));
    await tick(5000);
    expect(mocks.activityDetails).not.toHaveBeenCalled();
    expect(vi.getTimerCount()).toBe(0);
    visibility('visible');
    await tick();
    expect(mocks.activityDetails).toHaveBeenCalledTimes(1);
    expect(view.result.current.details?.entry.id).toBe('job');
    expect(vi.getTimerCount()).toBe(1);
    visibility('hidden');
    await tick(5000);
    expect(mocks.activityDetails).toHaveBeenCalledTimes(1);
    expect(vi.getTimerCount()).toBe(0);
    visibility('visible');
    await tick();
    expect(mocks.activityDetails).toHaveBeenCalledTimes(2);
    view.unmount();
    expect(vi.getTimerCount()).toBe(0);
});

it('aborts a hidden read and rejects its late response after a newer visible result', async () => {
    const stale = Promise.withResolvers<ActivityDetails>();
    const confirmed = details('job');
    mocks.activityDetails.mockReturnValueOnce(stale.promise).mockResolvedValue(confirmed);
    const view = renderHook(() => useActivityDetails('job'));
    await tick();
    const signal = mocks.activityDetails.mock.calls[0][1].signal as AbortSignal;
    visibility('hidden');
    expect(signal.aborted).toBe(true);
    visibility('visible');
    await tick();
    expect(view.result.current.details).toBe(confirmed);
    stale.resolve(details('job', true));
    await tick(5000);
    expect(view.result.current.details).toBe(confirmed);
    expect(mocks.activityDetails).toHaveBeenCalledTimes(2);
    expect(vi.getTimerCount()).toBe(0);
    view.unmount();
});

it.each([
    {label: 'a saved rating', before: null, saved: feedback},
    {label: 'feedback removal', before: feedback, saved: null},
])(
    'preserves $label while a pre-mutation poll finishes and a fresh follow-up is pending',
    async ({before, saved}) => {
        const stale = Promise.withResolvers<ActivityDetails>();
        const fresh = Promise.withResolvers<ActivityDetails>();
        const initial = {...details('job'), feedback: before};
        mocks.activityDetails
            .mockResolvedValueOnce(initial)
            .mockReturnValueOnce(stale.promise)
            .mockReturnValueOnce(fresh.promise);
        const view = renderHook(() => useActivityDetails('job'));
        await tick();
        visibility('hidden');
        visibility('visible');
        await tick();
        expect(mocks.activityDetails).toHaveBeenCalledTimes(2);
        act(() => view.result.current.updateFeedback(saved));
        expect(view.result.current.details?.feedback).toEqual(saved);
        stale.resolve(initial);
        await tick();
        expect(mocks.activityDetails).toHaveBeenCalledTimes(3);
        expect(view.result.current.details?.feedback).toEqual(saved);
        fresh.resolve({...initial, feedback: saved});
        await tick();
        expect(view.result.current.details?.feedback).toEqual(saved);
        expect(vi.getTimerCount()).toBe(0);
        view.unmount();
    },
);

it('never exposes the previous id during a StrictMode switch or accepts its late read or callback', async () => {
    const stale = Promise.withResolvers<ActivityDetails>();
    const current = Promise.withResolvers<ActivityDetails>();
    mocks.activityDetails
        .mockResolvedValueOnce(details('first'))
        .mockReturnValueOnce(stale.promise)
        .mockReturnValueOnce(current.promise);
    const renders: {id: string; shown: string | undefined}[] = [];
    const view = renderHook(
        ({id}) => {
            const result = useActivityDetails(id);
            renders.push({id, shown: result.details?.entry.id});
            return result;
        },
        {initialProps: {id: 'first'}, wrapper: StrictMode},
    );
    await tick();
    expect(mocks.activityDetails).toHaveBeenCalledTimes(1);
    const oldUpdate = view.result.current.updateFeedback;
    visibility('hidden');
    visibility('visible');
    await tick();
    const signal = mocks.activityDetails.mock.calls[1][1].signal as AbortSignal;
    view.rerender({id: 'second'});
    expect(view.result.current.details).toBeNull();
    expect(view.result.current.error).toBe('');
    expect(renders.filter((item) => item.id === 'second').every((item) => item.shown === undefined)).toBe(
        true,
    );
    expect(signal.aborted).toBe(true);
    await tick();
    act(() => oldUpdate(feedback));
    await act(async () => {
        current.resolve(details('second'));
        stale.resolve(details('first', true));
    });
    expect(view.result.current.details?.entry.id).toBe('second');
    expect(view.result.current.details?.feedback).toBeNull();
    expect(mocks.activityDetails).toHaveBeenCalledTimes(3);
    view.unmount();
    expect(vi.getTimerCount()).toBe(0);
});

it('reports a read failure without a retry loop and recovers on visibility return', async () => {
    mocks.activityDetails
        .mockRejectedValueOnce(new Error('Synthetic detail read failed'))
        .mockResolvedValue(details('job'));
    const view = renderHook(() => useActivityDetails('job'));
    await tick(5000);
    expect(view.result.current.error).toBe('Synthetic detail read failed');
    expect(view.result.current.details).toBeNull();
    expect(mocks.activityDetails).toHaveBeenCalledTimes(1);
    expect(vi.getTimerCount()).toBe(0);
    visibility('hidden');
    visibility('visible');
    await tick();
    expect(view.result.current.error).toBe('');
    expect(view.result.current.details?.entry.id).toBe('job');
    expect(mocks.activityDetails).toHaveBeenCalledTimes(2);
    view.unmount();
});
