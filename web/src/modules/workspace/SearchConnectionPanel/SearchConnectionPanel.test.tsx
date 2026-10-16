import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {beforeEach, expect, it, vi} from 'vitest';

import {SearchConnectionPanel} from '@/modules/workspace/SearchConnectionPanel';
import ru from '@/shared/i18n/ru.json';

const mocks = vi.hoisted(() => ({GET: vi.fn(), POST: vi.fn(), DELETE: vi.fn()}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: mocks,
}));

function mount() {
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <SearchConnectionPanel />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
}

beforeEach(() => {
    mocks.GET.mockReset().mockResolvedValue({
        data: {configured: false, state: 'unchecked'},
        response: new Response(),
    });
    mocks.POST.mockReset();
    mocks.DELETE.mockReset();
});

it('masks the key clears it after saving and only displays the configured flag', async () => {
    mocks.POST.mockResolvedValue({data: {configured: true, state: 'unchecked'}, response: new Response()});
    mount();
    fireEvent.click(screen.getByRole('button', {name: ru.internetSearch.optionalExa}));
    await screen.findByText(ru.internetSearch.keyless);
    const key = screen.getByLabelText(ru.internetSearch.key);
    expect(key).toHaveAttribute('type', 'password');
    fireEvent.change(key, {target: {value: 'fake-exa-key-123'}});
    fireEvent.click(screen.getByRole('button', {name: ru.internetSearch.save}));
    await screen.findByText(ru.internetSearch.configured);
    expect(key).toHaveValue('');
    expect(mocks.POST).toHaveBeenCalledWith('/api/search/connection', {body: {apiKey: 'fake-exa-key-123'}});
});

it('keeps the input available for retry and explains fallback after a provider rate limit', async () => {
    mocks.POST.mockRejectedValueOnce(new Error('Network unavailable'));
    mount();
    fireEvent.click(screen.getByRole('button', {name: ru.internetSearch.optionalExa}));
    await screen.findByText(ru.internetSearch.keyless);
    fireEvent.click(screen.getByRole('button', {name: ru.internetSearch.check}));
    await screen.findByRole('alert');
    await waitFor(() => expect(screen.getByRole('button', {name: ru.internetSearch.check})).toBeEnabled());
    mocks.POST.mockResolvedValue({data: {configured: false, state: 'limited'}, response: new Response()});
    fireEvent.click(screen.getByRole('button', {name: ru.internetSearch.check}));
    expect(await screen.findByText(ru.internetSearch.states.limited)).toBeVisible();
});
