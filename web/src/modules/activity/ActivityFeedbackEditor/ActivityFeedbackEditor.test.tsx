import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {beforeEach, expect, it, vi} from 'vitest';
import {useState} from 'react';

import type {components} from '@/shared/api/schema';
import {ActivityFeedbackEditor} from '@/modules/activity/ActivityFeedbackEditor/ActivityFeedbackEditor';
import ru from '@/shared/i18n/ru.json';

const mocks = vi.hoisted(() => ({PUT: vi.fn(), DELETE: vi.fn()}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: mocks,
}));

function Example({onRefresh}: {readonly onRefresh?: () => Promise<void>}) {
    const [value, setValue] = useState<components['schemas']['ActivityFeedback'] | null>(null);
    return (
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ActivityFeedbackEditor id="sent" value={value} onUpdated={setValue} onRefresh={onRefresh} />
            </ThemeProvider>
        </NextIntlClientProvider>
    );
}

beforeEach(() => {
    mocks.PUT.mockReset();
    mocks.DELETE.mockReset();
});

it('saves a comment with its rating, edits it and removes the entire feedback', async () => {
    mocks.PUT.mockImplementation(async (_: unknown, request: {body: {rating: string; comment: string}}) => ({
        data: {...request.body, updatedAt: 1},
        response: new Response(),
    }));
    mocks.DELETE.mockResolvedValue({data: {ok: true}, response: new Response()});
    render(<Example />);
    fireEvent.click(screen.getByRole('button', {name: ru.events.feedback.bad}));
    fireEvent.change(screen.getByRole('textbox', {name: ru.events.feedback.comment}), {
        target: {value: 'Слишком формально.'},
    });
    fireEvent.click(screen.getByRole('button', {name: ru.events.feedback.save}));
    expect(await screen.findByRole('status')).toHaveTextContent(ru.events.feedback.saved);
    expect(mocks.PUT).toHaveBeenLastCalledWith('/api/activity/{id}/feedback', {
        params: {path: {id: 'sent'}},
        body: {rating: 'bad', comment: 'Слишком формально.'},
    });
    fireEvent.click(screen.getByRole('button', {name: ru.events.feedback.good}));
    fireEvent.change(screen.getByRole('textbox', {name: ru.events.feedback.comment}), {target: {value: ''}});
    fireEvent.click(screen.getByRole('button', {name: ru.events.feedback.save}));
    await waitFor(() =>
        expect(mocks.PUT).toHaveBeenLastCalledWith('/api/activity/{id}/feedback', {
            params: {path: {id: 'sent'}},
            body: {rating: 'good', comment: ''},
        }),
    );
    fireEvent.click(screen.getByRole('button', {name: ru.events.feedback.remove}));
    await waitFor(() =>
        expect(screen.getByRole('textbox', {name: ru.events.feedback.comment})).toHaveValue(''),
    );
    await waitFor(() => expect(screen.queryByRole('button', {name: ru.events.feedback.remove})).toBeNull());
});

it('keeps an unsaved comment and selected rating after a failed save', async () => {
    mocks.PUT.mockResolvedValue({
        error: {message: 'Не удалось сохранить оценку.'},
        response: new Response('', {status: 503}),
    });
    render(<Example />);
    fireEvent.click(screen.getByRole('button', {name: ru.events.feedback.bad}));
    fireEvent.change(screen.getByRole('textbox', {name: ru.events.feedback.comment}), {
        target: {value: 'Не учтена договорённость.'},
    });
    fireEvent.click(screen.getByRole('button', {name: ru.events.feedback.save}));
    expect(await screen.findByRole('alert')).toHaveTextContent('Не удалось сохранить оценку.');
    expect(screen.getByRole('textbox', {name: ru.events.feedback.comment})).toHaveValue(
        'Не учтена договорённость.',
    );
    expect(screen.getByRole('button', {name: ru.events.feedback.bad})).toHaveAttribute(
        'aria-pressed',
        'true',
    );
});

it('treats a persisted rating as saved even if refreshing the activity list fails', async () => {
    mocks.PUT.mockResolvedValue({
        data: {rating: 'good', comment: 'Коротко и понятно.', updatedAt: 1},
        response: new Response(),
    });
    render(<Example onRefresh={vi.fn().mockRejectedValue(new Error('Snapshot unavailable'))} />);
    fireEvent.click(screen.getByRole('button', {name: ru.events.feedback.good}));
    fireEvent.change(screen.getByRole('textbox', {name: ru.events.feedback.comment}), {
        target: {value: 'Коротко и понятно.'},
    });
    fireEvent.click(screen.getByRole('button', {name: ru.events.feedback.save}));
    expect(await screen.findByRole('status')).toHaveTextContent(ru.events.feedback.saved);
    expect(await screen.findByRole('alert')).toHaveTextContent(ru.events.feedback.refreshFailed);
    expect(screen.getByRole('textbox', {name: ru.events.feedback.comment})).toHaveValue('Коротко и понятно.');
    expect(mocks.PUT).toHaveBeenCalledTimes(1);
});
