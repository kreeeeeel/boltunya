import {Cpu, Microphone, Volume} from '@gravity-ui/icons';
import {Box, Flex, Icon, Select, Slider, Switch, Text} from '@gravity-ui/uikit';
import {useEffect, useId, useRef, useState} from 'react';
import type {SpeechAsset} from '@aigram/api-client';
import {useTranslations} from 'next-intl';
import type {SettingsDraftSectionProps} from '@/modules/settings/model/SettingsDraftSectionProps';
import {SpeechAssets} from '@/modules/speech/SpeechAssets/SpeechAssets';
import {useSpeechState} from '@/modules/speech/model/useSpeechState';
import {speechReady} from '@/modules/speech/model/speechReady';
import {SpeechPreview} from '@/modules/speech/SpeechPreview/SpeechPreview';
import type {SpeechPreviewHandle} from '@/modules/speech/SpeechPreview/SpeechPreview';
import {SpeechToolbar} from '@/modules/speech/SpeechToolbar/SpeechToolbar';
import {SpeechModelImport} from '@/modules/speech/SpeechModelImport/SpeechModelImport';
import {api, errorMessage} from '@/shared/api/client';
import {desktopBridge} from '@/shared/desktop/bridge';
import {SegmentedTabs} from '@/shared/ui/SegmentedTabs/SegmentedTabs';

export function VoiceSettings({value, onUpdate}: SettingsDraftSectionProps) {
    const t = useTranslations('voice');
    const speech = useSpeechState();
    const [kind, setKind] = useState<SpeechAsset['kind']>('synthesis');
    const preview = useRef<SpeechPreviewHandle | null>(null);
    const previewContainer = useRef<HTMLDivElement | null>(null);
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

    function selectAsset(asset: SpeechAsset) {
        if (asset.kind === 'recognition') {
            onUpdate({...value, voiceRecognitionModel: asset.id});
        } else if (asset.kind === 'synthesis') {
            onUpdate({...value, voiceSynthesisModel: asset.id});
        }
    }

    function previewAsset(asset: SpeechAsset) {
        selectAsset(asset);
        previewContainer.current?.scrollIntoView({block: 'center', behavior: 'smooth'});
        preview.current?.check(asset.id);
    }

    return (
        <Flex direction="column" gap={5}>
            <SegmentedTabs
                label={t('catalogTabs')}
                value={kind}
                items={[
                    {value: 'runtime', label: t('tabs.runtime'), icon: <Icon data={Cpu} size={16} />},
                    {
                        value: 'recognition',
                        label: t('tabs.recognition'),
                        icon: <Icon data={Microphone} size={16} />,
                    },
                    {value: 'synthesis', label: t('tabs.synthesis'), icon: <Icon data={Volume} size={16} />},
                ]}
                onUpdate={(next) => {
                    if (next === 'runtime' || next === 'recognition' || next === 'synthesis') {
                        setKind(next);
                    }
                }}
            />
            {kind === 'recognition' && (
                <Flex direction="column" gap={2}>
                    <Switch
                        checked={value.voiceInputEnabled}
                        onUpdate={(voiceInputEnabled) => onUpdate({...value, voiceInputEnabled})}
                    >
                        {t('recognitionEnabled')}
                    </Switch>
                    <Text color="secondary">{t('recognitionHint')}</Text>
                </Flex>
            )}
            {(['recognition', 'synthesis'] as const)
                .filter((candidate) => candidate === kind)
                .map((kind) => {
                    const key = kind === 'recognition' ? 'voiceRecognitionModel' : 'voiceSynthesisModel';
                    const selected = value[key];
                    const assets = speech.state?.assets.filter((asset) => asset.kind === kind) ?? [];
                    return (
                        <Flex key={kind} direction="column" gap={2}>
                            <Text>{t('models.' + kind)}</Text>
                            <Select
                                size="l"
                                filterable
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
            {kind === 'synthesis' && (
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
            )}
            {kind === 'synthesis' && (
                <Box ref={previewContainer}>
                    <SpeechPreview
                        ref={preview}
                        modelId={value.voiceSynthesisModel}
                        speed={value.voiceSpeechRate}
                        ready={speechReady(speech.state?.assets ?? [], value.voiceSynthesisModel)}
                        assets={speech.state?.assets}
                    />
                </Box>
            )}
            <SpeechToolbar
                onRefresh={reload}
                onBrowse={desktop && storageOpenable ? browse : undefined}
                browseHint={desktop ? t('folderNotReady') : t('desktopOnly')}
                onImport={kind === 'synthesis' ? () => setImportExpanded((current) => !current) : undefined}
                importExpanded={importExpanded}
                importControls={importId}
                busy={browsing}
            />
            {storageError && (
                <Text color="danger" role="alert">
                    {storageError}
                </Text>
            )}
            <Box
                id={importId}
                hidden={!importExpanded || kind !== 'synthesis'}
                role="region"
                aria-label={t('importModel')}
            >
                <Text variant="subheader-2">{t('importModel')}</Text>
                <SpeechModelImport onImported={imported} />
            </Box>
            <SpeechAssets
                {...speech}
                kind={kind}
                selected={{recognition: value.voiceRecognitionModel, synthesis: value.voiceSynthesisModel}}
                onSelect={selectAsset}
                onPreview={previewAsset}
            />
        </Flex>
    );
}
