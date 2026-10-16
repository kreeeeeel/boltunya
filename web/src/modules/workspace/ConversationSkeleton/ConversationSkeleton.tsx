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
            <Flex alignItems="center" className={styles.header} gap={3}>
                <Skeleton variant="circle" width={34} height={34} />
                <Flex direction="column" gap={2}>
                    <Skeleton width={136} height={12} />
                    <Skeleton width={96} height={9} />
                </Flex>
            </Flex>
            <Flex direction="column" className={styles.messages}>
                {Array.from({length: 7}, (_, index) => (
                    <Box
                        key={index}
                        className={[styles.bubble, index % 3 === 1 ? styles.outgoing : ''].join(' ')}
                    >
                        <Skeleton width={index % 2 === 0 ? 190 : 250} height={12} />
                        {index % 2 === 1 && <Skeleton width={150} height={12} />}
                        <Skeleton width={38} height={8} />
                    </Box>
                ))}
            </Flex>
            <Box className={styles.composer}>
                <Skeleton height={40} />
            </Box>
        </Flex>
    );
}
