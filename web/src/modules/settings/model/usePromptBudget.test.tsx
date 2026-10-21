import {act, renderHook} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {usePromptBudget} from '@/modules/settings/model/usePromptBudget';
import {syntheticPromptBudget} from '@tests/fixtures/promptBudget';

const mocks = vi.hoisted(() => ({POST: vi.fn()}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: mocks,
}));

it('debounces typing and ignores a prior model calculation after selection changes', async () => {
    vi.useFakeTimers();
    const old = Promise.withResolvers<unknown>();
    mocks.POST.mockReset()
        .mockReturnValueOnce(old.promise)
        .mockResolvedValue({data: syntheticPromptBudget({globalPrompt: 'new'}), response: new Response()});
    try {
        const view = renderHook(({text}) => usePromptBudget({globalPrompt: text}), {
            initialProps: {text: 'old'},
        });
        await act(() => vi.advanceTimersByTimeAsync(350));
        view.rerender({text: 'n'});
        view.rerender({text: 'new'});
        expect(view.result.current.report).toBeUndefined();
        await act(() => vi.advanceTimersByTimeAsync(350));
        expect(mocks.POST).toHaveBeenCalledTimes(2);
        const current = view.result.current.report;
        await act(async () =>
            old.resolve({
                data: syntheticPromptBudget({globalPrompt: 'old '.repeat(2000)}),
                response: new Response(),
            }),
        );
        expect(view.result.current.report).toEqual(current);
        expect(mocks.POST.mock.calls[0][1].signal.aborted).toBe(true);
        view.unmount();
    } finally {
        vi.useRealTimers();
    }
});
