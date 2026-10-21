import {Button, Flex, Icon, Text, Tooltip} from '@gravity-ui/uikit';
import {Check, Copy, Link} from '@gravity-ui/icons';
import {useState} from 'react';
import {useTranslations} from 'next-intl';
import type {Chat} from '@/shared/api/client';
import {TelegramAvatar} from '@/shared/ui/TelegramAvatar/TelegramAvatar';
import styles from '@/modules/workspace/ChatHeading/ChatHeading.module.css';
import {TypingIndicator} from '@/modules/workspace/TypingIndicator/TypingIndicator';

export function ChatHeading({chat}: {readonly chat: Chat}) {
    const t = useTranslations();
    const [copied, setCopied] = useState('');
    const [error, setError] = useState(false);
    const group = chat.kind === 'group' || chat.kind === 'supergroup';
    const subtitle = group
        ? chat.memberCount == null
            ? t('chatGroup')
            : t('chatMembers', {count: chat.memberCount})
        : chat.username
          ? '@' + chat.username
          : '';

    async function copy(kind: 'username' | 'link') {
        try {
            await navigator.clipboard.writeText(
                kind === 'username' ? '@' + chat.username : 'https://t.me/' + chat.username,
            );
            setCopied(kind);
            setError(false);
        } catch {
            setError(true);
        }
    }

    return (
        <Flex alignItems="center" gap={3} className={styles.root}>
            <TelegramAvatar title={chat.title} url={chat.avatarUrl} size="m" />
            <Flex direction="column" className={styles.text}>
                <Text className={styles.title}>{chat.title}</Text>
                {chat.typingUsers?.length ? (
                    <TypingIndicator users={chat.typingUsers} group={group} live />
                ) : (
                    subtitle && (
                        <Flex alignItems="center" gap={1} className={styles.subtitleRow}>
                            <Text className={styles.subtitle} qa="chat-subtitle">
                                {subtitle}
                            </Text>
                            {!group && chat.username && (
                                <>
                                    {(['username', 'link'] as const).map((kind) => (
                                        <Tooltip
                                            key={kind}
                                            content={t(copied === kind ? 'copied' : 'chatCopy.' + kind)}
                                        >
                                            <Button
                                                view="flat-secondary"
                                                size="xs"
                                                className={styles.copy}
                                                aria-label={t('chatCopy.' + kind)}
                                                onClick={() => void copy(kind)}
                                            >
                                                <Icon
                                                    data={
                                                        copied === kind
                                                            ? Check
                                                            : kind === 'username'
                                                              ? Copy
                                                              : Link
                                                    }
                                                    size={14}
                                                />
                                            </Button>
                                        </Tooltip>
                                    ))}
                                </>
                            )}
                        </Flex>
                    )
                )}
                {error && (
                    <Text color="danger" role="alert">
                        {t('copyError')}
                    </Text>
                )}
            </Flex>
        </Flex>
    );
}
