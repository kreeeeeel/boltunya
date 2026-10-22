import {Flex, Text} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import type {Chat} from '@/shared/api/client';
import {TelegramAvatar} from '@/shared/ui/TelegramAvatar/TelegramAvatar';
import styles from '@/modules/workspace/ChatHeading/ChatHeading.module.css';
import {TypingIndicator} from '@/modules/workspace/TypingIndicator/TypingIndicator';

export function ChatHeading({chat}: {readonly chat: Chat}) {
    const t = useTranslations();
    const group = chat.kind === 'group' || chat.kind === 'supergroup';
    const subtitle = group
        ? chat.memberCount == null
            ? t('chatGroup')
            : t('chatMembers', {count: chat.memberCount})
        : '';
    return (
        <Flex alignItems="center" gap={3} className={styles.root}>
            <TelegramAvatar title={chat.title} url={chat.avatarUrl} size="m" />
            <Flex direction="column" className={styles.text}>
                <Text className={styles.title}>{chat.title}</Text>
                {chat.typingUsers?.length ? (
                    <TypingIndicator users={chat.typingUsers} group={group} live />
                ) : (
                    subtitle && (
                        <Text className={styles.subtitle} qa="chat-subtitle">
                            {subtitle}
                        </Text>
                    )
                )}
            </Flex>
        </Flex>
    );
}
