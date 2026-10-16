import {Box, Button, Flex, Icon, Text} from '@gravity-ui/uikit';
import {Sparkles} from '@gravity-ui/icons';
import {useTranslations} from 'next-intl';

import type {Chat} from '@/shared/api/client';
import {TelegramAvatar} from '@/shared/ui/TelegramAvatar/TelegramAvatar';
import styles from '@/modules/workspace/ChatList/ChatList.module.css';

export function ChatListRow({
    chat,
    selected,
    timeFormat,
    onSelect,
}: {
    readonly chat: Chat;
    readonly selected: boolean;
    readonly timeFormat: Intl.DateTimeFormat;
    readonly onSelect: (id: string) => void;
}) {
    const t = useTranslations();
    const group = chat.kind === 'group' || chat.kind === 'supergroup';
    const author = chat.lastMessageOutgoing ? t('chatPreviewYou') : group ? chat.lastMessageSender : '';

    return (
        <Box qa="chat-row" className={[styles.row, selected ? styles.selected : ''].join(' ')}>
            <Button
                qa="chat-open"
                view="flat"
                className={styles.hitArea}
                aria-label={chat.title}
                aria-pressed={selected}
                onClick={() => onSelect(chat.id)}
            />
            <TelegramAvatar
                qa="chat-avatar"
                title={chat.title}
                url={chat.avatarUrl}
                size="l"
                className={[
                    styles.avatar,
                    styles['avatar' + ((chat.id.charCodeAt(chat.id.length - 1) || 0) % 6)],
                ].join(' ')}
            />
            <Flex direction="column" className={styles.chatText}>
                <Text qa="chat-name" className={styles.name}>
                    {chat.title}
                </Text>
                <Text
                    qa="chat-preview"
                    className={[styles.preview, chat.unread > 0 ? styles.unreadPreview : ''].join(' ')}
                >
                    {author && chat.preview ? `${author}: ` : ''}
                    {chat.preview}
                </Text>
                <Flex
                    alignItems="center"
                    className={[styles.meta, chat.rule.enabled ? styles.aiMeta : ''].join(' ')}
                >
                    {chat.rule.enabled && <Icon data={Sparkles} size={13} />}
                    <Text>{t(chat.rule.enabled ? chat.rule.style : 'manual')}</Text>
                </Flex>
            </Flex>
            <Flex direction="column" alignItems="flex-end" className={styles.aside}>
                <Text className={styles.time}>
                    {chat.date > 0 ? timeFormat.format(chat.date * 1000) : ''}
                </Text>
                <Flex alignItems="center" gap={2} className={styles.controls}>
                    {chat.unread > 0 && <Text className={styles.unread}>{chat.unread}</Text>}
                </Flex>
            </Flex>
        </Box>
    );
}
