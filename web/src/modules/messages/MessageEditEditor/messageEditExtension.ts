import type {Extension} from '@gravity-ui/markdown-editor/core';
import {Fragment, Slice, type Mark, type Node as ProseMirrorNode} from '@gravity-ui/markdown-editor/pm/model';
import {Plugin, TextSelection, type EditorState} from '@gravity-ui/markdown-editor/pm/state';
import {closeHistory} from '@gravity-ui/markdown-editor/pm/history';
import type {EditorView} from '@gravity-ui/markdown-editor/pm/view';
import {
    atomicEntityName,
    createMessageEditDocument,
    entityMarkName,
    readMessageEditDocument,
    softBreakName,
} from '@/modules/messages/MessageEditEditor/messageEditDocument';
import type {
    MessageEditDraft,
    MessageEditFormat,
    MessageEditMark,
} from '@/modules/messages/MessageEditEditor/MessageEditEditor.types';
import styles from '@/modules/messages/MessageEditEditor/MessageEditEditor.module.css';

const clipboardType = 'application/x-aigram-message-edit';
const formats: MessageEditFormat[] = [
    'bold',
    'italic',
    'underline',
    'strike',
    'code',
    'pre',
    'precode',
    'texturl',
    'spoiler',
    'quote',
    'expandablequote',
];

export interface MessageEditBridge {
    view: EditorView | null;
    draftId: string;
    initialDraft: MessageEditDraft;
    disabled: boolean;
    failed: boolean;
    ariaLabel: string;
    autoFocus: boolean;
    sequence: number;
    change(draft: MessageEditDraft): void;
    selectionChange(): void;
    error(): void;
}

export function configureMessageEditBridge(
    bridge: MessageEditBridge,
    options: Pick<MessageEditBridge, 'change' | 'error' | 'disabled'>,
): void {
    const editableChanged = bridge.disabled !== options.disabled;
    bridge.change = options.change;
    bridge.error = options.error;
    bridge.disabled = options.disabled;
    if (editableChanged) {
        bridge.view?.setProps({editable: () => !bridge.disabled && !bridge.failed});
    }
}

export function failMessageEditBridge(bridge: MessageEditBridge): void {
    if (bridge.failed) {
        return;
    }
    bridge.failed = true;
    bridge.error();
    bridge.view?.setProps({editable: () => false});
}

function markFormat(mark: Mark): string {
    return mark.attrs.kind === 'existing' ? mark.attrs.typeHint : mark.attrs.kind;
}

export function hasMessageEditFormat(state: EditorState, format: MessageEditFormat): boolean {
    let found = false;
    if (state.selection.empty) {
        return (state.storedMarks ?? state.selection.$from.marks()).some(
            (mark) => mark.type.name === entityMarkName && markFormat(mark) === format,
        );
    }
    state.doc.nodesBetween(state.selection.from, state.selection.to, (node) => {
        found ||= node.marks.some((mark) => mark.type.name === entityMarkName && markFormat(mark) === format);
    });
    return found;
}

export function toggleMessageEditFormat(
    bridge: MessageEditBridge,
    format: MessageEditFormat,
    url = '',
): void {
    const view = bridge.view;
    if (!view || bridge.disabled || bridge.failed) {
        return;
    }
    const state = view.state;
    const {from, to, empty} = state.selection;
    const tr = state.tr;
    if (hasMessageEditFormat(state, format)) {
        const matching = new Map<string, Mark>();
        if (empty) {
            (state.storedMarks ?? state.selection.$from.marks()).forEach((mark) => {
                if (mark.type.name === entityMarkName && markFormat(mark) === format) {
                    tr.removeStoredMark(mark);
                }
            });
        } else {
            state.doc.nodesBetween(from, to, (node) => {
                node.marks.forEach((mark) => {
                    if (mark.type.name === entityMarkName && markFormat(mark) === format) {
                        matching.set(mark.attrs.entityId, mark);
                    }
                });
            });
            matching.forEach((mark) => tr.removeMark(from, to, mark));
        }
    } else {
        const attrs: MessageEditMark = {
            entityId: `new-${++bridge.sequence}`,
            kind: format,
            sourceIndex: null,
            typeHint: '',
            url: format === 'texturl' ? url : '',
            language: '',
        };
        const mark = state.schema.marks[entityMarkName].create(attrs);
        if (empty) {
            tr.addStoredMark(mark);
        } else {
            tr.addMark(from, to, mark);
        }
    }
    view.dispatch(tr);
    view.focus();
}

function trustedSlice(slice: Slice, bridge: MessageEditBridge): boolean {
    let valid = true;
    function check(attrs: MessageEditMark) {
        if (
            typeof attrs.entityId !== 'string' ||
            attrs.entityId.length > 100 ||
            typeof attrs.url !== 'string' ||
            typeof attrs.language !== 'string'
        ) {
            valid = false;
            return;
        }
        if (attrs.kind !== 'existing') {
            valid &&= formats.includes(attrs.kind) && attrs.sourceIndex === null;
            return;
        }
        const original = bridge.initialDraft.entities.find(
            (entity) => entity.kind === 'existing' && entity.sourceIndex === attrs.sourceIndex,
        );
        valid &&= Boolean(
            original &&
                original.typeHint === attrs.typeHint &&
                original.url === attrs.url &&
                original.language === attrs.language,
        );
    }
    slice.content.descendants((node) => {
        valid &&= ['paragraph', 'text', softBreakName, atomicEntityName].includes(node.type.name);
        if (node.type.name === atomicEntityName) {
            check(node.attrs as MessageEditMark);
            valid &&=
                node.attrs.kind === 'existing' &&
                ['customemoji', 'mentionname'].includes(node.attrs.typeHint);
        }
        node.marks.forEach((mark) => {
            if (mark.type.name === entityMarkName) {
                check(mark.attrs as MessageEditMark);
            } else {
                valid = false;
            }
        });
    });
    return valid;
}

function copyEntityReferences(slice: Slice, bridge: MessageEditBridge): Slice {
    const ids = new Map<string, string>();
    function copiedAttrs(attrs: MessageEditMark, atomic = false): MessageEditMark {
        const entityId = !atomic ? ids.get(attrs.entityId) : undefined;
        const copied = entityId ?? `paste-${++bridge.sequence}`;
        if (!atomic) {
            ids.set(attrs.entityId, copied);
        }
        return {...attrs, entityId: copied};
    }
    function copy(node: ProseMirrorNode): ProseMirrorNode {
        const marks = node.marks.map((mark) => mark.type.create(copiedAttrs(mark.attrs as MessageEditMark)));
        if (node.isText) {
            return node.mark(marks);
        }
        const children: ProseMirrorNode[] = [];
        node.forEach((child) => children.push(copy(child)));
        const attrs =
            node.type.name === atomicEntityName
                ? copiedAttrs(node.attrs as MessageEditMark, true)
                : node.attrs;
        return node.type.create(attrs, Fragment.from(children), marks);
    }
    const nodes: ProseMirrorNode[] = [];
    slice.content.forEach((node) => nodes.push(copy(node)));
    return new Slice(Fragment.from(nodes), slice.openStart, slice.openEnd);
}

function pastedSlice(
    event: ClipboardEvent,
    view: EditorView,
    bridge: MessageEditBridge,
    clipboardNonce: string,
): Slice {
    const encoded = event.clipboardData?.getData(clipboardType);
    if (encoded) {
        try {
            const fragment: {draftId?: unknown; nonce?: unknown; slice?: unknown} = JSON.parse(encoded);
            if (fragment.draftId === bridge.draftId && fragment.nonce === clipboardNonce) {
                const slice = Slice.fromJSON(view.state.schema, fragment.slice);
                if (trustedSlice(slice, bridge)) {
                    return copyEntityReferences(slice, bridge);
                }
            }
        } catch {
            // External clipboard metadata is untrusted; the plain-text payload remains usable.
        }
    }
    const text = (event.clipboardData?.getData('text/plain') ?? '')
        .split('\r\n')
        .join('\n')
        .split('\r')
        .join('\n');
    const doc = createMessageEditDocument(view.state.schema, {text, entities: []});
    const marks = view.state.storedMarks ?? view.state.selection.$from.marks();
    const nodes: ProseMirrorNode[] = [];
    doc.firstChild?.forEach((node) => nodes.push(node.mark(marks)));
    return new Slice(Fragment.from(nodes), 0, 0);
}

export function createMessageEditExtension(bridge: MessageEditBridge): Extension {
    const initialized = new WeakSet<EditorView>();
    const clipboardNonce = crypto.randomUUID();
    return (builder) => {
        const attrs = {
            entityId: {default: ''},
            kind: {default: 'existing'},
            sourceIndex: {default: null},
            typeHint: {default: ''},
            url: {default: ''},
            language: {default: ''},
        };
        builder.overrideNodeSpec('doc', (previous) => ({...previous, content: 'paragraph'}));
        builder.overrideNodeSpec('paragraph', (previous) => ({...previous, whitespace: 'pre'}));
        builder.addMarkSpec(entityMarkName, () => ({
            attrs,
            excludes: '',
            inclusive: true,
            toDOM: (mark) => {
                const format = markFormat(mark);
                return [
                    'span',
                    {
                        class: `${styles.mark} ${formats.includes(format as MessageEditFormat) ? styles[format] : ''}`,
                    },
                    0,
                ];
            },
        }));
        builder.addMarkdownTokenParserSpec(entityMarkName, () => ({
            name: entityMarkName,
            type: 'mark',
            ignore: true,
        }));
        builder.addMarkSerializerSpec(entityMarkName, () => ({open: '', close: '', mixable: true}));
        builder.addNodeSpec(softBreakName, () => ({
            inline: true,
            group: 'inline',
            selectable: false,
            linebreakReplacement: true,
            toDOM: () => ['br'],
            parseDOM: [{tag: 'br'}],
        }));
        builder.addMarkdownTokenParserSpec(softBreakName, () => ({
            name: softBreakName,
            type: 'node',
            ignore: true,
        }));
        builder.addNodeSerializerSpec(softBreakName, () => (state) => state.write('\n'));
        builder.addNodeSpec(atomicEntityName, () => ({
            attrs,
            inline: true,
            group: 'inline',
            content: 'inline*',
            atom: true,
            selectable: true,
            toDOM: () => ['span', {class: styles.atom, contenteditable: 'false'}, 0],
        }));
        builder.addMarkdownTokenParserSpec(atomicEntityName, () => ({
            name: atomicEntityName,
            type: 'node',
            ignore: true,
        }));
        builder.addNodeSerializerSpec(atomicEntityName, () => (state, node) => state.renderInline(node));
        builder.addPlugin(
            () =>
                new Plugin({
                    props: {
                        editable: () => !bridge.disabled && !bridge.failed,
                        attributes: {
                            role: 'textbox',
                            'aria-label': bridge.ariaLabel,
                            'aria-multiline': 'true',
                        },
                        handleKeyDown(view, event) {
                            if (event.isComposing || bridge.disabled || bridge.failed) {
                                return false;
                            }
                            if (event.key === 'Enter' && !event.ctrlKey && !event.metaKey && !event.altKey) {
                                event.preventDefault();
                                const marks = view.state.storedMarks ?? view.state.selection.$from.marks();
                                view.dispatch(
                                    view.state.tr
                                        .replaceSelectionWith(
                                            view.state.schema.nodes[softBreakName].create(null, null, marks),
                                            false,
                                        )
                                        .scrollIntoView(),
                                );
                                return true;
                            }
                            const hotkeys: Record<string, MessageEditFormat> = {
                                b: 'bold',
                                i: 'italic',
                                u: 'underline',
                            };
                            const format = hotkeys[event.key.toLowerCase()];
                            if ((event.ctrlKey || event.metaKey) && !event.altKey && format) {
                                event.preventDefault();
                                toggleMessageEditFormat(bridge, format);
                                return true;
                            }
                            return false;
                        },
                        handleDOMEvents: {
                            copy(view, event) {
                                if (!event.clipboardData || view.state.selection.empty) {
                                    return false;
                                }
                                event.preventDefault();
                                const slice = view.state.selection.content();
                                event.clipboardData.setData(
                                    'text/plain',
                                    slice.content.textBetween(0, slice.content.size, '', '\n'),
                                );
                                event.clipboardData.setData(
                                    clipboardType,
                                    JSON.stringify({
                                        draftId: bridge.draftId,
                                        nonce: clipboardNonce,
                                        slice: slice.toJSON(),
                                    }),
                                );
                                return true;
                            },
                            cut(view, event) {
                                if (
                                    !event.clipboardData ||
                                    view.state.selection.empty ||
                                    bridge.disabled ||
                                    bridge.failed
                                ) {
                                    return false;
                                }
                                event.preventDefault();
                                const slice = view.state.selection.content();
                                event.clipboardData.setData(
                                    'text/plain',
                                    slice.content.textBetween(0, slice.content.size, '', '\n'),
                                );
                                event.clipboardData.setData(
                                    clipboardType,
                                    JSON.stringify({
                                        draftId: bridge.draftId,
                                        nonce: clipboardNonce,
                                        slice: slice.toJSON(),
                                    }),
                                );
                                view.dispatch(
                                    closeHistory(
                                        view.state.tr
                                            .deleteSelection()
                                            .scrollIntoView()
                                            .setMeta('uiEvent', 'cut'),
                                    ),
                                );
                                return true;
                            },
                            paste(view, event) {
                                if (!event.clipboardData || bridge.disabled || bridge.failed) {
                                    return false;
                                }
                                event.preventDefault();
                                view.dispatch(
                                    closeHistory(
                                        view.state.tr
                                            .replaceSelection(
                                                pastedSlice(event, view, bridge, clipboardNonce),
                                            )
                                            .scrollIntoView()
                                            .setMeta('uiEvent', 'paste'),
                                    ),
                                );
                                return true;
                            },
                            drop(_view, event) {
                                event.preventDefault();
                                return true;
                            },
                        },
                    },
                    view(view) {
                        bridge.view = view;
                        if (!initialized.has(view)) {
                            initialized.add(view);
                            queueMicrotask(() => {
                                if (view.isDestroyed || bridge.view !== view) {
                                    return;
                                }
                                try {
                                    view.updateState(
                                        view.state.reconfigure({
                                            plugins: view.state.plugins.filter(
                                                (plugin) => !plugin.spec.isInputRules,
                                            ),
                                        }),
                                    );
                                    const doc = createMessageEditDocument(
                                        view.state.schema,
                                        bridge.initialDraft,
                                    );
                                    const tr = view.state.tr
                                        .replaceWith(0, view.state.doc.content.size, doc.content)
                                        .setMeta('addToHistory', false);
                                    view.dispatch(tr.setSelection(TextSelection.atEnd(tr.doc)));
                                    bridge.change(readMessageEditDocument(view.state.doc));
                                    if (bridge.autoFocus) {
                                        view.focus();
                                    }
                                } catch {
                                    failMessageEditBridge(bridge);
                                }
                            });
                        }
                        return {
                            update(current, previous) {
                                if (!current.state.doc.eq(previous.doc)) {
                                    try {
                                        bridge.change(readMessageEditDocument(current.state.doc));
                                    } catch {
                                        failMessageEditBridge(bridge);
                                    }
                                }
                                bridge.selectionChange();
                            },
                            destroy() {
                                if (bridge.view === view) {
                                    bridge.view = null;
                                }
                            },
                        };
                    },
                }),
            builder.Priority.Highest,
        );
    };
}
