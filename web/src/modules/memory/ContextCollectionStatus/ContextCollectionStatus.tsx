import {Button, Flex, Loader, Text} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import type * as Contract from '@aigram/api-client';
import styles from '@/modules/memory/ContextCollectionStatus/ContextCollectionStatus.module.css';
export function ContextCollectionStatus({
    progress,
    busy,
    onCancel,
    onDetails,
}: {
    readonly progress: Contract.MemoryCollectionProgress;
    readonly busy: boolean;
    readonly onCancel: () => void;
    readonly onDetails?: () => void;
}) {
    const t = useTranslations();
    return (
        <Flex direction="column" gap={3} className={styles.root} qa="context-progress">
            <Flex alignItems="center" gap={3} role="status" aria-live="polite">
                {progress.status !== 'error' && <Loader size="s" />}
                <Text variant="subheader-1">{t('context.collecting')}</Text>
            </Flex>
            <Flex direction="column" gap={1}>
                <Text>{t('memory.status.' + progress.status)}</Text>
                <Text color="secondary">{t('context.queue', {count: progress.queuedChats})}</Text>
                {progress.queuePosition > 0 && (
                    <Text color="secondary">{t('context.position', {position: progress.queuePosition})}</Text>
                )}
                <Text color="secondary">{t('memory.scanned', {count: progress.processedMessages})}</Text>
            </Flex>
            <Flex gap={2}>
                <Button view="outlined" size="m" loading={busy} onClick={onCancel}>
                    {t('context.cancel')}
                </Button>
                {onDetails && (
                    <Button view="flat-secondary" size="m" onClick={onDetails}>
                        {t('context.details')}
                    </Button>
                )}
            </Flex>
        </Flex>
    );
}
