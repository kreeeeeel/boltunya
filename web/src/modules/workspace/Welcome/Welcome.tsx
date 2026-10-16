import {Box, Button, Flex, Icon, Loader, Text} from '@gravity-ui/uikit';
import {ArrowRight} from '@gravity-ui/icons';
import {useTranslations} from 'next-intl';
import {useState} from 'react';

import {ConnectDialog} from '@/modules/workspace/ConnectDialog/ConnectDialog';
import styles from '@/modules/workspace/Welcome/Welcome.module.css';
import type {AppState} from '@/shared/api/client';
import {api, dataOrThrow, errorMessage} from '@/shared/api/client';

export function Welcome({
    state,
    loading,
    error,
    onRefresh,
}: {
    readonly state: AppState | null;
    readonly loading: boolean;
    readonly error: string;
    readonly onRefresh: () => Promise<void>;
}) {
    const t = useTranslations();
    const [connect, setConnect] = useState(false);
    const [restoreError, setRestoreError] = useState('');
    const continuing = state && !['disconnected', 'error'].includes(state.telegram.state);
    const visibleError = restoreError || error || state?.telegram.error;
    async function retry() {
        try {
            if (state?.telegram.state === 'error') {
                dataOrThrow(await api.POST('/api/telegram/restore'));
            }
            await onRefresh();
            setRestoreError('');
        } catch (cause) {
            setRestoreError(errorMessage(cause));
        }
    }
    return (
        <Flex className={styles.root} direction="column" alignItems="center" qa="welcome">
            <Flex direction="column" alignItems="center" className={styles.content}>
                <Box
                    as="img"
                    src="/assets/illustrations/welcome.png"
                    alt=""
                    aria-hidden
                    className={styles.illustration}
                />
                <Text as="h1" className={styles.title}>
                    {t('welcomeTitle')}
                </Text>
                <Text className={styles.description}>{t('welcomeDescription')}</Text>
                {loading || state?.telegram.state === 'connecting' ? (
                    <Flex
                        gap={3}
                        alignItems="center"
                        role="status"
                        aria-label={t('loading')}
                        className={styles.loading}
                    >
                        <Loader size="s" />
                        <Text>
                            {t(state?.telegram.state === 'connecting' ? 'restoringSession' : 'loading')}
                        </Text>
                    </Flex>
                ) : (
                    <Button
                        view="action"
                        size="xl"
                        className={styles.connect}
                        disabled={!state}
                        onClick={() => setConnect(true)}
                    >
                        {t(continuing ? 'continueLogin' : 'connect')}
                        <Icon data={ArrowRight} size={19} />
                    </Button>
                )}
                {visibleError && (
                    <Flex direction="column" alignItems="center" gap={3} className={styles.error}>
                        <Text color="danger">{visibleError}</Text>
                        <Button onClick={() => void retry()}>{t('retry')}</Button>
                    </Flex>
                )}
            </Flex>
            {connect && (
                <ConnectDialog
                    status={state?.telegram}
                    onClose={() => setConnect(false)}
                    onRefresh={onRefresh}
                />
            )}
        </Flex>
    );
}
