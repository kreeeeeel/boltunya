import {Box, Flex, Icon, Text, Tooltip} from '@gravity-ui/uikit';
import {Check, CheckDouble, Sparkles, TrashBin} from '@gravity-ui/icons';
import {useLocale, useTranslations} from 'next-intl';
import type {Message} from '@/shared/api/client';
import {isEmojiMessage} from '@/modules/workspace/model/messagePresentation';
import {MessageMedia} from '@/modules/workspace/MessageMedia/MessageMedia';
import {RichText} from '@/modules/workspace/RichText/RichText';
import styles from '@/modules/workspace/Conversation/Conversation.module.css';
import {TelegramAvatar} from '@/shared/ui/TelegramAvatar/TelegramAvatar';
import {messagesGrouped} from '@/modules/workspace/model/messagesGrouped';
export function MessageBubble({
    message,
    nextMessage,
    previousMessage,
    group = message.group,
    replyText,
}: {
    readonly message: Message;
    readonly nextMessage?: Message;
    readonly previousMessage?: Message;
    readonly group?: boolean;
    readonly replyText?: string;
}) {
    const t = useTranslations();
    const locale = useLocale();
    const continues = messagesGrouped(message, nextMessage, group);
    const groupIncoming = group && !message.outgoing;
    const bubble = (
        <Box
            qa={message.outgoing ? 'message-outgoing' : 'message-incoming'}
            data-message-id={message.id}
            data-message-kind={message.kind}
            data-message-deleted={message.deleted || undefined}
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
                {message.outgoing && <Icon data={message.read ? CheckDouble : Check} size={16} />}
            </Flex>
        </Box>
    );
    if (!groupIncoming) {
        return bubble;
    }
    return (
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
}
