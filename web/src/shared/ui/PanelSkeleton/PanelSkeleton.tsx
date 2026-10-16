import {Box, Flex, Skeleton} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';

import styles from '@/shared/ui/PanelSkeleton/PanelSkeleton.module.css';

export function PanelSkeleton() {
    const t = useTranslations();
    return (
        <Box className={styles.root} role="status" aria-label={t('loading')} qa="panel-skeleton">
            <Skeleton width={210} height={24} />
            <Skeleton width="62%" height={12} />
            <Box className={styles.summary}>
                <Skeleton width="46%" height={14} />
                <Skeleton width="70%" height={11} />
            </Box>
            <Flex className={styles.cards}>
                {Array.from({length: 3}, (_, index) => (
                    <Flex key={index} direction="column" className={styles.card}>
                        <Skeleton width={38} height={38} />
                        <Skeleton width="70%" height={16} />
                        <Skeleton width="100%" height={11} />
                        <Skeleton width="86%" height={11} />
                        <Skeleton height={32} />
                    </Flex>
                ))}
            </Flex>
        </Box>
    );
}
