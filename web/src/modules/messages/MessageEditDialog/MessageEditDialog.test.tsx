import {ThemeProvider, TextArea} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor, within} from '@testing-library/react';
import {useState} from 'react';
import {beforeEach, expect, it, vi} from 'vitest';
import {MessageEditDialog} from '@/modules/messages/MessageEditDialog/MessageEditDialog';
import type {MessageEditEditorProps} from '@/modules/messages/MessageEditEditor/MessageEditEditor.types';
import type {MessageEditOptions} from '@aigram/api-client';
import type {Message} from '@/shared/api/client';
import {ApiRequestError} from '@/shared/api/ApiRequestError';
import ru from '@/shared/i18n/ru.json';

const mocks = vi.hoisted(() => ({messageEditOptions: vi.fn(), editMessage: vi.fn()}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: {chats: {messageEditOptions: mocks.messageEditOptions, editMessage: mocks.editMessage}},
}));
vi.mock('@/modules/messages/MessageEditEditor/MessageEditEditor', () => ({
    MessageEditEditor: function Example({
        initialDraft,
        onChange,
        disabled,
        ariaLabel,
    }: MessageEditEditorProps) {
        const [draft, setDraft] = useState(initialDraft);
        return (
            <TextArea
                value={draft.text}
                disabled={disabled}
                controlProps={{'aria-label': ariaLabel}}
                onUpdate={(text) => {
                    const next = {...draft, text};
                    setDraft(next);
                    onChange(next);
                }}
            />
        );
    },
}));

const message: Message = {id: '123', chatId: '42', text: 'Original', outgoing: true, kind: 'text', date: 1};
const options: MessageEditOptions = {
    text: 'Original',
    entities: [{kind: 'existing', sourceIndex: 7, typeHint: 'bold', offset: 0, length: 8}],
    revision: 'a'.repeat(64),
    kind: 'text',
    maxLength: 4096,
};

beforeEach(() => {
    vi.clearAllMocks();
    mocks.messageEditOptions.mockReset();
    mocks.editMessage.mockReset();
    mocks.messageEditOptions.mockResolvedValue(options);
    mocks.editMessage.mockResolvedValue({...message, text: 'Original updated', editedAt: 100});
});

function mount(value = message, onUpdated = vi.fn(), onClose = vi.fn()) {
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <MessageEditDialog message={value} onUpdated={onUpdated} onClose={onClose} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    return {onUpdated, onClose};
}

it('patches the confirmed message once, stays open and refreshes entity references before another save', async () => {
    let resolveRefresh: (value: MessageEditOptions) => void = () => {};
    mocks.messageEditOptions.mockResolvedValueOnce(options).mockImplementationOnce(
        () =>
            new Promise((resolve) => {
                resolveRefresh = resolve;
            }),
    );
    const {onUpdated, onClose} = mount();
    const input = await screen.findByRole('textbox', {name: ru.messageEdit.titleText});
    fireEvent.change(input, {target: {value: 'Original updated'}});
    fireEvent.click(screen.getByRole('button', {name: ru.messageEdit.save}));
    await waitFor(() => expect(onUpdated).toHaveBeenCalledOnce());
    expect(mocks.editMessage).toHaveBeenCalledWith({
        chatId: '42',
        messageId: '123',
        messageEditRequest: {
            text: 'Original updated',
            revision: options.revision,
            entities: [{...options.entities[0], url: '', language: ''}],
        },
    });
    expect(input).toBeDisabled();
    expect(onClose).not.toHaveBeenCalled();
    resolveRefresh({...options, text: 'Original updated', revision: 'b'.repeat(64)});
    await waitFor(() => expect(screen.getByRole('textbox', {name: ru.messageEdit.titleText})).toBeEnabled());
    expect(screen.getByRole('button', {name: ru.saved})).toBeDisabled();
    expect(screen.getByRole('dialog', {name: ru.messageEdit.titleText})).toBeVisible();
});

it('allows removing a media caption and preserves exact whitespace on a later edit', async () => {
    mocks.messageEditOptions
        .mockResolvedValueOnce({...options, kind: 'caption', entities: []})
        .mockResolvedValue({...options, text: '', kind: 'caption', entities: [], revision: 'b'.repeat(64)});
    mount({...message, kind: 'photo'});
    const input = await screen.findByRole('textbox', {name: ru.messageEdit.titleCaption});
    fireEvent.change(input, {target: {value: ''}});
    fireEvent.click(screen.getByRole('button', {name: ru.messageEdit.save}));
    await waitFor(() =>
        expect(mocks.editMessage).toHaveBeenCalledWith(
            expect.objectContaining({
                messageEditRequest: {text: '', entities: [], revision: options.revision},
            }),
        ),
    );
    const refreshed = await screen.findByRole('textbox', {name: ru.messageEdit.titleCaption});
    await waitFor(() => expect(refreshed).toBeEnabled());
    fireEvent.change(refreshed, {target: {value: '  Caption  '}});
    fireEvent.click(screen.getByRole('button', {name: ru.messageEdit.save}));
    await waitFor(() =>
        expect(mocks.editMessage).toHaveBeenLastCalledWith(
            expect.objectContaining({
                messageEditRequest: {text: '  Caption  ', entities: [], revision: 'b'.repeat(64)},
            }),
        ),
    );
});

it('keeps a conflicting draft until an explicit discard and reload', async () => {
    mocks.editMessage.mockRejectedValue(
        new ApiRequestError('Internal backend detail', 409, 'MESSAGE_EDIT_CONFLICT'),
    );
    mount();
    const input = await screen.findByRole('textbox', {name: ru.messageEdit.titleText});
    fireEvent.change(input, {target: {value: 'Unsaved draft'}});
    fireEvent.click(screen.getByRole('button', {name: ru.messageEdit.save}));
    expect(await screen.findByRole('alert')).toHaveTextContent(ru.messageEdit.changedElsewhere);
    expect(input).toHaveValue('Unsaved draft');
    fireEvent.click(screen.getByRole('button', {name: ru.messageEdit.reload}));
    const confirmation = await screen.findByRole('dialog', {name: ru.messageEdit.discardTitle});
    expect(mocks.messageEditOptions).toHaveBeenCalledOnce();
    fireEvent.click(await within(confirmation).findByRole('button', {name: ru.messageEdit.keepEditing}));
    expect(input).toHaveValue('Unsaved draft');
    expect(mocks.editMessage).toHaveBeenCalledOnce();
});

it('distinguishes a confirmed edit from a failed refresh and only retries the read', async () => {
    mocks.messageEditOptions
        .mockResolvedValueOnce(options)
        .mockRejectedValueOnce(new Error('Private transport detail'));
    const {onUpdated} = mount();
    const input = await screen.findByRole('textbox', {name: ru.messageEdit.titleText});
    fireEvent.change(input, {target: {value: 'Original updated'}});
    fireEvent.click(screen.getByRole('button', {name: ru.messageEdit.save}));
    expect(await screen.findByText(ru.messageEdit.refreshFailedAfterSave)).toBeVisible();
    expect(input).toBeDisabled();
    expect(onUpdated).toHaveBeenCalledOnce();
    expect(screen.queryByText('Private transport detail')).toBeNull();
    mocks.messageEditOptions.mockResolvedValue({
        ...options,
        text: 'Original updated',
        revision: 'b'.repeat(64),
    });
    fireEvent.click(screen.getByRole('button', {name: ru.messageEdit.retryRefresh}));
    await waitFor(() => expect(screen.getByRole('textbox', {name: ru.messageEdit.titleText})).toBeEnabled());
    expect(mocks.editMessage).toHaveBeenCalledOnce();
});

it('asks before Escape discards changes and never mutates Telegram on cancellation', async () => {
    const {onClose} = mount();
    const input = await screen.findByRole('textbox', {name: ru.messageEdit.titleText});
    fireEvent.change(input, {target: {value: 'My draft'}});
    fireEvent.keyDown(document, {key: 'Escape', code: 'Escape'});
    const confirmation = await screen.findByRole('dialog', {name: ru.messageEdit.discardTitle});
    expect(onClose).not.toHaveBeenCalled();
    fireEvent.click(await within(confirmation).findByRole('button', {name: ru.messageEdit.discard}));
    expect(onClose).toHaveBeenCalledOnce();
    expect(mocks.editMessage).not.toHaveBeenCalled();
});

it('allows emoji exactly at the Telegram code point limit', async () => {
    mocks.messageEditOptions.mockResolvedValue({...options, text: 'Hi', entities: [], maxLength: 4});
    mount();
    const input = await screen.findByRole('textbox', {name: ru.messageEdit.titleText});
    fireEvent.change(input, {target: {value: '🙂🙂🙂🙂'}});
    expect(screen.getByText('4 / 4')).toBeVisible();
    expect(screen.getByRole('button', {name: ru.messageEdit.save})).toBeEnabled();
    fireEvent.click(screen.getByRole('button', {name: ru.messageEdit.save}));
    await waitFor(() =>
        expect(mocks.editMessage).toHaveBeenCalledWith(
            expect.objectContaining({
                messageEditRequest: {text: '🙂🙂🙂🙂', entities: [], revision: options.revision},
            }),
        ),
    );
});
