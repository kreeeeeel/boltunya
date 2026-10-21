import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {beforeEach, expect, it, vi} from 'vitest';
import {useState} from 'react';
import type * as Contract from '@aigram/api-client';
import {ActivityFeedbackEditor} from '@/modules/activity/ActivityFeedbackEditor/ActivityFeedbackEditor';
import ru from '@/shared/i18n/ru.json';
const mocks = vi.hoisted(() => ({saveActivityFeedback: vi.fn(), removeActivityFeedback: vi.fn()}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: {
        activity: {
            saveActivityFeedback: mocks.saveActivityFeedback,
            removeActivityFeedback: mocks.removeActivityFeedback,
        },
    },
}));
function Example({onRefresh}: {readonly onRefresh?: () => Promise<void>}) {
    const [value, setValue] = useState<Contract.ActivityFeedback | null>(null);
    return (
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ActivityFeedbackEditor id="sent" value={value} onUpdated={setValue} onRefresh={onRefresh} />
            </ThemeProvider>
        </NextIntlClientProvider>
    );
}
beforeEach(() => {
    mocks.saveActivityFeedback.mockReset();
    mocks.removeActivityFeedback.mockReset();
});
it('saves a comment with its rating, edits it and removes the entire feedback', async () => {
    mocks.saveActivityFeedback.mockImplementation(
        async (request: {
            activityFeedbackUpdate: {
                rating: string;
                comment: string;
            };
        }) => ({...request.activityFeedbackUpdate, updatedAt: 1}),
    );
    mocks.removeActivityFeedback.mockResolvedValue({ok: true});
    render(<Example />);
    fireEvent.click(screen.getByRole('button', {name: ru.events.feedback.bad}));
    fireEvent.change(screen.getByRole('textbox', {name: ru.events.feedback.comment}), {
        target: {value: 'Слишком формально.'},
    });
    fireEvent.click(screen.getByRole('button', {name: ru.events.feedback.save}));
    expect(await screen.findByRole('status')).toHaveTextContent(ru.events.feedback.saved);
    expect(mocks.saveActivityFeedback).toHaveBeenLastCalledWith({
        id: 'sent',
        activityFeedbackUpdate: {rating: 'bad', comment: 'Слишком формально.'},
    });
    fireEvent.click(screen.getByRole('button', {name: ru.events.feedback.good}));
    fireEvent.change(screen.getByRole('textbox', {name: ru.events.feedback.comment}), {target: {value: ''}});
    fireEvent.click(screen.getByRole('button', {name: ru.events.feedback.save}));
    await waitFor(() =>
        expect(mocks.saveActivityFeedback).toHaveBeenLastCalledWith({
            id: 'sent',
            activityFeedbackUpdate: {rating: 'good', comment: ''},
        }),
    );
    fireEvent.click(screen.getByRole('button', {name: ru.events.feedback.remove}));
    await waitFor(() =>
        expect(screen.getByRole('textbox', {name: ru.events.feedback.comment})).toHaveValue(''),
    );
    await waitFor(() => expect(screen.queryByRole('button', {name: ru.events.feedback.remove})).toBeNull());
});
it('keeps an unsaved comment and selected rating after a failed save', async () => {
    mocks.saveActivityFeedback.mockRejectedValue(new Error('Не удалось сохранить оценку.'));
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
    mocks.saveActivityFeedback.mockResolvedValue({
        rating: 'good',
        comment: 'Коротко и понятно.',
        updatedAt: 1,
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
    expect(mocks.saveActivityFeedback).toHaveBeenCalledTimes(1);
});
