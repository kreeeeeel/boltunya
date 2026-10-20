import {Clock} from '@gravity-ui/icons';
import {Box, Flex, Icon, Loader, Text} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import type {ReactNode} from 'react';

import type {Activity} from '@/shared/api/client';
import styles from '@/modules/activity/ActivityProgress/ActivityProgress.module.css';

export function ActivityProgress({
    entry,
    className = '',
    children,
}: {
    readonly entry: Activity;
    readonly className?: string;
    readonly children?: ReactNode;
}) {
    const t = useTranslations('events');
    const running = entry.status === 'generating';

    return (
        <Box className={`${styles.process} ${className}`} qa="reply-process">
            <Flex alignItems="center" gap={3}>
                {running ? <Loader size="s" /> : <Icon data={Clock} size={18} />}
                <Text variant="subheader-1" aria-live="polite">
                    {entry.status === 'queued'
                        ? t('position', {count: entry.queuePosition ?? 0})
                        : t(`phase.${entry.phase ?? 'context'}`)}
                </Text>
            </Flex>
            {entry.thinking ? (
                <Text className={styles.thinking}>{entry.thinking}</Text>
            ) : (
                <Text color="secondary" className={styles.preview}>
                    {entry.preview}
                </Text>
            )}
            {running && <Box className={styles.progress} role="progressbar" aria-label={t('processing')} />}
            {children}
        </Box>
    );
}
