import {afterEach, describe, expect, it, vi} from 'vitest';
import {WysiwygEditor} from '@gravity-ui/markdown-editor/core';
import {BaseInputRules, BaseKeymap, BaseSchema, History} from '@gravity-ui/markdown-editor/extensions';
import {undo, redo} from '@gravity-ui/markdown-editor/pm/history';
import {NodeSelection, TextSelection} from '@gravity-ui/markdown-editor/pm/state';
import {
    createMessageEditExtension,
    toggleMessageEditFormat,
    type MessageEditBridge,
} from '@/modules/messages/MessageEditEditor/messageEditExtension';
import {readMessageEditDocument} from '@/modules/messages/MessageEditEditor/messageEditDocument';
import type {
    MessageEditDraft,
    MessageEditEntity,
} from '@/modules/messages/MessageEditEditor/MessageEditEditor.types';

const editors: WysiwygEditor[] = [];
afterEach(() => editors.splice(0).forEach((editor) => editor.destroy()));

function entity(
    sourceIndex: number,
    offset: number,
    length: number,
    typeHint = 'futureentity',
): MessageEditEntity {
    return {kind: 'existing', sourceIndex, offset, length, typeHint, url: '', language: ''};
}

async function setup(draft: MessageEditDraft) {
    const change = vi.fn();
    const error = vi.fn();
    const bridge: MessageEditBridge = {
        view: null,
        draftId: 'synthetic-revision',
        initialDraft: draft,
        disabled: false,
        failed: false,
        ariaLabel: 'Edit message',
        autoFocus: false,
        sequence: 0,
        change,
        error,
        selectionChange: vi.fn(),
    };
    const editor = new WysiwygEditor({
        initialContent: '',
        mdPreset: 'zero',
        allowHTML: false,
        extensions: (builder) => {
            builder.use(BaseSchema, {}).use(BaseKeymap).use(BaseInputRules).use(History, {});
            builder.use(createMessageEditExtension(bridge));
        },
    });
    editors.push(editor);
    document.body.append(editor.dom);
    await Promise.resolve();
    expect(error).not.toHaveBeenCalled();
    expect(bridge.view).not.toBeNull();
    const view = bridge.view!;
    return {bridge, view, change, read: () => readMessageEditDocument(view.state.doc)};
}

function clipboardEvent(type: string, data: Map<string, string>): Event {
    const event = new Event(type, {bubbles: true, cancelable: true});
    Object.defineProperty(event, 'clipboardData', {
        value: {
            getData: (key: string) => data.get(key) ?? '',
            setData: (key: string, value: string) => data.set(key, value),
            get types() {
                return [...data.keys()];
            },
        },
    });
    return event;
}

describe('Telegram editor document and public ProseMirror bridge', () => {
    it('preserves literal Markdown empty lines trailing LF and overlapping unsupported entities', async () => {
        const text = '**literal** ...\n\nПривет 😀\n';
        const draft = {text, entities: [entity(0, 0, text.length), entity(1, 0, 11, 'bold')]};
        const {read, view} = await setup(draft);
        expect(read()).toEqual(draft);
        expect(view.state.plugins.some((plugin) => plugin.spec.isInputRules)).toBe(false);
    });

    it('round trips an empty caption without manufacturing a newline', async () => {
        const {read} = await setup({text: '', entities: []});
        expect(read()).toEqual({text: '', entities: []});
    });

    it('preserves nested partial formatting inside atomic mentions and custom emoji surrogate pairs', async () => {
        const draft = {
            text: 'Hi Alice 😀!',
            entities: [
                entity(0, 3, 5, 'mentionname'),
                entity(1, 3, 2, 'bold'),
                entity(2, 9, 2, 'customemoji'),
            ],
        };
        const {read, view} = await setup(draft);
        expect(read()).toEqual(draft);
        expect(view.dom.querySelectorAll('[contenteditable="false"]')).toHaveLength(2);
        expect(view.dom.innerHTML).not.toContain('sourceIndex');
        expect(view.dom.innerHTML).not.toContain('synthetic-revision');
    });

    it('maps opaque ranges through a normal edit and restores exact payload identity on undo and redo', async () => {
        const draft = {text: 'prefix opaque suffix', entities: [entity(0, 7, 6)]};
        const {view, read} = await setup(draft);
        view.dispatch(view.state.tr.insertText('X', 1));
        expect(read().entities).toEqual([entity(0, 8, 6)]);
        expect(undo(view.state, view.dispatch)).toBe(true);
        expect(read()).toEqual(draft);
        expect(redo(view.state, view.dispatch)).toBe(true);
        expect(read().entities).toEqual([entity(0, 8, 6)]);
    });

    it('deletes an emoji as one atomic token and undo restores its source reference and nested marks', async () => {
        const draft = {text: 'A😀B', entities: [entity(0, 0, 4), entity(1, 1, 2, 'customemoji')]};
        const {view, read} = await setup(draft);
        view.dispatch(view.state.tr.setSelection(NodeSelection.create(view.state.doc, 2)).deleteSelection());
        expect(read()).toEqual({text: 'AB', entities: [entity(0, 0, 2)]});
        undo(view.state, view.dispatch);
        expect(read()).toEqual(draft);
    });

    it('removes only a selected formatting instance and retains overlapping unknown marks', async () => {
        const {view, bridge, read} = await setup({
            text: 'abcdef',
            entities: [entity(0, 0, 6, 'bold'), entity(1, 0, 6)],
        });
        view.dispatch(view.state.tr.setSelection(TextSelection.create(view.state.doc, 3, 5)));
        toggleMessageEditFormat(bridge, 'bold');
        expect(read().entities).toEqual(
            [entity(0, 0, 2, 'bold'), entity(1, 0, 6), entity(0, 4, 2, 'bold')].sort(
                (left, right) => left.offset - right.offset || right.length - left.length,
            ),
        );
    });

    it('adds known formatting while retaining original opaque references', async () => {
        const {view, bridge, read} = await setup({text: 'abc', entities: [entity(0, 0, 3)]});
        view.dispatch(view.state.tr.setSelection(TextSelection.create(view.state.doc, 1, 4)));
        toggleMessageEditFormat(bridge, 'italic');
        expect(read().entities).toContainEqual(entity(0, 0, 3));
        expect(read().entities).toContainEqual({
            kind: 'italic',
            sourceIndex: null,
            typeHint: '',
            url: '',
            language: '',
            offset: 0,
            length: 3,
        });
    });

    it('uses inline LF for Enter and leaves composing Enter to the IME', async () => {
        const {view, read} = await setup({text: '😀', entities: [entity(0, 0, 2, 'bold')]});
        view.dom.dispatchEvent(
            new KeyboardEvent('keydown', {key: 'Enter', isComposing: true, bubbles: true}),
        );
        expect(read().text).toBe('😀');
        view.dom.dispatchEvent(new KeyboardEvent('keydown', {key: 'Enter', bubbles: true}));
        expect(read()).toEqual({text: '😀\n', entities: [entity(0, 0, 3, 'bold')]});
        expect(view.state.doc.childCount).toBe(1);
    });

    it('leaves Ctrl and Cmd Enter to the save shortcut without inserting an extra newline', async () => {
        const {view, read} = await setup({text: 'unchanged', entities: []});
        const control = new KeyboardEvent('keydown', {
            key: 'Enter',
            ctrlKey: true,
            bubbles: true,
            cancelable: true,
        });
        const command = new KeyboardEvent('keydown', {
            key: 'Enter',
            metaKey: true,
            bubbles: true,
            cancelable: true,
        });
        view.dom.dispatchEvent(control);
        view.dom.dispatchEvent(command);
        expect(read()).toEqual({text: 'unchanged', entities: []});
        expect(control.defaultPrevented).toBe(false);
        expect(command.defaultPrevented).toBe(false);
    });

    it('preserves all source references through cut paste and undo using a revision bound fragment', async () => {
        const draft = {text: 'A😀\nB', entities: [entity(0, 0, 5), entity(1, 1, 2, 'customemoji')]};
        const {view, read} = await setup(draft);
        view.dispatch(
            view.state.tr.setSelection(
                TextSelection.create(view.state.doc, 1, view.state.doc.content.size - 1),
            ),
        );
        const data = new Map<string, string>();
        view.dom.dispatchEvent(clipboardEvent('cut', data));
        expect(read()).toEqual({text: '', entities: []});
        view.dom.dispatchEvent(clipboardEvent('paste', data));
        expect(read()).toEqual(draft);
        expect(undo(view.state, view.dispatch)).toBe(true);
        expect(read().text).toBe('');
    });

    it('keeps an adjacent copied custom emoji as two original entities with separate UTF16 ranges', async () => {
        const {view, read} = await setup({text: '😀', entities: [entity(0, 0, 2, 'customemoji')]});
        view.dispatch(view.state.tr.setSelection(NodeSelection.create(view.state.doc, 1)));
        const data = new Map<string, string>();
        view.dom.dispatchEvent(clipboardEvent('copy', data));
        view.dispatch(view.state.tr.setSelection(TextSelection.atEnd(view.state.doc)));
        view.dom.dispatchEvent(clipboardEvent('paste', data));
        expect(read()).toEqual({
            text: '😀😀',
            entities: [entity(0, 0, 2, 'customemoji'), entity(0, 2, 2, 'customemoji')],
        });
    });

    it('treats external or forged fragments as literal plain text without Markdown parsing', async () => {
        const {view, read} = await setup({text: '', entities: []});
        const data = new Map([
            ['application/x-aigram-message-edit', JSON.stringify({draftId: 'another-account', slice: {}})],
            ['text/plain', '**literal** ...\n\n'],
            ['text/html', '<b>changed</b>'],
        ]);
        view.dom.dispatchEvent(clipboardEvent('paste', data));
        expect(read()).toEqual({text: '**literal** ...\n\n', entities: []});
    });

    it('rejects a same-revision fragment that invents an original source reference', async () => {
        const {view, read} = await setup({text: 'x', entities: []});
        view.dispatch(view.state.tr.setSelection(TextSelection.create(view.state.doc, 1, 2)));
        const copied = new Map<string, string>();
        view.dom.dispatchEvent(clipboardEvent('copy', copied));
        const nonce = JSON.parse(copied.get('application/x-aigram-message-edit')!).nonce;
        const data = new Map([
            [
                'application/x-aigram-message-edit',
                JSON.stringify({
                    draftId: 'synthetic-revision',
                    nonce,
                    slice: {
                        content: [
                            {
                                type: 'text',
                                text: 'forged',
                                marks: [
                                    {
                                        type: 'telegramEntity',
                                        attrs: {
                                            entityId: 'forged',
                                            kind: 'existing',
                                            sourceIndex: 99,
                                            typeHint: 'futureentity',
                                            url: '',
                                            language: '',
                                        },
                                    },
                                ],
                            },
                        ],
                    },
                }),
            ],
            ['text/plain', 'safe plain text'],
        ]);
        view.dom.dispatchEvent(clipboardEvent('paste', data));
        expect(read()).toEqual({text: 'safe plain text', entities: []});
    });

    it('does not import source references from another editor instance even with the same revision', async () => {
        const draft = {text: 'opaque', entities: [entity(0, 0, 6)]};
        const first = await setup(draft);
        const second = await setup(draft);
        first.view.dispatch(
            first.view.state.tr.setSelection(TextSelection.create(first.view.state.doc, 1, 7)),
        );
        second.view.dispatch(
            second.view.state.tr
                .setSelection(TextSelection.create(second.view.state.doc, 1, 7))
                .deleteSelection()
                .setStoredMarks([]),
        );
        const data = new Map<string, string>();
        first.view.dom.dispatchEvent(clipboardEvent('copy', data));
        second.view.dom.dispatchEvent(clipboardEvent('paste', data));
        expect(second.read()).toEqual({text: 'opaque', entities: []});
    });
});
