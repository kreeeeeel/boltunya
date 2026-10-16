'use client';

import {Box, Flex, Label, Text} from '@gravity-ui/uikit';
import {useEffect, useState} from 'react';
import {useTranslations} from 'next-intl';
import type {components} from '@/shared/api/schema';
import {api, dataOrThrow, errorMessage} from '@/shared/api/client';
import {ScrollArea} from '@/shared/ui/ScrollArea/ScrollArea';
import {PanelSkeleton} from '@/shared/ui/PanelSkeleton/PanelSkeleton';
import {ResourceChart} from '@/modules/monitoring/ResourceChart/ResourceChart';
import styles from '@/modules/monitoring/MetricsPanel/MetricsPanel.module.css';

const GIBIBYTE = 1024 ** 3;
type Sample = components['schemas']['MetricSample'];

export function MetricsPanel() {
    const t = useTranslations();
    const [samples, setSamples] = useState<Sample[] | null>(null);
    const [error, setError] = useState('');
    useEffect(() => {
        let active = true;
        let timer: ReturnType<typeof setTimeout>;
        async function poll() {
            try {
                const result = dataOrThrow(await api.GET('/api/metrics'));
                if (active) {
                    setSamples(result);
                    setError('');
                }
            } catch (cause) {
                if (active) {
                    setError(errorMessage(cause));
                }
            } finally {
                if (active) {
                    timer = setTimeout(poll, 2000);
                }
            }
        }
        void poll();
        return () => {
            active = false;
            clearTimeout(timer);
        };
    }, []);
    if (!samples && !error) {
        return <PanelSkeleton />;
    }
    const last = samples?.at(-1);
    const points = (samples ?? []).map((sample) => ({
        timestamp: sample.timestamp,
        cpu: sample.cpuPercent,
        appCpu: sample.appCpuPercent,
        gpu: sample.gpuPercent ?? null,
        memory: sample.memoryUsedBytes / GIBIBYTE,
        appMemory: sample.appMemoryBytes / GIBIBYTE,
        ollamaMemory: sample.ollamaMemoryBytes / GIBIBYTE,
        receive: sample.networkReceiveBytesPerSecond / 1024 ** 2,
        send: sample.networkSendBytesPerSecond / 1024 ** 2,
    }));
    const gb = (value: number) => t('metrics.gb', {value: (value / GIBIBYTE).toFixed(2)});
    return (
        <ScrollArea className={styles.root}>
            <Box className={styles.content}>
                <Flex justifyContent="space-between" alignItems="center">
                    <Text variant="header-2">{t('metrics.title')}</Text>
                    <Label theme={error ? 'danger' : 'success'}>
                        {t(error ? 'metrics.disconnected' : 'metrics.live')}
                    </Label>
                </Flex>
                <Text className={styles.description}>{t('metrics.description')}</Text>
                {error && <Text color="danger">{error}</Text>}
                <Box className={styles.grid}>
                    <ResourceChart
                        title="CPU"
                        value={last ? last.cpuPercent.toFixed(1) + '%' : '—'}
                        points={points}
                        maximum={100}
                        lines={[
                            {key: 'cpu', label: t('metrics.system'), color: '#3a91e8'},
                            {key: 'appCpu', label: t('metrics.application'), color: '#50b79c'},
                        ]}
                    />
                    <ResourceChart
                        title="GPU"
                        value={
                            last?.gpuPercent !== null && last?.gpuPercent !== undefined
                                ? last.gpuPercent.toFixed(1) + '%'
                                : t('metrics.unavailable')
                        }
                        points={points}
                        maximum={100}
                        lines={[{key: 'gpu', label: t('metrics.gpuLoad'), color: '#9573de'}]}
                    />
                    <ResourceChart
                        title={t('metrics.memory')}
                        value={last ? gb(last.memoryUsedBytes) : '—'}
                        points={points}
                        maximum={last ? last.memoryTotalBytes / GIBIBYTE : undefined}
                        lines={[{key: 'memory', label: t('metrics.system'), color: '#d89151'}]}
                    />
                    <ResourceChart
                        title={t('metrics.applicationMemory')}
                        value={last ? gb(last.appMemoryBytes + last.ollamaMemoryBytes) : '—'}
                        points={points}
                        lines={[
                            {key: 'appMemory', label: t('metrics.application'), color: '#50b79c'},
                            {key: 'ollamaMemory', label: 'Ollama', color: '#9573de'},
                        ]}
                    />
                    <ResourceChart
                        title={t('metrics.network')}
                        value={
                            last
                                ? (
                                      (last.networkReceiveBytesPerSecond + last.networkSendBytesPerSecond) /
                                      1024 ** 2
                                  ).toFixed(2) + ' MB/s'
                                : '—'
                        }
                        points={points}
                        lines={[
                            {key: 'receive', label: t('metrics.receive'), color: '#3a91e8'},
                            {key: 'send', label: t('metrics.send'), color: '#d89151'},
                        ]}
                    />
                    <Flex direction="column" gap={4} className={styles.details}>
                        <Text variant="subheader-2">{t('metrics.details')}</Text>
                        {last && (
                            <>
                                <Text>JVM · {gb(last.backendMemoryBytes)}</Text>
                                <Text>Ollama · {gb(last.ollamaMemoryBytes)}</Text>
                                <Text>
                                    VRAM ·{' '}
                                    {last.gpuMemoryUsedBytes !== null && last.gpuMemoryUsedBytes !== undefined
                                        ? `${gb(last.gpuMemoryUsedBytes)} / ${gb(last.gpuMemoryTotalBytes ?? 0)}`
                                        : t('metrics.unavailable')}
                                </Text>
                            </>
                        )}
                        <Text color="secondary">{t('metrics.note')}</Text>
                    </Flex>
                </Box>
            </Box>
        </ScrollArea>
    );
}
