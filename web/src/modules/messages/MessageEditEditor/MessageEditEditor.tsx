'use client';

import {MarkdownEditorView, useMarkdownEditor} from '@gravity-ui/markdown-editor';
import {redo, undo} from '@gravity-ui/markdown-editor/pm/history';
import {
    Bold,
    Italic,
    Underline,
    Strikethrough,
    Code,
    EyeSlash,
    Link,
    QuoteOpen,
    ArrowUturnCcwLeft,
    ArrowUturnCwRight,
} from '@gravity-ui/icons';
import {Box, Button, Flex, Icon, TextInput, Toaster, ToasterProvider} from '@gravity-ui/uikit';
import {useCallback, useEffect, useState} from 'react';
import {useTranslations} from 'next-intl';
import {
    createMessageEditExtension,
    configureMessageEditBridge,
    failMessageEditBridge,
    hasMessageEditFormat,
    toggleMessageEditFormat,
    type MessageEditBridge,
} from '@/modules/messages/MessageEditEditor/messageEditExtension';
import type {
    MessageEditEditorProps,
    MessageEditFormat,
} from '@/modules/messages/MessageEditEditor/MessageEditEditor.types';
import styles from '@/modules/messages/MessageEditEditor/MessageEditEditor.module.css';

const tools = [
    {format: 'bold', icon: Bold},
    {format: 'italic', icon: Italic},
    {format: 'underline', icon: Underline},
    {format: 'strike', icon: Strikethrough},
    {format: 'code', icon: Code},
    {format: 'spoiler', icon: EyeSlash},
    {format: 'texturl', icon: Link},
    {format: 'quote', icon: QuoteOpen},
] as const;

export function MessageEditEditor({
    initialDraft,
    draftId,
    disabled = false,
    autoFocus = false,
    ariaLabel,
    onChange,
    onError,
}: MessageEditEditorProps) {
    const t = useTranslations('messageEditFormat');
    const [, refreshSelection] = useState(0);
    const [linkOpen, setLinkOpen] = useState(false);
    const [url, setUrl] = useState('');
    const [failed, setFailed] = useState(false);
    const [toaster] = useState(() => new Toaster());
    const reportFailure = useCallback(() => {
        setFailed(true);
        onError(t('error'));
    }, [onError, t]);
    const [bridge] = useState<MessageEditBridge>(() => ({
        view: null,
        draftId,
        initialDraft,
        disabled,
        failed: false,
        ariaLabel,
        autoFocus,
        sequence: 0,
        change: onChange,
        selectionChange: () => refreshSelection((value) => value + 1),
        error: reportFailure,
    }));
    const editor = useMarkdownEditor(
        {
            preset: 'zero',
            initial: {markup: '', mode: 'wysiwyg', toolbarVisible: false},
            md: {html: false, linkify: false},
            wysiwygConfig: {
                extensions: createMessageEditExtension(bridge),
                searchPanel: false,
                placeholderOptions: {value: ''},
            },
            experimental: {
                beforeEditorModeChange: ({mode, reason}) => {
                    if (reason === 'error-boundary') {
                        queueMicrotask(() => failMessageEditBridge(bridge));
                    }
                    return mode === 'wysiwyg';
                },
            },
        },
        [bridge],
    );
    useEffect(() => {
        configureMessageEditBridge(bridge, {change: onChange, error: reportFailure, disabled});
    }, [bridge, disabled, onChange, reportFailure]);
    useEffect(() => () => toaster.destroy(), [toaster]);
    function format(kind: MessageEditFormat) {
        if (kind === 'texturl' && bridge.view && !hasMessageEditFormat(bridge.view.state, kind)) {
            setLinkOpen(true);
        } else {
            toggleMessageEditFormat(bridge, kind);
        }
    }
    let validUrl = false;
    try {
        validUrl = ['http:', 'https:', 'mailto:', 'tg:'].includes(new URL(url).protocol);
    } catch {
        // An unfinished URL is expected while typing; no editor failure has occurred.
    }
    const view = bridge.view;
    return (
        <Box className={styles.root} qa="message-edit-editor">
            <Flex
                className={styles.toolbar}
                gap={1}
                alignItems="center"
                wrap
                role="toolbar"
                aria-label={ariaLabel}
            >
                {tools.map(({format: kind, icon}) => (
                    <Button
                        key={kind}
                        size="m"
                        view="flat"
                        title={t(kind === 'texturl' ? 'link' : kind)}
                        aria-label={t(kind === 'texturl' ? 'link' : kind)}
                        selected={Boolean(view && hasMessageEditFormat(view.state, kind))}
                        disabled={disabled || bridge.failed || !view}
                        onMouseDown={(event) => event.preventDefault()}
                        onClick={() => format(kind)}
                    >
                        <Icon data={icon} size={16} />
                    </Button>
                ))}
                <Box className={styles.separator} />
                <Button
                    size="m"
                    view="flat"
                    aria-label={t('undo')}
                    title={t('undo')}
                    disabled={disabled || bridge.failed || !view || !undo(view.state)}
                    onMouseDown={(event) => event.preventDefault()}
                    onClick={() => {
                        if (view) {
                            undo(view.state, view.dispatch);
                            view.focus();
                        }
                    }}
                >
                    <Icon data={ArrowUturnCcwLeft} size={16} />
                </Button>
                <Button
                    size="m"
                    view="flat"
                    aria-label={t('redo')}
                    title={t('redo')}
                    disabled={disabled || bridge.failed || !view || !redo(view.state)}
                    onMouseDown={(event) => event.preventDefault()}
                    onClick={() => {
                        if (view) {
                            redo(view.state, view.dispatch);
                            view.focus();
                        }
                    }}
                >
                    <Icon data={ArrowUturnCwRight} size={16} />
                </Button>
            </Flex>
            {linkOpen && (
                <Flex gap={2} className={styles.linkForm} wrap>
                    <TextInput
                        value={url}
                        onUpdate={setUrl}
                        size="m"
                        controlProps={{'aria-label': t('linkUrl')}}
                        className={styles.url}
                    />
                    <Button
                        size="m"
                        view="action"
                        disabled={!validUrl || disabled}
                        onClick={() => {
                            toggleMessageEditFormat(bridge, 'texturl', url);
                            setLinkOpen(false);
                            setUrl('');
                        }}
                    >
                        {t('linkApply')}
                    </Button>
                    <Button
                        size="m"
                        onClick={() => {
                            setLinkOpen(false);
                            setUrl('');
                            view?.focus();
                        }}
                    >
                        {t('linkCancel')}
                    </Button>
                </Flex>
            )}
            {!failed && (
                <ToasterProvider toaster={toaster}>
                    <MarkdownEditorView
                        editor={editor}
                        settingsVisible={false}
                        stickyToolbar={false}
                        qa="message-edit-surface"
                    />
                </ToasterProvider>
            )}
        </Box>
    );
}
