import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {ConversationComposer} from '@/modules/workspace/Conversation/ConversationComposer';
import ru from '@/shared/i18n/ru.json';

it('keeps the input visible during queued generation and preserves a typed draft', () => {
    const update = vi.fn();
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ConversationComposer
                    chatId="42"
                    text="My draft"
                    onTextChange={update}
                    busy="draft"
                    onSend={vi.fn()}
                    onDraft={vi.fn()}
                    onMediaSent={vi.fn()}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    expect(screen.getByRole('textbox', {name: ru.messagePlaceholder})).toHaveValue('My draft');
    fireEvent.change(screen.getByRole('textbox', {name: ru.messagePlaceholder}), {
        target: {value: 'Edited draft'},
    });
    expect(update).toHaveBeenCalledWith('Edited draft');
    expect(screen.getByRole('button', {name: ru.draft})).toBeDisabled();
    expect(document.querySelector('.g-skeleton')).toBeNull();
});
