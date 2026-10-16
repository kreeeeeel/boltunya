import {Box, Flex, Icon, Text, Tooltip} from '@gravity-ui/uikit';
import {Check, CheckDouble, Sparkles, TrashBin} from '@gravity-ui/icons';
import {useLocale, useTranslations} from 'next-intl';
import type {Message} from '@/shared/api/client';
import {isEmojiMessage} from '@/modules/workspace/model/messagePresentation';
import {MessageMedia} from '@/modules/workspace/MessageMedia/MessageMedia';
import {RichText} from '@/modules/workspace/RichText/RichText';
import styles from '@/modules/workspace/Conversation/Conversation.module.css';
const GROUP_INTERVAL_SECONDS = 300;
export function MessageBubble({
    message,
    nextMessage,
    replyText,
}: {
    readonly message: Message;
    readonly nextMessage?: Message;
    readonly replyText?: string;
}) {
    const t = useTranslations();
    const locale = useLocale();
    return (
        <Box
            qa={message.outgoing ? 'message-outgoing' : 'message-incoming'}
            data-message-id={message.id}
            data-message-kind={message.kind}
            data-message-deleted={message.deleted || undefined}
            className={`${styles.bubble} ${message.outgoing ? styles.outgoing : styles.incoming} ${message.kind === 'sticker' || isEmojiMessage(message.text) ? styles.stickerBubble : ''} ${isEmojiMessage(message.text) ? styles.emojiBubble : ''} ${message.media ? styles.mediaBubble : ''} ${nextMessage?.outgoing === message.outgoing && nextMessage.date - message.date < GROUP_INTERVAL_SECONDS ? styles.grouped : styles.tail}`}
        >
            {message.group && !message.outgoing && (
                <Text className={styles.sender}>{message.senderName}</Text>
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
}
