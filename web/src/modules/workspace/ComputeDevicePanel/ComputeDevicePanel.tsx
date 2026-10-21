import {ArrowRotateRight} from '@gravity-ui/icons';
import {Button, Flex, Icon, Label, Select, Skeleton, Text} from '@gravity-ui/uikit';
import {useLocale, useTranslations} from 'next-intl';
import {useEffect, useState} from 'react';
import {api, errorMessage} from '@/shared/api/client';
import type * as Contract from '@aigram/api-client';
import styles from '@/modules/workspace/ComputeDevicePanel/ComputeDevicePanel.module.css';
const GIBIBYTE = 1024 ** 3;
export function ComputeDevicePanel({
    value,
    savedValue,
    onUpdate,
}: {
    readonly value: string;
    readonly savedValue: string;
    readonly onUpdate: (device: string) => void;
}) {
    const t = useTranslations('computeDevice');
    const locale = useLocale();
    const [status, setStatus] = useState<Contract.ComputeDeviceStatus>();
    const [revision, setRevision] = useState(0);
    const [busy, setBusy] = useState(true);
    const [error, setError] = useState('');
    useEffect(() => {
        const controller = new AbortController();
        void api.models
            .engineDevices({
                signal: controller.signal,
            })
            .then((result) => {
                if (!controller.signal.aborted) {
                    setStatus(result);
                    setError('');
                }
            })
            .catch((cause) => {
                if (!controller.signal.aborted) {
                    setError(errorMessage(cause));
                }
            })
            .finally(() => {
                if (!controller.signal.aborted) {
                    setBusy(false);
                }
            });
        return () => controller.abort();
    }, [savedValue, revision]);
    function name(device: Contract.ComputeDevice) {
        return device.kind === 'auto' || device.kind === 'cpu' ? t(device.kind) : device.name;
    }
    const options =
        status?.devices.map((device) => ({
            value: device.id,
            text: name(device),
            disabled: !device.selectable,
            content: (
                <Flex alignItems="center" justifyContent="space-between" gap={3} className={styles.option}>
                    <Text ellipsis>{name(device)}</Text>
                    {device.memoryBytes > 0 && (
                        <Text color="secondary">
                            {t('memory', {
                                value: new Intl.NumberFormat(locale, {maximumFractionDigits: 1}).format(
                                    device.memoryBytes / GIBIBYTE,
                                ),
                            })}
                        </Text>
                    )}
                </Flex>
            ),
        })) ?? [];
    if (status && !options.some((option) => option.value === value)) {
        options.push({
            value,
            text: t('unavailableSelection'),
            disabled: true,
            content: <Text>{t('unavailableSelection')}</Text>,
        });
    }
    const active = status?.devices.find((device) => device.id === status.active);
    return (
        <Flex direction="column" gap={3} qa="compute-device-panel">
            <Flex alignItems="center" justifyContent="space-between" gap={2}>
                <Text variant="subheader-2">{t('title')}</Text>
                <Button
                    view="flat-secondary"
                    aria-label={t('refresh')}
                    disabled={busy}
                    loading={busy && !!status}
                    onClick={() => {
                        setBusy(true);
                        setRevision((previous) => previous + 1);
                    }}
                >
                    <Icon data={ArrowRotateRight} size={18} />
                </Button>
            </Flex>
            {!status && !error && <Skeleton height={36} />}
            {status && (
                <>
                    <Select
                        size="l"
                        width="max"
                        value={[value]}
                        options={options}
                        disabled={!status.selectionSupported || busy}
                        onUpdate={([device]) => {
                            if (device) {
                                onUpdate(device);
                            }
                        }}
                        renderOption={(option) => <>{option.content}</>}
                        renderSelectedOption={(option) => <>{option.content}</>}
                        aria-label={t('label')}
                    />
                    {!status.selectionSupported && status.limitation && (
                        <Text color="secondary">{t('limitations.' + status.limitation)}</Text>
                    )}
                    {status.selectionSupported && (
                        <Text className={styles.note} color="secondary">
                            {t('nextStart')}
                        </Text>
                    )}
                    {status.restartRequired && status.selectionSupported && (
                        <Label className={styles.notice} theme="warning">
                            {t('restartRequired')}
                        </Label>
                    )}
                    {active && status.managed && (
                        <Text color="secondary">{t('active', {name: name(active)})}</Text>
                    )}
                    {status.nvidiaSelectionOnly && <Text color="secondary">{t('nvidiaOnly')}</Text>}
                    {value === 'auto' && status.autoUsesEnvironment && (
                        <Text color="secondary">{t('autoHint')}</Text>
                    )}
                    {status.devices
                        .filter((device) => device.reason)
                        .map((device) => (
                            <Text key={device.id} color="warning">
                                {device.name}: {t('reasons.' + device.reason)}
                            </Text>
                        ))}
                </>
            )}
            {error && (
                <Text color="danger" role="alert">
                    {error}
                </Text>
            )}
        </Flex>
    );
}
