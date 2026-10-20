import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {beforeEach, expect, it, vi} from 'vitest';

import {ErrorsPanel} from '@/modules/diagnostics/ErrorsPanel/ErrorsPanel';
import ru from '@/shared/i18n/ru.json';

const mocks = vi.hoisted(() => ({GET: vi.fn(), DELETE: vi.fn()}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: mocks,
}));

const entry = {
    id: 'error-1',
    date: 1729411200,
    firstOccurredAt: 1729411100,
    source: 'engine',
    code: 'ENGINE_UNAVAILABLE',
    message: 'Ollama недоступна.',
    remediation: 'Запустите Ollama и повторите попытку.',
    exceptionType: 'ConnectException',
    occurrences: 2,
    metadata: {status: '503'},
};
function mount() {
    return render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ErrorsPanel />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
}

beforeEach(() => {
    mocks.GET.mockReset().mockResolvedValue({data: {errors: [entry]}, response: new Response()});
    mocks.DELETE.mockReset().mockResolvedValue({data: {ok: true}, response: new Response()});
});

it('opens separate explanation, recovery and technical blocks from an error card', async () => {
    mount();
    fireEvent.click(await screen.findByRole('button', {name: /Ollama недоступна/}));
    const dialog = screen.getByRole('dialog', {name: ru.diagnostics.details});
    expect(dialog).toHaveTextContent(ru.diagnostics.happened);
    expect(dialog).toHaveTextContent(ru.diagnostics.solution);
    expect(dialog).toHaveTextContent(ru.diagnostics.technical);
    expect(dialog).toHaveTextContent('503');
    expect(dialog.querySelectorAll('[class*="block"]')).toHaveLength(3);
});

it('shows the real empty state after clearing the error journal', async () => {
    mount();
    await screen.findByText(entry.message);
    mocks.GET.mockResolvedValue({data: {errors: []}, response: new Response()});
    fireEvent.click(screen.getByRole('button', {name: ru.diagnostics.clear}));
    await waitFor(() => expect(mocks.DELETE).toHaveBeenCalledWith('/api/diagnostics/errors'));
    expect(await screen.findByText(ru.diagnostics.empty)).toBeVisible();
    expect(screen.queryByText(entry.message)).toBeNull();
});
