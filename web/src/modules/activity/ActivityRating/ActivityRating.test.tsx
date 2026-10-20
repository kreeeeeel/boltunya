import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {beforeEach, expect, it, vi} from 'vitest';

import {ActivityRating} from '@/modules/activity/ActivityRating/ActivityRating';
import ru from '@/shared/i18n/ru.json';

const mocks = vi.hoisted(() => ({PUT: vi.fn(), DELETE: vi.fn()}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: mocks,
}));

beforeEach(() => {
    mocks.PUT.mockReset();
    mocks.DELETE.mockReset();
});

it('quickly changes a rating without overwriting its saved comment and can remove it', async () => {
    const onRefresh = vi.fn().mockResolvedValue(undefined);
    mocks.PUT.mockResolvedValue({
        data: {rating: 'good', comment: 'Сохранённый комментарий', updatedAt: 1},
        response: new Response(),
    });
    mocks.DELETE.mockResolvedValue({data: {ok: true}, response: new Response()});
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ActivityRating id="sent" rating="bad" onDetails={vi.fn()} onRefresh={onRefresh} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    fireEvent.click(screen.getByRole('button', {name: ru.events.feedback.good}));
    await waitFor(() =>
        expect(mocks.PUT).toHaveBeenCalledWith('/api/activity/{id}/feedback', {
            params: {path: {id: 'sent'}},
            body: {rating: 'good'},
        }),
    );
    await waitFor(() =>
        expect(screen.getByRole('button', {name: ru.events.feedback.good})).toHaveAttribute(
            'aria-pressed',
            'true',
        ),
    );
    fireEvent.click(screen.getByRole('button', {name: ru.events.feedback.good}));
    await waitFor(() =>
        expect(mocks.DELETE).toHaveBeenCalledWith('/api/activity/{id}/feedback', {
            params: {path: {id: 'sent'}},
        }),
    );
    await waitFor(() =>
        expect(screen.getByRole('button', {name: ru.events.feedback.good})).toHaveAttribute(
            'aria-pressed',
            'false',
        ),
    );
});

it('retains the existing rating when a request fails', async () => {
    mocks.PUT.mockResolvedValue({
        error: {message: 'Не удалось сохранить оценку.'},
        response: new Response('', {status: 503}),
    });
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ActivityRating id="sent" rating="good" onDetails={vi.fn()} onRefresh={vi.fn()} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    fireEvent.click(screen.getByRole('button', {name: ru.events.feedback.bad}));
    expect(await screen.findByRole('alert')).toHaveTextContent('Не удалось сохранить оценку.');
    expect(screen.getByRole('button', {name: ru.events.feedback.good})).toHaveAttribute(
        'aria-pressed',
        'true',
    );
});

it('reflects ratings changed in the details dialog after the activity snapshot refreshes', () => {
    const renderRating = (rating: 'good' | 'bad' | null) => (
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ActivityRating id="sent" rating={rating} onDetails={vi.fn()} onRefresh={vi.fn()} />
            </ThemeProvider>
        </NextIntlClientProvider>
    );
    const view = render(renderRating('good'));
    view.rerender(renderRating('bad'));
    expect(screen.getByRole('button', {name: ru.events.feedback.bad})).toHaveAttribute(
        'aria-pressed',
        'true',
    );
    view.rerender(renderRating(null));
    expect(screen.getByRole('button', {name: ru.events.feedback.bad})).toHaveAttribute(
        'aria-pressed',
        'false',
    );
});
