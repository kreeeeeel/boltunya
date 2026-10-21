import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {render, screen, waitFor} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {MessageEditEditor} from '@/modules/messages/MessageEditEditor/MessageEditEditor';

it.each(['**literal** ...\n', ''])(
    'mounts the real Gravity view with exact text %j and no Markdown source control',
    async (text) => {
        const change = vi.fn();
        const error = vi.fn();
        const messages = {
            messageEditFormat: {
                bold: 'Bold',
                italic: 'Italic',
                underline: 'Underline',
                strike: 'Strike',
                code: 'Code',
                spoiler: 'Spoiler',
                link: 'Link',
                quote: 'Quote',
                undo: 'Undo',
                redo: 'Redo',
                linkUrl: 'URL',
                linkApply: 'Apply',
                linkCancel: 'Cancel',
                error: 'Editor failed',
            },
        };
        render(
            <NextIntlClientProvider locale="en" messages={messages}>
                <ThemeProvider>
                    <MessageEditEditor
                        initialDraft={{text, entities: []}}
                        draftId="synthetic-revision"
                        ariaLabel="Edit message"
                        onChange={change}
                        onError={error}
                    />
                </ThemeProvider>
            </NextIntlClientProvider>,
        );
        await waitFor(() => expect(change).toHaveBeenLastCalledWith({text, entities: []}));
        expect(screen.getByRole('textbox', {name: 'Edit message'}).textContent).toBe(
            text.replaceAll('\n', ''),
        );
        expect(screen.getByRole('toolbar', {name: 'Edit message'})).toBeVisible();
        expect(screen.getByRole('button', {name: 'Bold'})).toBeEnabled();
        expect(screen.queryByRole('combobox')).toBeNull();
        expect(error).not.toHaveBeenCalled();
    },
);
