import {Box, Flex, Select, Slider, Switch, Text} from '@gravity-ui/uikit';
import {useEffect, useId, useState} from 'react';
import {useTranslations} from 'next-intl';
import type {SettingsDraftSectionProps} from '@/modules/settings/model/SettingsDraftSectionProps';
import {SpeechAssets} from '@/modules/speech/SpeechAssets/SpeechAssets';
import {useSpeechState} from '@/modules/speech/model/useSpeechState';
import {speechReady} from '@/modules/speech/model/speechReady';
import {SpeechPreview} from '@/modules/speech/SpeechPreview/SpeechPreview';
import {SpeechToolbar} from '@/modules/speech/SpeechToolbar/SpeechToolbar';
import {SpeechModelImport} from '@/modules/speech/SpeechModelImport/SpeechModelImport';
import {api, errorMessage} from '@/shared/api/client';
import {desktopBridge} from '@/shared/desktop/bridge';

export function VoiceSettings({value, onUpdate}: SettingsDraftSectionProps) {
    const t = useTranslations('voice');
    const speech = useSpeechState();
    const [importExpanded, setImportExpanded] = useState(false);
    const importId = useId();
    const [storageOpenable, setStorageOpenable] = useState(false);
    const [storageRevision, setStorageRevision] = useState(0);
    const [storageError, setStorageError] = useState('');
    const [browsing, setBrowsing] = useState(false);
    const desktop = desktopBridge();
    const installedAssets =
        speech.state?.assets
            .filter((asset) => asset.installed)
            .map((asset) => asset.id)
            .join(',') ?? '';

    useEffect(() => {
        const controller = new AbortController();
        if (desktop) {
            void api.system
                .storageLocations({signal: controller.signal})
                .then(({locations}) => {
                    if (!controller.signal.aborted) {
                        setStorageOpenable(
                            locations.some((location) => location.id === 'speech' && location.openable),
                        );
                    }
                })
                .catch((cause) => {
                    if (!controller.signal.aborted) {
                        setStorageError(errorMessage(cause));
                    }
                });
        }
        return () => controller.abort();
    }, [desktop, storageRevision, installedAssets]);

    async function browse() {
        setBrowsing(true);
        setStorageError('');
        try {
            await desktop?.openStorageLocation('speech');
        } catch (cause) {
            setStorageError(errorMessage(cause));
        } finally {
            setBrowsing(false);
        }
    }

    function reload() {
        speech.reload();
        setStorageRevision((current) => current + 1);
    }

    function imported() {
        window.dispatchEvent(new Event('speech-assets-updated'));
        setStorageRevision((current) => current + 1);
    }

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
            <Flex direction="column" gap={2}>
                <Text>{t('replyMode')}</Text>
                <Select
                    size="l"
                    width="max"
                    value={[value.voiceReplyMode]}
                    aria-label={t('replyMode')}
                    options={(['text', 'always', 'random', 'mirror'] as const).map((mode) => ({
                        value: mode,
                        content: t('replyModes.' + mode),
                    }))}
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
                <Text color="secondary">{t('replyHints.' + value.voiceReplyMode)}</Text>
            </Flex>
            {value.voiceReplyMode === 'random' && (
                <Flex direction="column" gap={2}>
                    <Flex justifyContent="space-between" gap={3}>
                        <Text>{t('probability')}</Text>
                        <Text color="secondary">{value.voiceReplyProbability}%</Text>
                    </Flex>
                    <Slider
                        min={0}
                        max={100}
                        step={1}
                        value={value.voiceReplyProbability}
                        aria-label={t('probability')}
                        onUpdate={(voiceReplyProbability) => onUpdate({...value, voiceReplyProbability})}
                    />
                </Flex>
            )}
            {(['recognition', 'synthesis'] as const).map((kind) => {
                const key = kind === 'recognition' ? 'voiceRecognitionModel' : 'voiceSynthesisModel';
                const selected = value[key];
                const assets = speech.state?.assets.filter((asset) => asset.kind === kind) ?? [];
                return (
                    <Flex key={kind} direction="column" gap={2}>
                        <Text>{t('models.' + kind)}</Text>
                        <Select
                            size="l"
                            width="max"
                            value={selected ? [selected] : []}
                            placeholder={t('chooseInstalled')}
                            aria-label={t('models.' + kind)}
                            disabled={!assets.length}
                            options={assets.map((asset) => ({
                                value: asset.id,
                                content: asset.title + (asset.installed ? '' : ' · ' + t('notInstalled')),
                            }))}
                            onUpdate={([id]) => {
                                if (id) {
                                    onUpdate({...value, [key]: id});
                                }
                            }}
                        />
                    </Flex>
                );
            })}
            <Flex direction="column" gap={2}>
                <Flex justifyContent="space-between" gap={3}>
                    <Text>{t('speechRate')}</Text>
                    <Text color="secondary">{value.voiceSpeechRate.toFixed(1)}×</Text>
                </Flex>
                <Slider
                    min={0.5}
                    max={2}
                    step={0.1}
                    value={value.voiceSpeechRate}
                    aria-label={t('speechRate')}
                    onUpdate={(voiceSpeechRate) => onUpdate({...value, voiceSpeechRate})}
                />
            </Flex>
            <SpeechPreview
                modelId={value.voiceSynthesisModel}
                speed={value.voiceSpeechRate}
                ready={speechReady(speech.state?.assets ?? [], value.voiceSynthesisModel)}
            />
            <SpeechToolbar
                onRefresh={reload}
                onBrowse={desktop && storageOpenable ? browse : undefined}
                browseHint={desktop ? t('folderNotReady') : t('desktopOnly')}
                onImport={() => setImportExpanded((current) => !current)}
                importExpanded={importExpanded}
                importControls={importId}
                busy={browsing}
            />
            {storageError && (
                <Text color="danger" role="alert">
                    {storageError}
                </Text>
            )}
            <Box id={importId} hidden={!importExpanded} role="region" aria-label={t('importModel')}>
                <Text variant="subheader-2">{t('importModel')}</Text>
                <SpeechModelImport onImported={imported} />
            </Box>
            <SpeechAssets {...speech} />
        </Flex>
    );
}
