import {ArrowRotateRight, CircleExclamation, TrashBin} from '@gravity-ui/icons';
import {Box, Button, Flex, Icon, Label, Skeleton, Text} from '@gravity-ui/uikit';
import {useLocale, useTranslations} from 'next-intl';
import {useCallback, useEffect, useState} from 'react';

import {api, dataOrThrow, errorMessage} from '@/shared/api/client';
import type {components} from '@/shared/api/schema';
import {ScrollArea} from '@/shared/ui/ScrollArea/ScrollArea';
import {ErrorDetailsDialog} from '@/modules/diagnostics/ErrorDetailsDialog/ErrorDetailsDialog';
import styles from '@/modules/diagnostics/ErrorsPanel/ErrorsPanel.module.css';

export function ErrorsPanel() {
    const t = useTranslations('diagnostics');
    const locale = useLocale();
    const [entries, setEntries] = useState<components['schemas']['DiagnosticEntry'][]>();
    const [selected, setSelected] = useState<components['schemas']['DiagnosticEntry']>();
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);

    const refresh = useCallback(async () => {
        const value = dataOrThrow(await api.GET('/api/diagnostics/errors'));
        return value.errors;
    }, []);

    useEffect(() => {
        let active = true;
        let timeout: ReturnType<typeof setTimeout>;
        const read = async () => {
            try {
                const value = await refresh();
                if (active) {
                    setEntries(value);
                    setError('');
                }
            } catch (cause) {
                if (active) {
                    setError(errorMessage(cause));
                }
            } finally {
                if (active) {
                    timeout = setTimeout(() => void read(), 5000);
                }
            }
        };
        void read();
        return () => {
            active = false;
            clearTimeout(timeout);
        };
    }, [refresh]);

    async function act(clear: boolean) {
        setBusy(true);
        setError('');
        try {
            if (clear) {
                dataOrThrow(await api.DELETE('/api/diagnostics/errors'));
            }
            setEntries(await refresh());
        } catch (cause) {
            setError(errorMessage(cause));
        } finally {
            setBusy(false);
        }
    }

    return (
        <ScrollArea className={styles.root}>
            <Box className={styles.content}>
                <Flex
                    className={styles.toolbar}
                    alignItems="center"
                    justifyContent="space-between"
                    gap={3}
                    wrap
                >
                    <Flex direction="column" gap={1}>
                        <Text variant="header-1">{t('title')}</Text>
                        <Text color="secondary">{t('description')}</Text>
                    </Flex>
                    <Flex gap={2}>
                        <Button
                            view="flat-secondary"
                            disabled={busy}
                            aria-label={t('refresh')}
                            onClick={() => void act(false)}
                        >
                            <Icon data={ArrowRotateRight} size={18} />
                        </Button>
                        <Button
                            view="flat-secondary"
                            disabled={busy || !entries?.length}
                            aria-label={t('clear')}
                            onClick={() => void act(true)}
                        >
                            <Icon data={TrashBin} size={18} />
                        </Button>
                    </Flex>
                </Flex>
                {!entries && !error && (
                    <Box className={styles.cards} aria-label={t('loading')} role="status">
                        {Array.from({length: 3}, (_, index) => (
                            <Skeleton key={index} height={210} />
                        ))}
                    </Box>
                )}
                {entries?.length === 0 && (
                    <Flex
                        direction="column"
                        gap={3}
                        alignItems="center"
                        justifyContent="center"
                        className={styles.empty}
                        qa="empty-errors"
                    >
                        <Box className={styles.emptyIcon}>
                            <Icon data={CircleExclamation} size={36} />
                        </Box>
                        <Text variant="header-1">{t('empty')}</Text>
                        <Text color="secondary">{t('emptyDescription')}</Text>
                    </Flex>
                )}
                <Box className={styles.cards} qa="errors-grid">
                    {entries?.map((entry) => (
                        <Box
                            as="button"
                            type="button"
                            key={entry.id}
                            className={styles.card}
                            onClick={() => setSelected(entry)}
                            qa="error-card"
                        >
                            <Flex alignItems="center" justifyContent="space-between" gap={2}>
                                <Label theme="danger">{t('sources.' + entry.source)}</Label>
                                {entry.occurrences > 1 && (
                                    <Label>{t('occurrences', {count: entry.occurrences})}</Label>
                                )}
                            </Flex>
                            <Text className={styles.message} variant="subheader-2">
                                {entry.message}
                            </Text>
                            <Text color="secondary" className={styles.remediation}>
                                {entry.remediation}
                            </Text>
                            <Text color="secondary" className={styles.date}>
                                {new Date(entry.date * 1000).toLocaleString(locale)}
                            </Text>
                        </Box>
                    ))}
                </Box>
                {error && (
                    <Flex direction="column" gap={3}>
                        <Text color="danger" role="alert">
                            {error}
                        </Text>
                        <Button onClick={() => void act(false)}>{t('refresh')}</Button>
                    </Flex>
                )}
            </Box>
            {selected && <ErrorDetailsDialog entry={selected} onClose={() => setSelected(undefined)} />}
        </ScrollArea>
    );
}
