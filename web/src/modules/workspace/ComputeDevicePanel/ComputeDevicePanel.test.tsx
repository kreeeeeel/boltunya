import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {beforeEach, expect, it, vi} from 'vitest';

import {ComputeDevicePanel} from '@/modules/workspace/ComputeDevicePanel/ComputeDevicePanel';
import {computeDevices} from '@tests/fixtures/computeDevices';
import ru from '@/shared/i18n/ru.json';

const mocks = vi.hoisted(() => ({GET: vi.fn()}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: mocks,
}));

beforeEach(() => {
    mocks.GET.mockReset().mockResolvedValue({data: computeDevices, response: new Response()});
});

function mount(value = 'auto', savedValue = 'auto', onUpdate = vi.fn()) {
    return render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ComputeDevicePanel value={value} savedValue={savedValue} onUpdate={onUpdate} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
}

it('offers known NVIDIA UUIDs and CPU mode for app-managed Ollama without starting or restarting it', async () => {
    const onUpdate = vi.fn();
    mount('auto', 'auto', onUpdate);
    const select = await screen.findByRole('combobox', {name: ru.computeDevice.label});
    fireEvent.click(select);
    fireEvent.click(
        await screen.findByRole('option', {name: (name) => name.includes('NVIDIA GeForce RTX 4070')}),
    );
    expect(onUpdate).toHaveBeenCalledWith(computeDevices.devices[2].id);
    expect(screen.getByText(ru.computeDevice.nextStart)).toBeVisible();
    expect(mocks.GET).toHaveBeenCalledTimes(1);
});

it('does not offer device changes for an externally managed Ollama server', async () => {
    mocks.GET.mockResolvedValue({
        data: {
            ...computeDevices,
            managed: false,
            active: null,
            selectionSupported: false,
            limitation: 'external',
        },
        response: new Response(),
    });
    mount();
    const select = await screen.findByRole('combobox', {name: ru.computeDevice.label});
    expect(select).toBeDisabled();
    fireEvent.click(select);
    expect(screen.queryByRole('option')).toBeNull();
    expect(screen.getByText(ru.computeDevice.limitations.external)).toBeVisible();
});

it('keeps an unavailable configured device visible and refreshes metadata after settings are saved', async () => {
    const missing = 'GPU-aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee';
    const view = mount(missing);
    expect(await screen.findByRole('combobox', {name: ru.computeDevice.label})).toHaveTextContent(
        ru.computeDevice.unavailableSelection,
    );
    view.rerender(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ComputeDevicePanel value="cpu" savedValue="cpu" onUpdate={vi.fn()} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    await waitFor(() => expect(mocks.GET).toHaveBeenCalledTimes(2));
    expect(screen.getByRole('combobox', {name: ru.computeDevice.label})).toHaveTextContent(
        ru.computeDevice.cpu,
    );
});

it('shows an outdated-driver recommendation without blocking a selectable NVIDIA device', async () => {
    const data = structuredClone(computeDevices);
    data.devices[2].reason = 'driver';
    mocks.GET.mockResolvedValue({data, response: new Response()});
    const onUpdate = vi.fn();
    mount('auto', 'auto', onUpdate);
    fireEvent.click(await screen.findByRole('combobox', {name: ru.computeDevice.label}));
    fireEvent.click(
        await screen.findByRole('option', {name: (name) => name.includes('NVIDIA GeForce RTX 4070')}),
    );
    expect(onUpdate).toHaveBeenCalledWith(data.devices[2].id);
    expect(screen.getByText((content) => content.includes(ru.computeDevice.reasons.driver))).toBeVisible();
});
