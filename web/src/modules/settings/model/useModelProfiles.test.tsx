import {act, renderHook} from '@testing-library/react';
import {StrictMode} from 'react';
import {afterEach, beforeEach, expect, it, vi} from 'vitest';
import {useModelProfiles} from '@/modules/settings/model/useModelProfiles';
import {modelProfiles, providerPreset} from '@tests/fixtures/modelProfiles';

const mocks = vi.hoisted(() => ({modelProfiles: vi.fn()}));

vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: {models: mocks},
}));

const report = {profiles: modelProfiles, presets: [providerPreset]};

beforeEach(() => {
    mocks.modelProfiles.mockReset().mockResolvedValue(report);
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
});

afterEach(() => {
    vi.restoreAllMocks();
});

it('shares one real store read across StrictMode consumers and exposes the same profile references', async () => {
    const view = renderHook(() => [useModelProfiles(), useModelProfiles()], {wrapper: StrictMode});
    await act(async () => {});
    expect(mocks.modelProfiles).toHaveBeenCalledTimes(1);
    expect(mocks.modelProfiles.mock.calls[0][0].signal).toBeInstanceOf(AbortSignal);
    expect(view.result.current[0].profiles).toBe(modelProfiles);
    expect(view.result.current[0].profiles).toBe(view.result.current[1].profiles);
    expect(view.result.current[0].loading).toBe(false);
    view.unmount();
});

it('does not launch its queued initial GET after the hook unmounts', async () => {
    const view = renderHook(useModelProfiles);
    view.unmount();
    await act(async () => {});
    expect(mocks.modelProfiles).not.toHaveBeenCalled();
});

it.each([
    {action: 'save', profiles: [{...modelProfiles[0], name: 'Updated synthetic model'}]},
    {action: 'delete', profiles: []},
])(
    'synchronizes remaining consumers after the initiating $action flow unmounts before refresh',
    async ({profiles}) => {
        const observer = renderHook(useModelProfiles);
        const initiating = renderHook(useModelProfiles);
        await act(async () => {});
        expect(mocks.modelProfiles).toHaveBeenCalledTimes(1);
        const refresh = initiating.result.current.refresh;
        initiating.unmount();
        mocks.modelProfiles.mockResolvedValue({profiles, presets: [providerPreset]});
        await act(async () => {
            await refresh();
        });
        expect(mocks.modelProfiles).toHaveBeenCalledTimes(2);
        expect(observer.result.current.profiles).toEqual(profiles);
        expect(observer.result.current.presets).toEqual([providerPreset]);
        observer.unmount();
    },
);
