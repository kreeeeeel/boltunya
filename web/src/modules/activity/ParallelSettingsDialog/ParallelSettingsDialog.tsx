import {Box, Dialog, Flex, Label, RadioGroup, Skeleton, Text} from '@gravity-ui/uikit';
import {useEffect, useState} from 'react';
import {useLocale, useTranslations} from 'next-intl';
import {api, dataOrThrow, errorMessage} from '@/shared/api/client';
import type {components} from '@/shared/api/schema';
import {SaveDialogFooter} from '@/shared/ui/SaveDialogFooter/SaveDialogFooter';
import {useSaveFeedback} from '@/shared/ui/SaveDialogFooter/useSaveFeedback';
import styles from '@/modules/activity/ParallelSettingsDialog/ParallelSettingsDialog.module.css';
const GIBIBYTE = 1024 ** 3;
export function ParallelSettingsDialog({
    value,
    onClose,
    onRefresh,
}: {
    readonly value: number;
    readonly onClose: () => void;
    readonly onRefresh: () => Promise<void>;
}) {
    const t = useTranslations('events.parallel');
    const common = useTranslations();
    const locale = useLocale();
    const [count, setCount] = useState(value);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [warning, setWarning] = useState('');
    const [sample, setSample] = useState<components['schemas']['MetricSample'] | null>(null);
    const [metricsLoading, setMetricsLoading] = useState(true);
    const [metricsError, setMetricsError] = useState(false);
    const feedback = useSaveFeedback(count);
    useEffect(() => {
        const controller = new AbortController();
        void api
            .GET('/api/metrics', {signal: controller.signal})
            .then((result) => {
                setSample(dataOrThrow(result).at(-1) ?? null);
            })
            .catch(() => {
                if (!controller.signal.aborted) {
                    setMetricsError(true);
                }
            })
            .finally(() => {
                if (!controller.signal.aborted) {
                    setMetricsLoading(false);
                }
            });
        return () => controller.abort();
    }, []);
    async function save() {
        setBusy(true);
        setError('');
        setWarning('');
        feedback.clearSaved();
        try {
            dataOrThrow(await api.PATCH('/api/settings', {body: {maxParallelTasks: count}}));
            feedback.markSaved();
            void onRefresh().catch(() => setWarning(common('settingsCenter.refreshWarning')));
        } catch (cause) {
            setError(errorMessage(cause));
        } finally {
            setBusy(false);
        }
    }
    const load = ['light', 'medium', 'high', 'veryHigh'][count - 1] ?? 'light';
    const bytes = (value: number) =>
        common('metrics.gb', {
            value: new Intl.NumberFormat(locale, {maximumFractionDigits: 1}).format(value / GIBIBYTE),
        });
    return (
        <Dialog open onClose={onClose} maxWidth="s" fullWidth contentOverflow="auto" aria-label={t('title')}>
            <Dialog.Header caption={t('title')} />
            <Dialog.Body>
                <Flex direction="column" gap={5}>
                    <Text color="secondary">{t('description')}</Text>
                    <Text variant="subheader-2">{t('limit')}</Text>
                    <RadioGroup
                        size="l"
                        direction="horizontal"
                        value={String(count)}
                        onUpdate={(value) => setCount(Number(value))}
                        options={[1, 2, 3, 4].map((value) => ({
                            value: String(value),
                            content: String(value),
                        }))}
                        aria-label={t('limit')}
                    />
                    <Text color="secondary">{t('gpuHint')}</Text>
                    {error && (
                        <Text color="danger" role="alert">
                            {error}
                        </Text>
                    )}
                    {warning && (
                        <Text color="warning" role="alert">
                            {warning}
                        </Text>
                    )}
                </Flex>
            </Dialog.Body>
            <Box className={styles.resources}>
                <Flex gap={2} alignItems="center" wrap>
                    <Text>{t('load')}</Text>
                    <Label
                        theme={
                            count >= 4 ? 'danger' : count === 3 ? 'warning' : count === 2 ? 'info' : 'normal'
                        }
                    >
                        {t('levels.' + load)}
                    </Label>
                </Flex>
                <Text color="secondary">{t('estimateHint')}</Text>
                {metricsLoading ? (
                    <Skeleton height={20} />
                ) : (
                    <Flex direction="column" gap={1}>
                        {sample && sample.appMemoryBytes + sample.ollamaMemoryBytes > 0 && (
                            <Text color="secondary">
                                {t('ram', {value: bytes(sample.appMemoryBytes + sample.ollamaMemoryBytes)})}
                            </Text>
                        )}
                        {sample?.gpuMemoryUsedBytes != null &&
                            sample.gpuMemoryTotalBytes != null &&
                            sample.gpuMemoryTotalBytes > 0 && (
                                <Text color="secondary">
                                    {t('vram', {
                                        used: bytes(sample.gpuMemoryUsedBytes),
                                        total: bytes(sample.gpuMemoryTotalBytes),
                                    })}
                                </Text>
                            )}
                        {(metricsError || !sample) && (
                            <Text color="secondary">{t('metricsUnavailable')}</Text>
                        )}
                    </Flex>
                )}
            </Box>
            <SaveDialogFooter
                saved={feedback.saved}
                busy={busy}
                saveLabel={common('save')}
                cancelLabel={common('close')}
                onSave={() => void save()}
                onClose={onClose}
            />
        </Dialog>
    );
}
