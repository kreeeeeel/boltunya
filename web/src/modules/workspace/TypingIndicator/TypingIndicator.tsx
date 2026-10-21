import {Box, Flex, Text} from '@gravity-ui/uikit';
import {useLocale, useTranslations} from 'next-intl';
import type {Chat} from '@/shared/api/client';
import styles from '@/modules/workspace/TypingIndicator/TypingIndicator.module.css';
export function TypingIndicator({
    users,
    group = false,
    live = false,
}: {
    readonly users: NonNullable<Chat['typingUsers']>;
    readonly group?: boolean;
    readonly live?: boolean;
}) {
    const t = useTranslations('typing');
    const locale = useLocale();
    if (users.length === 0) {
        return null;
    }
    const action = users.every((user) => user.action === users[0].action) ? users[0].action : 'active';
    const actionText = t((users.length > 1 ? 'plural.' : 'singular.') + action);
    const names = users.slice(0, 2).map((user) => user.name.trim() || t('participant'));
    if (users.length > 2) {
        names.push(t('others', {count: users.length - 2}));
    }
    const label = group
        ? t('group', {
              names: new Intl.ListFormat(locale, {style: 'short', type: 'conjunction'}).format(names),
              action: actionText,
          })
        : actionText;
    return (
        <Flex
            className={styles.root}
            gap={2}
            alignItems="center"
            qa="typing-indicator"
            role={live ? 'status' : undefined}
        >
            <Flex className={styles.dots} gap={1} alignItems="center" aria-hidden>
                {[0, 1, 2].map((index) => (
                    <Box key={index} className={styles.dot} style={{animationDelay: index * 150 + 'ms'}} />
                ))}
            </Flex>
            <Text className={styles.text}>{label}</Text>
        </Flex>
    );
}
