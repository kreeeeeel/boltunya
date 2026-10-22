import {ArrowUpRightFromSquare} from '@gravity-ui/icons';
import {Button, Flex, Icon, Select, Switch, Text, Tooltip} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import type {SettingsDraftSectionProps} from '@/modules/settings/model/SettingsDraftSectionProps';
import {useSpeechState} from '@/modules/speech/model/useSpeechState';
import {speechReady} from '@/modules/speech/model/speechReady';
import {SpeechPreview} from '@/modules/speech/SpeechPreview/SpeechPreview';
import {SettingsSlider} from '@/shared/ui/SettingsSlider';
import type {SpeechComputeDevice} from '@aigram/api-client';

export function VoiceSettings({
    value,
    onUpdate,
    onOpenLibrary,
    libraryDisabled = false,
    savedDevice,
}: SettingsDraftSectionProps & {
    readonly onOpenLibrary?: () => void;
    readonly libraryDisabled?: boolean;
    readonly savedDevice: SpeechComputeDevice;
}) {
    const t = useTranslations('voice');
    const speech = useSpeechState();
    const assets = speech.state?.assets ?? [];

    return (
        <Flex direction="column" gap={5}>
            <Flex direction="column" gap={2}>
                <Switch
                    checked={value.voiceInputEnabled}
                    onUpdate={(voiceInputEnabled) => onUpdate({...value, voiceInputEnabled})}
                >
                    {t('recognitionEnabled')}
                </Switch>
                <Text color="secondary">{t('recognitionHint')}</Text>
            </Flex>
            {(['recognition', 'synthesis'] as const).map((kind) => {
                const key = kind === 'recognition' ? 'voiceRecognitionModel' : 'voiceSynthesisModel';
                const selected = value[key];
                const installed = assets.filter(
                    (asset) =>
                        asset.kind === kind &&
                        asset.installed &&
                        asset.dependencies.every((id) =>
                            assets.some((dependency) => dependency.id === id && dependency.installed),
                        ),
                );
                const available = installed.some((asset) => asset.id === selected);
                return (
                    <Flex key={kind} direction="column" gap={2}>
                        <Text>{t('models.' + kind)}</Text>
                        <Select
                            size="l"
                            filterable
                            width="max"
                            value={available ? [selected] : []}
                            placeholder={t('chooseInstalled')}
                            aria-label={t('models.' + kind)}
                            disabled={!installed.length}
                            options={installed.map((asset) => ({value: asset.id, content: asset.title}))}
                            onUpdate={([id]) => {
                                if (id) {
                                    onUpdate({...value, [key]: id});
                                }
                            }}
                        />
                        {!available && !!speech.state && (
                            <Text color="secondary">{t('selectionUnavailable')}</Text>
                        )}
                    </Flex>
                );
            })}
            <SettingsSlider
                label={t('speechRate')}
                displayValue={value.voiceSpeechRate.toFixed(1) + '×'}
                min={0.5}
                max={2}
                step={0.1}
                value={value.voiceSpeechRate}
                onUpdate={(voiceSpeechRate) => onUpdate({...value, voiceSpeechRate})}
            />
            {speech.error && (
                <Text color="danger" role="alert">
                    {speech.error}
                </Text>
            )}
            {onOpenLibrary && (
                <Flex>
                    <Tooltip content={libraryDisabled ? t('saveBeforeLibrary') : undefined}>
                        <Button size="l" view="flat-info" onClick={onOpenLibrary} disabled={libraryDisabled}>
                            <Icon data={ArrowUpRightFromSquare} size={18} />
                            {t('openLibrary')}
                        </Button>
                    </Tooltip>
                </Flex>
            )}
            <SpeechPreview
                modelId={value.voiceSynthesisModel}
                speed={value.voiceSpeechRate}
                ready={speechReady(assets, value.voiceSynthesisModel)}
                assets={assets}
                configurationKey={savedDevice}
                blockedReason={
                    value.voiceConversionDevice !== savedDevice ? t('rvcDevice.saveBeforePreview') : undefined
                }
            />
        </Flex>
    );
}
