import {Box, Button, Flex, Loader, Select, Switch, Text} from '@gravity-ui/uikit';
import {useState} from 'react';
import {useTranslations} from 'next-intl';
import type {AppState} from '@/shared/api/client';
import {useSpeechState} from '@/modules/speech/model/useSpeechState';
import {speechReady} from '@/modules/speech/model/speechReady';
import {chatVoiceOverride} from '@/modules/workspace/RuleDialog/model/chatVoiceOverride';
import type {RuleFieldsProps} from '@/modules/workspace/RuleDialog/model/RuleFieldsProps';
import styles from '@/modules/workspace/RuleDialog/RuleDialog.module.css';
import {RuleSelectFilter} from '@/modules/workspace/RuleDialog/components/RuleSelectFilter';
import {SettingsSlider} from '@/shared/ui/SettingsSlider';

export function ChatVoiceFields({
    value,
    onUpdate,
    settings,
}: RuleFieldsProps & {readonly settings?: AppState['settings']}) {
    const t = useTranslations('voice');
    const local = useTranslations('chatSettings.voice');
    const speech = useSpeechState();
    const [customized, setCustomized] = useState(value.voiceSettingsMode === 'override');
    const override = value.voiceSettingsMode === 'override';
    const assets = speech.state?.assets ?? [];

    function selectMode(mode: string) {
        if (mode === 'inherit') {
            onUpdate({...value, voiceSettingsMode: 'inherit'});
        } else if (mode === 'override') {
            onUpdate(
                customized ? {...value, voiceSettingsMode: 'override'} : chatVoiceOverride(value, settings),
            );
            setCustomized(true);
        }
    }

    function modelOptions(kind: 'recognition' | 'synthesis', selected: string) {
        const options = assets
            .filter((asset) => asset.kind === kind && asset.installed)
            .map((asset) => ({value: asset.id, content: asset.title, disabled: false}));
        if (selected && !options.some((option) => option.value === selected)) {
            options.unshift({value: selected, content: selected + ' · ' + t('notInstalled'), disabled: true});
        }
        return options;
    }

    return (
        <Flex direction="column" gap={5}>
            <Flex direction="column" gap={2}>
                <Text>{local('source')}</Text>
                <Select
                    size="l"
                    width="max"
                    value={[override ? 'override' : 'inherit']}
                    options={(['inherit', 'override'] as const).map((mode) => ({
                        value: mode,
                        content: local(mode),
                    }))}
                    aria-label={local('source')}
                    onUpdate={([mode]) => selectMode(mode)}
                />
                <Text color="secondary">{local(override ? 'overrideHint' : 'inheritHint')}</Text>
            </Flex>
            {override && (
                <>
                    {!speech.state && !speech.error && (
                        <Flex alignItems="center" gap={2} role="status">
                            <Loader size="s" />
                            <Text>{t('manualLoadingModels')}</Text>
                        </Flex>
                    )}
                    {speech.error && (
                        <Flex direction="column" gap={2}>
                            <Text color="danger" role="alert">
                                {speech.error}
                            </Text>
                            <Box>
                                <Button view="outlined" onClick={speech.reload}>
                                    {t('retry')}
                                </Button>
                            </Box>
                        </Flex>
                    )}
                    <Flex direction="column" gap={2}>
                        <Text>{t('replyMode')}</Text>
                        <Select
                            size="l"
                            width="max"
                            value={[value.voiceReplyMode ?? 'text']}
                            options={(['text', 'always', 'random', 'mirror'] as const).map((mode) => ({
                                value: mode,
                                content: t('replyModes.' + mode),
                            }))}
                            aria-label={t('replyMode')}
                            onUpdate={([voiceReplyMode]) => {
                                if (
                                    voiceReplyMode === 'text' ||
                                    voiceReplyMode === 'always' ||
                                    voiceReplyMode === 'random' ||
                                    voiceReplyMode === 'mirror'
                                ) {
                                    onUpdate({...value, voiceReplyMode});
                                }
                            }}
                        />
                        <Text color="secondary">{t('replyHints.' + (value.voiceReplyMode ?? 'text'))}</Text>
                    </Flex>
                    {value.voiceReplyMode === 'random' && (
                        <SettingsSlider
                            label={t('probability')}
                            displayValue={(value.voiceReplyProbability ?? 50) + '%'}
                            min={0}
                            max={100}
                            step={1}
                            value={value.voiceReplyProbability ?? 50}
                            onUpdate={(voiceReplyProbability) => onUpdate({...value, voiceReplyProbability})}
                        />
                    )}
                    <Flex direction="column" gap={2}>
                        <Text>{t('models.synthesis')}</Text>
                        <Select
                            size="l"
                            filterable
                            renderFilter={RuleSelectFilter}
                            width="max"
                            popupClassName={styles.selectPopup}
                            value={[value.voiceSynthesisModel ?? 'piper-ru-irina']}
                            options={modelOptions('synthesis', value.voiceSynthesisModel ?? 'piper-ru-irina')}
                            disabled={!speech.state}
                            aria-label={t('models.synthesis')}
                            onUpdate={([voiceSynthesisModel]) => {
                                if (voiceSynthesisModel) {
                                    onUpdate({...value, voiceSynthesisModel});
                                }
                            }}
                        />
                        {speech.state &&
                            !speechReady(assets, value.voiceSynthesisModel ?? 'piper-ru-irina') && (
                                <Text color="secondary">{t('modelsRequired')}</Text>
                            )}
                    </Flex>
                    <SettingsSlider
                        label={t('speechRate')}
                        displayValue={(value.voiceSpeechRate ?? 1).toFixed(1) + '×'}
                        min={0.5}
                        max={2}
                        step={0.1}
                        value={value.voiceSpeechRate ?? 1}
                        onUpdate={(voiceSpeechRate) => onUpdate({...value, voiceSpeechRate})}
                    />
                    <Flex direction="column" gap={2}>
                        <Switch
                            checked={value.voiceInputEnabled ?? false}
                            onUpdate={(voiceInputEnabled) => onUpdate({...value, voiceInputEnabled})}
                        >
                            {t('recognitionEnabled')}
                        </Switch>
                        <Text color="secondary">{t('recognitionHint')}</Text>
                    </Flex>
                    {value.voiceInputEnabled && (
                        <Flex direction="column" gap={2}>
                            <Text>{t('models.recognition')}</Text>
                            <Select
                                size="l"
                                filterable
                                renderFilter={RuleSelectFilter}
                                width="max"
                                popupClassName={styles.selectPopup}
                                value={[value.voiceRecognitionModel ?? 'whisper-tiny']}
                                options={modelOptions(
                                    'recognition',
                                    value.voiceRecognitionModel ?? 'whisper-tiny',
                                )}
                                disabled={!speech.state}
                                aria-label={t('models.recognition')}
                                onUpdate={([voiceRecognitionModel]) => {
                                    if (voiceRecognitionModel) {
                                        onUpdate({...value, voiceRecognitionModel});
                                    }
                                }}
                            />
                        </Flex>
                    )}
                </>
            )}
        </Flex>
    );
}
