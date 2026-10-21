import {CircleStop} from '@gravity-ui/icons';
import {Box, Button, Flex, Icon, Text} from '@gravity-ui/uikit';
import {useLocale, useTranslations} from 'next-intl';
import {useState} from 'react';
import {ActivityProgress} from '@/modules/activity/ActivityProgress/ActivityProgress';
import {seconds} from '@/modules/activity/model/activity';
import {api, errorMessage} from '@/shared/api/client';
import type {Activity} from '@/shared/api/client';
import styles from '@/modules/workspace/ConversationGeneration/ConversationGeneration.module.css';
import {ActivityDetailsDialog} from '@/modules/activity/ActivityDetailsDialog/ActivityDetailsDialog';
import {StatusNotice} from '@/shared/ui/StatusNotice/StatusNotice';
export function ConversationGeneration({
    entry,
    onRefresh,
    onRetry,
}: {
    readonly entry: Activity;
    readonly onRefresh: () => Promise<void>;
    readonly onRetry?: () => Promise<void>;
}) {
    const t = useTranslations('events');
    const locale = useLocale();
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [details, setDetails] = useState(false);
    if (entry.status === 'error' || entry.status === 'interrupted') {
        return (
            <Box className={`${styles.root} ${styles.failure}`} qa="conversation-generation-error">
                <StatusNotice
                    title={t('failed')}
                    description={entry.detail}
                    actions={
                        <Flex gap={2} wrap>
                            <Button view="normal" onClick={() => setDetails(true)}>
                                {t('history')}
                            </Button>
                            {onRetry && (
                                <Button view="action" onClick={() => void onRetry()}>
                                    {t('retryDraft')}
                                </Button>
                            )}
                        </Flex>
                    }
                />
                {details && (
                    <ActivityDetailsDialog
                        id={entry.id}
                        onClose={() => setDetails(false)}
                        onRefresh={onRefresh}
                    />
                )}
            </Box>
        );
    }
    async function cancel() {
        setBusy(true);
        setError('');
        try {
            await api.activity.cancelActivity({
                id: entry.id,
            });
            await onRefresh();
        } catch (cause) {
            setError(errorMessage(cause));
        } finally {
            setBusy(false);
        }
    }
    return (
        <Box className={styles.root} qa="conversation-generation">
            <ActivityProgress entry={entry} className={styles.process}>
                <Flex alignItems="center" justifyContent="space-between" gap={3} className={styles.controls}>
                    <Text color="secondary">
                        {entry.durationMs
                            ? t('seconds', {value: seconds(entry.durationMs, locale)})
                            : t('waiting')}
                    </Text>
                    <Button
                        view="flat-danger"
                        loading={busy}
                        disabled={entry.phase === 'sending'}
                        onClick={() => void cancel()}
                        aria-label={t('stopNamed', {name: entry.title})}
                    >
                        <Icon data={CircleStop} size={16} />
                        {t('stop')}
                    </Button>
                </Flex>
                {error && (
                    <Text color="danger" role="alert">
                        {error}
                    </Text>
                )}
            </ActivityProgress>
        </Box>
    );
}
