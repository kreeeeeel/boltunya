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
                    <Skeleton className={styles.model} width={90} height={18} />
                    <Skeleton width={32} height={32} />
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
            <Box className={styles.composerArea}>
                <Flex alignItems="center" gap={1} className={styles.composer} qa="skeleton-message-composer">
                    <Skeleton width={32} height={32} qa="skeleton-composer-control" />
                    <Skeleton width={32} height={32} qa="skeleton-composer-control" />
                    <Skeleton className={styles.input} height={18} />
                    <Skeleton width={32} height={32} qa="skeleton-composer-control" />
                    <Skeleton width={32} height={32} qa="skeleton-composer-control" />
                    <Skeleton width={32} height={32} qa="skeleton-composer-control" />
                </Flex>
            </Box>
        </Flex>
    );
}
