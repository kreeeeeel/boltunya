import {Box, Flex, Icon, Text, Tooltip} from '@gravity-ui/uikit';
import {Check, CheckDouble, Sparkles, TrashBin, EyeSlash} from '@gravity-ui/icons';
import {useEffect, useRef, useState} from 'react';
import {useLocale, useTranslations} from 'next-intl';
import type {Message} from '@/shared/api/client';
import {isEmojiMessage} from '@/modules/workspace/model/messagePresentation';
import {MessageMedia} from '@/modules/workspace/MessageMedia/MessageMedia';
import {RichText} from '@/modules/workspace/RichText/RichText';
import styles from '@/modules/workspace/Conversation/Conversation.module.css';
import {TelegramAvatar} from '@/shared/ui/TelegramAvatar/TelegramAvatar';
import {messagesGrouped} from '@/modules/workspace/model/messagesGrouped';
import {MessageContextMenu} from '@/modules/messages/MessageContextMenu/MessageContextMenu';
import type {ContextExclusionChange} from '@/modules/messages/model/ContextExclusionChange';
import type {MessageDeletionResult} from '@/modules/messages/MessageDeletionDialog/MessageDeletionDialog.types';
export function MessageBubble({
    message,
    nextMessage,
    previousMessage,
    group = message.group,
    replyText,
    onContextChanged,
    onDraftRequest,
    onMenuOpenChange,
    onDeleted,
    onEditRequest,
}: {
    readonly message: Message;
    readonly nextMessage?: Message;
    readonly previousMessage?: Message;
    readonly group?: boolean;
    readonly replyText?: string;
    readonly onContextChanged?: (change: ContextExclusionChange) => Promise<void>;
    readonly onDraftRequest?: () => Promise<void>;
    readonly onMenuOpenChange?: (messageId: string, open: boolean) => void;
    readonly onDeleted?: (result: MessageDeletionResult) => Promise<void>;
    readonly onEditRequest?: (message: Message) => void;
}) {
    const t = useTranslations();
    const chatUi = useTranslations('chatUi');
    const locale = useLocale();
    const [menu, setMenu] = useState<{x: number; y: number} | null>(null);
    const [menuInitialized, setMenuInitialized] = useState(false);
    const row = useRef<HTMLDivElement>(null);
    useEffect(() => () => onMenuOpenChange?.(message.id, false), [message.id, onMenuOpenChange]);
    function openMenu(x: number, y: number) {
        setMenuInitialized(true);
        setMenu({x, y});
        onMenuOpenChange?.(message.id, true);
    }
    function closeMenu() {
        setMenu(null);
        onMenuOpenChange?.(message.id, false);
    }
    const continues = messagesGrouped(message, nextMessage, group);
    const groupIncoming = group && !message.outgoing;
    const bubble = (
        <Box
            qa={message.outgoing ? 'message-outgoing' : 'message-incoming'}
            data-message-id={message.id}
            data-message-kind={message.kind}
            data-message-deleted={message.deleted || undefined}
            data-message-context-excluded={message.contextExcluded || undefined}
            className={`${styles.bubble} ${message.outgoing ? styles.outgoing : styles.incoming} ${message.kind === 'sticker' || isEmojiMessage(message.text) ? styles.stickerBubble : ''} ${isEmojiMessage(message.text) ? styles.emojiBubble : ''} ${message.media ? styles.mediaBubble : ''} ${continues ? styles.grouped : styles.tail}`}
        >
            {groupIncoming && !messagesGrouped(message, previousMessage, group) && (
                <Text className={styles.sender} qa="message-sender-name">
                    {message.senderName}
                </Text>
            )}
            {message.replyToId && (
                <Box className={styles.replyQuote}>
                    <Text>{message.replyText || replyText || t('replyMessage')}</Text>
                </Box>
            )}
            <MessageMedia
                key={`${message.media?.fileId}:${message.media?.cacheVersion ?? 0}`}
                message={message}
            />
            {message.text && (
                <RichText
                    key={message.text}
                    text={message.text}
                    entities={message.entities}
                    className={styles.messageText}
                />
            )}
            <Flex justifyContent="flex-end" alignItems="center" gap={1} className={styles.messageMeta}>
                {message.deleted && (
                    <Tooltip
                        content={t('deleted.at', {
                            date: new Date((message.deletedAt ?? 0) * 1000).toLocaleString(locale),
                        })}
                    >
                        <Text className={styles.deletedBadge} qa="message-deleted">
                            <Icon data={TrashBin} size={12} />
                            {t('deleted.badge')}
                        </Text>
                    </Tooltip>
                )}
                {message.contextExcluded && (
                    <Tooltip content={t('messageMenu.excluded')}>
                        <Text
                            className={`${styles.aiBadge} ${styles.contextExcluded}`}
                            qa="message-context-excluded"
                            aria-label={t('messageMenu.excluded')}
                        >
                            <Icon data={EyeSlash} size={12} />
                            {t('messageMenu.excludedShort')}
                        </Text>
                    </Tooltip>
                )}
                {message.automated && (
                    <Tooltip content={t('aiMessage')}>
                        <Text className={styles.aiBadge} qa="message-ai" aria-label={t('aiMessage')}>
                            <Icon data={Sparkles} size={12} />
                            {t('aiShort')}
                        </Text>
                    </Tooltip>
                )}
                <Text>
                    {new Date(message.date * 1000).toLocaleTimeString(locale, {
                        hour: '2-digit',
                        minute: '2-digit',
                    })}
                </Text>
                {!!message.editedAt && (
                    <Tooltip
                        content={t('messageEdit.editedAt', {
                            date: new Date(message.editedAt * 1000).toLocaleString(locale),
                        })}
                    >
                        <Text qa="message-edited">{t('messageEdit.edited')}</Text>
                    </Tooltip>
                )}
                {message.outgoing && <Icon data={message.read ? CheckDouble : Check} size={16} />}
            </Flex>
        </Box>
    );
    const content = !groupIncoming ? (
        bubble
    ) : (
        <Flex className={styles.groupMessage} alignItems="flex-end" gap={2} qa="group-message">
            <Box className={styles.senderAvatar}>
                {!continues && (
                    <TelegramAvatar
                        qa="message-sender-avatar"
                        title={message.senderName || '?'}
                        url={message.senderAvatarUrl}
                        size="m"
                    />
                )}
            </Box>
            {bubble}
        </Flex>
    );
    return (
        <>
            <Box
                ref={row}
                qa="message-row"
                data-message-row-id={message.id}
                className={`${styles.messageRow} ${message.outgoing ? styles.messageRowOutgoing : ''}`}
                role="group"
                aria-label={chatUi('messageActions')}
                tabIndex={0}
                onMouseDown={(event) => {
                    if (event.button === 2) {
                        event.preventDefault();
                    }
                }}
                onContextMenu={(event) => {
                    event.preventDefault();
                    event.stopPropagation();
                    openMenu(event.clientX, event.clientY);
                }}
                onKeyDown={(event) => {
                    if (event.key === 'ContextMenu' || (event.shiftKey && event.key === 'F10')) {
                        event.preventDefault();
                        const bounds = row.current?.getBoundingClientRect();
                        if (bounds) {
                            openMenu(bounds.left + Math.min(160, bounds.width / 2), bounds.top + 24);
                        }
                    }
                }}
            >
                {content}
            </Box>
            {menuInitialized && (
                <MessageContextMenu
                    message={message}
                    position={menu}
                    onClose={closeMenu}
                    onExcludedChanged={onContextChanged ?? (async () => {})}
                    onDraftRequest={onDraftRequest}
                    onDeleted={onDeleted}
                    onEditRequest={onEditRequest}
                    onDialogOpenChange={(open) => onMenuOpenChange?.(message.id, open)}
                />
            )}
        </>
    );
}
