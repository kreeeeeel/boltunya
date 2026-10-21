import {Button, Flex, RadioGroup, Select, Skeleton, Switch, Text} from '@gravity-ui/uikit';
import {useState} from 'react';
import {useTranslations} from 'next-intl';

import type {SettingsDraftSectionProps} from '@/modules/settings/model/SettingsDraftSectionProps';
import {useMemoryDocument} from '@/modules/memory/model/useMemoryDocument';
import {ContextCollectionStatus} from '@/modules/memory/ContextCollectionStatus/ContextCollectionStatus';

export function MemoryCollectionSettings({value, onUpdate}: SettingsDraftSectionProps) {
    const t = useTranslations();
    const memory = useMemoryDocument('');
    const [mode, setMode] = useState('update');
    const document = memory.document;
    const active = document?.collection?.active ?? false;
    return (
        <Flex direction="column" gap={5}>
            <Switch
                checked={value.memoryEnabled}
                onUpdate={(memoryEnabled) => onUpdate({...value, memoryEnabled})}
            >
                {t('memory.enabled')}
            </Switch>
            <Text color="secondary">{t('memory.scheduleHint')}</Text>
            <Select
                size="l"
                width="max"
                disabled={!value.memoryEnabled}
                value={[String(value.memoryIntervalHours)]}
                options={Array.from(new Set([1, 3, 6, 12, 24, 48, 168, value.memoryIntervalHours]))
                    .sort((a, b) => a - b)
                    .map((hours) => ({value: String(hours), content: t('memory.hours', {count: hours})}))}
                onUpdate={([hours]) => onUpdate({...value, memoryIntervalHours: Number(hours)})}
                aria-label={t('memory.interval')}
            />
            <Text variant="subheader-3">{t('context.globalTitle')}</Text>
            <Text color="secondary">{t('context.globalHint')}</Text>
            {!document && !memory.error && <Skeleton height={120} />}
            {document && (
                <>
                    <Text color="secondary">{t('memory.tracked', {count: document.trackedChats})}</Text>
                    {active && document.collection ? (
                        <ContextCollectionStatus
                            progress={document.collection}
                            busy={memory.busy}
                            onCancel={() => void memory.cancel()}
                        />
                    ) : (
                        <>
                            <RadioGroup
                                direction="vertical"
                                value={mode}
                                onUpdate={setMode}
                                disabled={memory.busy}
                                aria-label={t('context.mode')}
                                options={['update', 'rebuild'].map((value) => ({
                                    value,
                                    content: (
                                        <Flex direction="column" gap={1}>
                                            <Text>{t('context.' + value)}</Text>
                                            <Text color="secondary">
                                                {t(
                                                    value === 'update'
                                                        ? 'context.globalUpdateHint'
                                                        : 'context.globalRebuildHint',
                                                )}
                                            </Text>
                                        </Flex>
                                    ),
                                }))}
                            />
                            <Button
                                view="outlined-info"
                                size="l"
                                disabled={memory.busy || document.status === 'disabled'}
                                onClick={() => void memory.refresh(mode === 'rebuild', true)}
                            >
                                {t('context.start')}
                            </Button>
                        </>
                    )}
                    <Text color="secondary">
                        {t('memory.status.' + document.status)} ·{' '}
                        {t('memory.scanned', {count: document.processedMessages})}
                    </Text>
                </>
            )}
            {(memory.error || document?.error) && (
                <Text color="danger" role="alert">
                    {memory.error || document?.error}
                </Text>
            )}
        </Flex>
    );
}
