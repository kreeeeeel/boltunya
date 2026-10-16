import {Flex, Skeleton} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';

import styles from '@/shared/ui/ReplySkeleton/ReplySkeleton.module.css';

export function ReplySkeleton() {
    const t = useTranslations();
    return (
        <Flex
            direction="column"
            className={styles.root}
            role="status"
            aria-label={t('generating')}
            qa="reply-skeleton"
        >
            <Skeleton height={11} width="100%" />
            <Skeleton height={11} width="84%" />
            <Skeleton height={11} width="55%" />
        </Flex>
    );
}
