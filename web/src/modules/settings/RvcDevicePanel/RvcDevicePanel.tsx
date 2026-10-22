import {ArrowRotateRight} from '@gravity-ui/icons';
import {Button, Flex, Icon, Label, Select, Skeleton, Text} from '@gravity-ui/uikit';
import {useEffect, useMemo, useState} from 'react';
import {useLocale, useTranslations} from 'next-intl';
import type {SpeechComputeDevice, SpeechComputeStatus} from '@aigram/api-client';
import {api} from '@/shared/api/client';

export function RvcDevicePanel({
    value,
    savedValue,
    onUpdate,
    active = true,
}: {
    readonly value: SpeechComputeDevice;
    readonly savedValue: SpeechComputeDevice;
    readonly onUpdate: (device: SpeechComputeDevice) => void;
    readonly active?: boolean;
}) {
    const t = useTranslations('voice.rvcDevice');
    const locale = useLocale();
    const [revision, setRevision] = useState(0);
    const request = useMemo(() => ({active, revision}), [active, revision]);
    const [snapshot, setSnapshot] = useState<{
        readonly request: object;
        readonly status?: SpeechComputeStatus;
        readonly failed: boolean;
    }>();
    const busy = active && snapshot?.request !== request;
    const status = snapshot?.status;
    const cudaAvailable = !busy && !snapshot?.failed && status?.cudaAvailable;

    useEffect(() => {
        if (!request.active) {
            return;
        }
        const controller = new AbortController();
        void api.speech
            .getSpeechComputeStatus({signal: controller.signal})
            .then((result) => {
                if (!controller.signal.aborted) {
                    setSnapshot({request, status: result, failed: false});
                }
            })
            .catch(() => {
                if (!controller.signal.aborted) {
                    setSnapshot((previous) => ({request, status: previous?.status, failed: true}));
                }
            });
        return () => controller.abort();
    }, [request]);

    return (
        <Flex direction="column" gap={2} qa="rvc-device-panel">
            <Flex alignItems="center" justifyContent="space-between" gap={2}>
                <Text variant="subheader-2">{t('title')}</Text>
                <Button
                    view="flat-secondary"
                    disabled={busy || !active}
                    loading={busy && !!status}
                    aria-label={t('refresh')}
                    onClick={() => setRevision((previous) => previous + 1)}
                >
                    <Icon data={ArrowRotateRight} size={18} />
                </Button>
            </Flex>
            <Select
                size="l"
                width="max"
                value={[value]}
                aria-label={t('label')}
                options={[
                    {value: 'cpu', content: t('cpu')},
                    {value: 'cuda', content: t('cuda'), disabled: !cudaAvailable},
                ]}
                onUpdate={([device]) => {
                    if (device === 'cpu' || (device === 'cuda' && cudaAvailable)) {
                        onUpdate(device);
                    }
                }}
            />
            <Text color="secondary">{t('hint')}</Text>
            <Text color="secondary">{t('saved', {device: t(savedValue)})}</Text>
            {busy && !status && <Skeleton height={20} width={200} />}
            {!!status && !busy && !snapshot?.failed && (
                <>
                    {status.cudaAvailable && (
                        <Flex gap={2} alignItems="center" wrap="wrap">
                            <Label theme="success">{t('available')}</Label>
                            {!!status.gpuName && <Text color="secondary">{status.gpuName}</Text>}
                            {status.gpuMemoryBytes > 0 && (
                                <Text color="secondary">
                                    {t('memory', {
                                        value: new Intl.NumberFormat(locale, {
                                            maximumFractionDigits: 1,
                                        }).format(status.gpuMemoryBytes / 1024 ** 3),
                                    })}
                                </Text>
                            )}
                        </Flex>
                    )}
                    {status.reason !== 'ready' && (
                        <Text color={value === 'cuda' ? 'warning' : 'secondary'}>
                            {t('reasons.' + status.reason)}
                        </Text>
                    )}
                    {value === 'cuda' && <Text color="secondary">{t('strict')}</Text>}
                </>
            )}
            {snapshot?.failed && (
                <Text role="alert" color="danger">
                    {t('loadFailed')}
                </Text>
            )}
        </Flex>
    );
}
