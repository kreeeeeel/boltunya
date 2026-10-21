import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {act, fireEvent, render, screen, waitFor} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import '@/modules/messages/MessageEditEditor/MessageEditEditor';
import {MessageDraftEditor} from '@/modules/messages/MessageDraftEditor/MessageDraftEditor';
import styles from '@/modules/messages/MessageEditEditor/MessageEditEditor.module.css';
import ru from '@/shared/i18n/ru.json';

it('renders actual marks and updates Telegram ranges when Unicode text is pasted into the compact editor', async () => {
    const change = vi.fn();
    const error = vi.fn();
    const submit = vi.fn().mockResolvedValue(undefined);
    const initial = {text: 'Слово и ещё', entities: [{offset: 0, length: 5, type: 'bold'}]};
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <MessageDraftEditor
                    initialMessage={initial}
                    draftId="synthetic-draft"
                    ariaLabel={ru.messagePlaceholder}
                    onChange={change}
                    onError={error}
                    onSubmit={submit}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    await act(async () => {
        await vi.dynamicImportSettled();
    });
    await waitFor(() => expect(change).toHaveBeenLastCalledWith(initial));
    const editor = screen.getByRole('textbox', {name: ru.messagePlaceholder});
    expect(editor.querySelector(`.${styles.bold}`)).toHaveTextContent('Слово');
    expect(screen.queryByRole('button', {name: ru.chooseEmoji})).not.toBeInTheDocument();
    fireEvent.keyDown(editor, {key: 'Enter', code: 'Enter'});
    expect(submit).toHaveBeenCalledOnce();
    expect(change).toHaveBeenLastCalledWith(initial);
    fireEvent.keyDown(editor, {key: 'Enter', code: 'Enter', shiftKey: true});
    await waitFor(() => expect(change).toHaveBeenLastCalledWith({...initial, text: initial.text + '\n'}));
    fireEvent.click(screen.getByRole('button', {name: ru.messageEditFormat.italic}));
    fireEvent.paste(editor, {
        clipboardData: {getData: (type: string) => (type === 'text/plain' ? '🙂' : '')},
    });
    await waitFor(() =>
        expect(change).toHaveBeenLastCalledWith({
            text: initial.text + '\n🙂',
            entities: [...initial.entities, {offset: initial.text.length + 1, length: 2, type: 'italic'}],
        }),
    );
    expect(submit).toHaveBeenCalledOnce();
    expect(error).not.toHaveBeenCalled();
});
