import {Button, Flex, Skeleton, Text} from '@gravity-ui/uikit';
import {useEffect, useState} from 'react';
import {useLocale, useTranslations} from 'next-intl';
import {api, dataOrThrow, errorMessage} from '@/shared/api/client';
import type {components} from '@/shared/api/schema';

export function CachePanel() {
    const t = useTranslations();
    const locale = useLocale();
    const [stats, setStats] = useState<components['schemas']['CacheStatistics']>();
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    useEffect(() => {
        let active = true;
        void api
            .GET('/api/cache')
            .then(dataOrThrow)
            .then((value) => {
                if (active) {
                    setStats(value);
                }
            })
            .catch((cause) => {
                if (active) {
                    setError(errorMessage(cause));
                }
            });
        return () => {
            active = false;
        };
    }, []);
    async function update(clear = false) {
        setBusy(true);
        setError('');
        try {
            setStats(dataOrThrow(clear ? await api.POST('/api/cache/clear') : await api.GET('/api/cache')));
        } catch (cause) {
            setError(errorMessage(cause));
        } finally {
            setBusy(false);
        }
    }
    const size = (bytes: number) =>
        t('storage.megabytes', {
            count: new Intl.NumberFormat(locale, {maximumFractionDigits: 1}).format(bytes / 1024 / 1024),
        });
    return (
        <Flex direction="column" gap={3} qa="cache-panel">
            <Text variant="subheader-2">{t('storage.cache')}</Text>
            <Text color="secondary">{t('storage.cacheHint')}</Text>
            {!stats && !error && <Skeleton height={80} />}
            {stats && (
                <>
                    <Text>{t('storage.total', {size: size(stats.totalBytes), count: stats.fileCount})}</Text>
                    {(['photoBytes', 'videoBytes', 'audioBytes', 'documentBytes'] as const).map((key) => (
                        <Flex key={key} justifyContent="space-between">
                            <Text>{t('storage.' + key)}</Text>
                            <Text color="secondary">{size(stats[key])}</Text>
                        </Flex>
                    ))}
                </>
            )}
            <Flex gap={2} wrap>
                <Button
                    loading={busy}
                    disabled={!stats || stats.fileCount === 0}
                    view="outlined-danger"
                    onClick={() => void update(true)}
                >
                    {t('storage.clear')}
                </Button>
                <Button disabled={busy} onClick={() => void update()}>
                    {t('storage.refresh')}
                </Button>
            </Flex>
            {error && <Text color="danger">{error}</Text>}
        </Flex>
    );
}
