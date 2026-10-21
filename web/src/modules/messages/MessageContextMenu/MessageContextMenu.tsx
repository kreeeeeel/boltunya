import {Box, Dialog, DropdownMenu, Icon, Portal, Text} from '@gravity-ui/uikit';
import {Copy, Link, EyeSlash, ArrowRotateLeft, Sparkles, TrashBin} from '@gravity-ui/icons';
import {useState} from 'react';
import {useTranslations} from 'next-intl';
import {api, dataOrThrow, errorMessage} from '@/shared/api/client';
import type {Message} from '@/shared/api/client';
import styles from '@/modules/messages/MessageContextMenu/MessageContextMenu.module.css';
import type {ContextExclusionChange} from '@/modules/messages/model/ContextExclusionChange';
import {MessageDeletionDialog} from '@/modules/messages/MessageDeletionDialog/MessageDeletionDialog';
import type {MessageDeletionResult} from '@/modules/messages/MessageDeletionDialog/MessageDeletionDialog.types';
export interface MessageContextMenuProps {
    readonly message: Message;
    readonly position: {x: number; y: number} | null;
    readonly onClose: () => void;
    readonly onExcludedChanged: (change: ContextExclusionChange) => Promise<void>;
    readonly onDraftRequest?: () => Promise<void>;
    readonly messageLink?: string;
    readonly onDeleted?: (result: MessageDeletionResult) => Promise<void>;
    readonly onDialogOpenChange?: (open: boolean) => void;
}
export function MessageContextMenu({
    message,
    position,
    onClose,
    onExcludedChanged,
    onDraftRequest,
    messageLink,
    onDeleted,
    onDialogOpenChange,
}: MessageContextMenuProps) {
    const t = useTranslations('messageMenu');
    const common = useTranslations();
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [deleting, setDeleting] = useState(false);
    async function action(callback: () => Promise<void>, change?: ContextExclusionChange) {
        if (busy) {
            return;
        }
        setBusy(true);
        setError('');
        try {
            await callback();
            onClose();
            if (change) {
                try {
                    await onExcludedChanged(change);
                } catch {
                    setError(t('refreshFailed'));
                }
            }
        } catch (cause) {
            setError(errorMessage(cause));
            onClose();
        } finally {
            setBusy(false);
        }
    }
    async function copyLink() {
        const url =
            messageLink ??
            dataOrThrow(
                await api.GET('/api/chats/{chatId}/messages/{messageId}/link', {
                    params: {path: {chatId: message.chatId, messageId: message.id}},
                }),
            ).url;
        if (!url) {
            throw new Error(t('linkUnavailable'));
        }
        await navigator.clipboard.writeText(url);
    }
    function exclusion(scope: 'message' | 'through' | 'from', excluded: boolean) {
        void action(
            async () => {
                dataOrThrow(
                    await api.PUT('/api/chats/{chatId}/messages/{messageId}/context', {
                        params: {path: {chatId: message.chatId, messageId: message.id}},
                        body: {scope, excluded},
                    }),
                );
            },
            {messageId: message.id, scope, excluded},
        );
    }
    if (!position && !error && !deleting) {
        return null;
    }
    return (
        <>
            <Portal>
                <Box className={styles.anchor} style={{left: position?.x ?? 0, top: position?.y ?? 0}}>
                    <DropdownMenu
                        open={position !== null}
                        hideOnScroll={false}
                        onOpenToggle={(open: boolean) => {
                            if (!open && !deleting) {
                                onClose();
                            }
                        }}
                        size="m"
                        switcherWrapperClassName={styles.target}
                        renderSwitcher={() => <Box aria-hidden />}
                        popupProps={{
                            strategy: 'fixed',
                            offset: 8,
                            placement: ['bottom-start', 'top-start'],
                            returnFocus: false,
                            initialFocus: -1,
                            className: styles.popup,
                            qa: 'message-context-menu',
                        }}
                        items={[
                            [
                                {
                                    text: t('copyText'),
                                    iconStart: <Icon data={Copy} size={17} />,
                                    disabled: busy || !message.text,
                                    action: () =>
                                        void action(() => navigator.clipboard.writeText(message.text)),
                                },
                                {
                                    text: t('copyLink'),
                                    iconStart: <Icon data={Link} size={17} />,
                                    disabled: busy,
                                    action: () => void action(copyLink),
                                },
                                ...(onDraftRequest
                                    ? [
                                          {
                                              text: t('draft'),
                                              iconStart: <Icon data={Sparkles} size={17} />,
                                              disabled: busy,
                                              action: () => void action(onDraftRequest),
                                          },
                                      ]
                                    : []),
                            ],
                            [
                                {
                                    text: t('exclude'),
                                    iconStart: <Icon data={EyeSlash} size={17} />,
                                    disabled: busy,
                                    items: ['message', 'through', 'from'].map((scope) => ({
                                        text: t('scopes.' + scope),
                                        action: () =>
                                            exclusion(scope as 'message' | 'through' | 'from', true),
                                    })),
                                },
                                {
                                    text: t('restore'),
                                    iconStart: <Icon data={ArrowRotateLeft} size={17} />,
                                    disabled: busy,
                                    items: [
                                        ...['message', 'through', 'from'].map((scope) => ({
                                            text: t('scopes.' + scope),
                                            action: () =>
                                                exclusion(scope as 'message' | 'through' | 'from', false),
                                        })),
                                        {
                                            text: t('restoreAll'),
                                            action: () =>
                                                void action(
                                                    async () => {
                                                        dataOrThrow(
                                                            await api.DELETE(
                                                                '/api/chats/{chatId}/context-exclusions',
                                                                {
                                                                    params: {path: {chatId: message.chatId}},
                                                                },
                                                            ),
                                                        );
                                                    },
                                                    {reset: true},
                                                ),
                                        },
                                    ],
                                },
                            ],
                            ...(!message.deleted
                                ? [
                                      [
                                          {
                                              text: t('delete'),
                                              theme: 'danger' as const,
                                              iconStart: <Icon data={TrashBin} size={17} />,
                                              disabled: busy,
                                              action: () => {
                                                  onClose();
                                                  setDeleting(true);
                                                  onDialogOpenChange?.(true);
                                              },
                                          },
                                      ],
                                  ]
                                : []),
                            ...(busy ? [[{text: t('saving'), disabled: true, action: () => {}}]] : []),
                        ]}
                    />
                </Box>
            </Portal>
            {deleting && (
                <MessageDeletionDialog
                    chatId={message.chatId}
                    messageId={message.id}
                    onDeleted={onDeleted}
                    onClose={() => {
                        setDeleting(false);
                        onDialogOpenChange?.(false);
                    }}
                />
            )}
            {error && (
                <Dialog open onClose={() => setError('')} maxWidth="s" aria-label={t('failed')}>
                    <Dialog.Header caption={t('failed')} />
                    <Dialog.Body>
                        <Text role="alert">{error}</Text>
                    </Dialog.Body>
                    <Dialog.Footer
                        textButtonCancel={common('close')}
                        onClickButtonCancel={() => setError('')}
                    />
                </Dialog>
            )}
        </>
    );
}
