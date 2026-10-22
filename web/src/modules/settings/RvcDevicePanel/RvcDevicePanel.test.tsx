import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {act, fireEvent, render, screen} from '@testing-library/react';
import {afterEach, beforeEach, expect, it, vi} from 'vitest';
import type {SpeechComputeDevice, SpeechComputeStatus} from '@aigram/api-client';
import {RvcDevicePanel} from '@/modules/settings/RvcDevicePanel/RvcDevicePanel';
import {speechCompute} from '@tests/fixtures/speechCompute';
import ru from '@/shared/i18n/ru.json';

const mocks = vi.hoisted(() => ({getSpeechComputeStatus: vi.fn()}));
vi.mock('@/shared/api/client', () => ({api: {speech: mocks}}));

beforeEach(() => {
    mocks.getSpeechComputeStatus.mockReset().mockResolvedValue(speechCompute);
});

afterEach(() => {
    vi.useRealTimers();
});

function surface(
    value: SpeechComputeDevice = 'cpu',
    savedValue: SpeechComputeDevice = 'cpu',
    onUpdate = vi.fn(),
    active = true,
) {
    return (
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <RvcDevicePanel value={value} savedValue={savedValue} onUpdate={onUpdate} active={active} />
            </ThemeProvider>
        </NextIntlClientProvider>
    );
}

it('offers actual CUDA capability without saving, downloading or starting speech on device selection', async () => {
    const onUpdate = vi.fn();
    render(surface('cpu', 'cpu', onUpdate));
    expect(await screen.findByText(ru.voice.rvcDevice.available)).toBeVisible();
    expect(screen.getByText(speechCompute.gpuName)).toBeVisible();
    fireEvent.click(screen.getByRole('combobox', {name: ru.voice.rvcDevice.label}));
    fireEvent.click(await screen.findByRole('option', {name: ru.voice.rvcDevice.cuda}));
    expect(onUpdate).toHaveBeenCalledExactlyOnceWith('cuda');
    expect(mocks.getSpeechComputeStatus).toHaveBeenCalledTimes(1);
});

it.each(['not-installed', 'cuda-unavailable'] as const)(
    'keeps a saved unavailable CUDA selection visible with %s and allows an explicit CPU choice',
    async (reason) => {
        mocks.getSpeechComputeStatus.mockResolvedValue({
            ...speechCompute,
            selectedDevice: 'cuda',
            runtimeInstalled: reason !== 'not-installed',
            cudaAvailable: false,
            reason,
        });
        const onUpdate = vi.fn();
        render(surface('cuda', 'cuda', onUpdate));
        expect(await screen.findByText(ru.voice.rvcDevice.reasons[reason])).toBeVisible();
        expect(screen.getByRole('combobox', {name: ru.voice.rvcDevice.label})).toHaveTextContent(
            ru.voice.rvcDevice.cuda,
        );
        expect(onUpdate).not.toHaveBeenCalled();
        fireEvent.click(screen.getByRole('combobox', {name: ru.voice.rvcDevice.label}));
        const cuda = await screen.findByRole('option', {name: ru.voice.rvcDevice.cuda});
        expect(cuda).toHaveAttribute('aria-disabled', 'true');
        fireEvent.click(cuda);
        expect(onUpdate).not.toHaveBeenCalled();
        fireEvent.click(screen.getByRole('option', {name: ru.voice.rvcDevice.cpu}));
        expect(onUpdate).toHaveBeenCalledExactlyOnceWith('cpu');
    },
);

it('does not poll hardware or probe again merely because the selected device was saved', async () => {
    vi.useFakeTimers();
    const view = render(surface());
    await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
    });
    expect(mocks.getSpeechComputeStatus).toHaveBeenCalledTimes(1);
    view.rerender(surface('cuda', 'cuda'));
    await act(async () => {
        await vi.advanceTimersByTimeAsync(10000);
    });
    expect(mocks.getSpeechComputeStatus).toHaveBeenCalledTimes(1);
    expect(vi.getTimerCount()).toBe(0);
    view.unmount();
});

it('defers an inactive panel, aborts its pending probe on hide and rejects a late result after reopening', async () => {
    const old = Promise.withResolvers<SpeechComputeStatus>();
    mocks.getSpeechComputeStatus
        .mockReturnValueOnce(old.promise)
        .mockResolvedValue({...speechCompute, cudaAvailable: false, reason: 'cuda-unavailable'});
    const view = render(surface('cpu', 'cpu', vi.fn(), false));
    await act(async () => {});
    expect(mocks.getSpeechComputeStatus).not.toHaveBeenCalled();
    view.rerender(surface());
    const signal = mocks.getSpeechComputeStatus.mock.calls[0][0].signal as AbortSignal;
    view.rerender(surface('cpu', 'cpu', vi.fn(), false));
    expect(signal.aborted).toBe(true);
    view.rerender(surface());
    expect(await screen.findByText(ru.voice.rvcDevice.reasons['cuda-unavailable'])).toBeVisible();
    await act(async () => {
        old.resolve(speechCompute);
    });
    expect(screen.queryByText(ru.voice.rvcDevice.available)).toBeNull();
    expect(mocks.getSpeechComputeStatus).toHaveBeenCalledTimes(2);
});

it('shows a localized probe failure and recovers only through explicit Refresh', async () => {
    mocks.getSpeechComputeStatus.mockRejectedValueOnce(new Error('Synthetic transport detail'));
    render(surface());
    expect(await screen.findByRole('alert')).toHaveTextContent(ru.voice.rvcDevice.loadFailed);
    expect(screen.queryByText('Synthetic transport detail')).toBeNull();
    fireEvent.click(screen.getByRole('button', {name: ru.voice.rvcDevice.refresh}));
    expect(await screen.findByText(ru.voice.rvcDevice.available)).toBeVisible();
    expect(mocks.getSpeechComputeStatus).toHaveBeenCalledTimes(2);
});
