import {Box, Dialog, DropdownMenu, Icon, Text} from '@gravity-ui/uikit';
import {Copy, Link, EyeSlash, ArrowRotateLeft, Sparkles} from '@gravity-ui/icons';
import {useState} from 'react';
import {useTranslations} from 'next-intl';
import {api, dataOrThrow, errorMessage} from '@/shared/api/client';
import type {Message} from '@/shared/api/client';
import styles from '@/modules/messages/MessageContextMenu/MessageContextMenu.module.css';
import type {ContextExclusionChange} from '@/modules/messages/model/ContextExclusionChange';
export interface MessageContextMenuProps {
    readonly message: Message;
    readonly position: {x: number; y: number} | null;
    readonly onClose: () => void;
    readonly onExcludedChanged: (change: ContextExclusionChange) => Promise<void>;
    readonly onDraftRequest?: () => Promise<void>;
    readonly messageLink?: string;
}
export function MessageContextMenu({
    message,
    position,
    onClose,
    onExcludedChanged,
    onDraftRequest,
    messageLink,
}: MessageContextMenuProps) {
    const t = useTranslations('messageMenu');
    const common = useTranslations();
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
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
    const anchor = position
        ? {getBoundingClientRect: () => DOMRect.fromRect({x: position.x, y: position.y, width: 0, height: 0})}
        : null;
    if (!position && !error) {
        return null;
    }
    return (
        <>
            <DropdownMenu
                open={position !== null}
                hideOnScroll={false}
                onOpenToggle={(open: boolean) => {
                    if (!open) {
                        onClose();
                    }
                }}
                size="m"
                switcherWrapperClassName={styles.hidden}
                renderSwitcher={() => <Box aria-hidden />}
                popupProps={{
                    anchorElement: anchor,
                    strategy: 'fixed',
                    placement: ['bottom-start', 'top-start'],
                    returnFocus: false,
                    qa: 'message-context-menu',
                }}
                items={[
                    [
                        {
                            text: t('copyText'),
                            iconStart: <Icon data={Copy} size={17} />,
                            disabled: busy || !message.text,
                            action: () => void action(() => navigator.clipboard.writeText(message.text)),
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
                                action: () => exclusion(scope as 'message' | 'through' | 'from', true),
                            })),
                        },
                        {
                            text: t('restore'),
                            iconStart: <Icon data={ArrowRotateLeft} size={17} />,
                            disabled: busy,
                            items: [
                                ...['message', 'through', 'from'].map((scope) => ({
                                    text: t('scopes.' + scope),
                                    action: () => exclusion(scope as 'message' | 'through' | 'from', false),
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
                    ...(busy ? [[{text: t('saving'), disabled: true, action: () => {}}]] : []),
                ]}
            />
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
