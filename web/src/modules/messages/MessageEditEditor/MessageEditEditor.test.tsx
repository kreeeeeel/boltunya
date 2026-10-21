import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {render, screen, waitFor} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {MessageEditEditor} from '@/modules/messages/MessageEditEditor/MessageEditEditor';

it('mounts the real Gravity view with literal text a compact toolbar and no Markdown source control', async () => {
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
                    initialDraft={{text: '**literal** ...\n', entities: []}}
                    draftId="synthetic-revision"
                    ariaLabel="Edit message"
                    onChange={change}
                    onError={error}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    await waitFor(() => expect(change).toHaveBeenLastCalledWith({text: '**literal** ...\n', entities: []}));
    expect(screen.getByRole('textbox', {name: 'Edit message'})).toHaveTextContent('**literal** ...');
    expect(screen.getByRole('toolbar', {name: 'Edit message'})).toBeVisible();
    expect(screen.getByRole('button', {name: 'Bold'})).toBeEnabled();
    expect(screen.queryByRole('combobox')).toBeNull();
    expect(error).not.toHaveBeenCalled();
});
