import {Flex, Skeleton} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';

import styles from '@/shared/ui/FormSkeleton/FormSkeleton.module.css';

export function FormSkeleton() {
    const t = useTranslations();
    return (
        <Flex
            direction="column"
            className={styles.root}
            role="status"
            aria-label={t('loading')}
            qa="form-skeleton"
        >
            <Skeleton width="70%" height={13} />
            <Skeleton width="90%" height={11} />
            {Array.from({length: 3}, (_, index) => (
                <Flex direction="column" gap={2} key={index}>
                    <Skeleton width={100} height={10} />
                    <Skeleton height={36} />
                </Flex>
            ))}
        </Flex>
    );
}
