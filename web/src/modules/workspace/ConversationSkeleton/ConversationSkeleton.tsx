import {Box, Flex, Skeleton} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';

import styles from '@/modules/workspace/ConversationSkeleton/ConversationSkeleton.module.css';

export function ConversationSkeleton() {
    const t = useTranslations();
    return (
        <Flex
            direction="column"
            className={styles.root}
            role="status"
            aria-label={t('loadingConversation')}
            qa="conversation-skeleton"
        >
            <Flex
                alignItems="center"
                justifyContent="space-between"
                className={styles.header}
                qa="skeleton-conversation-header"
            >
                <Flex alignItems="center" gap={3}>
                    <Skeleton className={styles.back} width={32} height={32} />
                    <Skeleton variant="circle" width={32} height={32} />
                    <Flex direction="column">
                        <Skeleton width={136} height={18} />
                        <Skeleton width={96} height={18} />
                    </Flex>
                </Flex>
                <Flex alignItems="center" gap={2}>
                    <Skeleton width={32} height={32} />
                    <Skeleton className={styles.inspector} width={32} height={32} />
                </Flex>
            </Flex>
            <Flex direction="column" className={styles.messages}>
                <Skeleton className={styles.date} width={72} height={24} />
                {Array.from({length: 7}, (_, index) => (
                    <Box
                        key={index}
                        className={[styles.bubble, index % 3 === 1 ? styles.outgoing : ''].join(' ')}
                    >
                        <Skeleton width={index % 2 === 0 ? 190 : 250} height={18} />
                        {index % 2 === 1 && <Skeleton width={150} height={18} />}
                        <Skeleton className={styles.time} width={38} height={14} />
                    </Box>
                ))}
            </Flex>
            <Flex alignItems="center" gap={3} className={styles.assistant} qa="skeleton-assistant-bar">
                <Skeleton width={36} height={36} />
                <Flex alignItems="center" gap={2} className={styles.model}>
                    <Skeleton width={116} height={16} />
                    <Skeleton width={126} height={18} />
                </Flex>
                <Flex gap={1}>
                    {[0, 1, 2].map((id) => (
                        <Skeleton key={id} width={36} height={36} qa="skeleton-assistant-action" />
                    ))}
                </Flex>
            </Flex>
        </Flex>
    );
}
