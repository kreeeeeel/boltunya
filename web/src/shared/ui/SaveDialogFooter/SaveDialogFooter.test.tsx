import {Button, Dialog, ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {act, fireEvent, render, screen} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {useState} from 'react';

import {SaveDialogFooter} from '@/shared/ui/SaveDialogFooter/SaveDialogFooter';
import {useSaveFeedback} from '@/shared/ui/SaveDialogFooter/useSaveFeedback';
import ru from '@/shared/i18n/ru.json';

function Example() {
    const [value, setValue] = useState('first');
    const feedback = useSaveFeedback(value);
    return (
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <Dialog open onClose={vi.fn()}>
                    <Dialog.Body>
                        <Button onClick={() => setValue('next')}>Edit</Button>
                    </Dialog.Body>
                    <SaveDialogFooter
                        saved={feedback.saved}
                        busy={false}
                        saveLabel="Save"
                        cancelLabel="Close"
                        onClose={vi.fn()}
                        onSave={feedback.markSaved}
                    />
                </Dialog>
            </ThemeProvider>
        </NextIntlClientProvider>
    );
}

it('shows success inside the save button briefly and keeps the dialog open', () => {
    vi.useFakeTimers();
    const view = render(<Example />);
    try {
        fireEvent.click(screen.getByRole('button', {name: 'Save'}));
        const saved = screen.getByRole('button', {name: ru.saved});
        expect(saved).toHaveAttribute('aria-label', ru.saved);
        expect(saved.querySelector('svg')).not.toBeNull();
        expect(screen.getByRole('dialog')).toBeVisible();
        act(() => vi.advanceTimersByTime(3000));
        expect(screen.getByRole('button', {name: 'Save'})).toBeVisible();
        fireEvent.click(screen.getByRole('button', {name: 'Save'}));
        fireEvent.click(screen.getByRole('button', {name: 'Edit'}));
        expect(screen.queryByRole('status')).toBeNull();
    } finally {
        view.unmount();
        vi.useRealTimers();
    }
});
