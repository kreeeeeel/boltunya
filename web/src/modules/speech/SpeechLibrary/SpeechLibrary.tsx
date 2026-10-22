import {Volume} from '@gravity-ui/icons';
import {Box, Flex, Icon, Text} from '@gravity-ui/uikit';
import type {SpeechAsset} from '@aigram/api-client';
import {useTranslations} from 'next-intl';
import {useId, useRef, useState} from 'react';
import type {AppState} from '@/shared/api/client';
import {api, errorMessage} from '@/shared/api/client';
import {ScrollArea} from '@/shared/ui/ScrollArea/ScrollArea';
import {SegmentedTabs} from '@/shared/ui/SegmentedTabs/SegmentedTabs';
import {SpeechAssets} from '@/modules/speech/SpeechAssets/SpeechAssets';
import {SpeechModelImport} from '@/modules/speech/SpeechModelImport/SpeechModelImport';
import {SpeechLibraryPreview} from '@/modules/speech/SpeechLibraryPreview/SpeechLibraryPreview';
import {SpeechToolbar} from '@/modules/speech/SpeechToolbar/SpeechToolbar';
import {useSpeechState} from '@/modules/speech/model/useSpeechState';
import {useSpeechStorage} from '@/modules/speech/model/useSpeechStorage';
import {voicePreferences} from '@/modules/speech/model/voicePreferences';
import styles from '@/modules/speech/SpeechLibrary/SpeechLibrary.module.css';

export function SpeechLibrary({
    settings,
    onRefresh,
}: {
    readonly settings: AppState['settings'];
    readonly onRefresh: () => Promise<void>;
}) {
    const t = useTranslations('voice');
    const speech = useSpeechState();
    const preferences = voicePreferences(settings);
    const installedAssets =
        speech.state?.assets
            .filter((asset) => asset.installed)
            .map((asset) => asset.id)
            .join(',') ?? '';
    const storage = useSpeechStorage(installedAssets);
    const [kind, setKind] = useState<SpeechAsset['kind']>('synthesis');
    const [importExpanded, setImportExpanded] = useState(false);
    const [selectionError, setSelectionError] = useState('');
    const [selecting, setSelecting] = useState(false);
    const selectionPending = useRef(false);
    const [previewAsset, setPreviewAsset] = useState<SpeechAsset>();
    const importId = useId();

    function reload() {
        speech.reload();
        storage.reload();
    }

    function imported() {
        window.dispatchEvent(new Event('speech-assets-updated'));
        storage.reload();
    }

    async function select(asset: SpeechAsset) {
        if (selectionPending.current) {
            return;
        }
        selectionPending.current = true;
        setSelecting(true);
        setSelectionError('');
        try {
            await api.settings.settings({
                settingsUpdate:
                    asset.kind === 'recognition'
                        ? {voiceRecognitionModel: asset.id}
                        : {voiceSynthesisModel: asset.id},
            });
            await onRefresh();
        } catch (cause) {
            setSelectionError(errorMessage(cause));
        } finally {
            selectionPending.current = false;
            setSelecting(false);
        }
    }

    return (
        <ScrollArea className={styles.root} qa="speech-library">
            <Flex direction="column" gap={5} className={styles.content}>
                <Flex
                    gap={4}
                    alignItems="center"
                    justifyContent="space-between"
                    wrap="wrap"
                    className={styles.header}
                >
                    <Flex alignItems="center" gap={3} className={styles.identity}>
                        <Icon data={Volume} size={23} className={styles.icon} />
                        <Flex direction="column" className={styles.heading}>
                            <Text as="h1" variant="subheader-2" className={styles.title}>
                                {t('libraryTitle')}
                            </Text>
                            <Text color="secondary" className={styles.hint}>
                                {t('libraryHint')}
                            </Text>
                        </Flex>
                    </Flex>
                    <SpeechToolbar
                        onRefresh={reload}
                        onBrowse={storage.browse}
                        browseHint={storage.desktop ? t('folderNotReady') : t('desktopOnly')}
                        onImport={
                            kind === 'synthesis' ? () => setImportExpanded((current) => !current) : undefined
                        }
                        importExpanded={importExpanded}
                        importControls={importId}
                        busy={storage.busy || selecting}
                    />
                </Flex>
                <SegmentedTabs
                    label={t('catalogTabs')}
                    view="panel"
                    value={kind}
                    items={[
                        {value: 'runtime', label: t('tabs.runtime')},
                        {value: 'recognition', label: t('tabs.recognition')},
                        {value: 'synthesis', label: t('tabs.synthesis')},
                    ]}
                    onUpdate={(next) => {
                        if (next === 'runtime' || next === 'recognition' || next === 'synthesis') {
                            setKind(next);
                        }
                    }}
                />
                {(storage.error || selectionError) && (
                    <Text color="danger" role="alert">
                        {storage.error || selectionError}
                    </Text>
                )}
                <Box
                    id={importId}
                    hidden={!importExpanded || kind !== 'synthesis'}
                    role="region"
                    aria-label={t('importModel')}
                    className={styles.import}
                >
                    <SpeechModelImport onImported={imported} />
                </Box>
                <SpeechAssets
                    {...speech}
                    kind={kind}
                    heading={false}
                    selected={{
                        recognition: preferences.voiceRecognitionModel,
                        synthesis: preferences.voiceSynthesisModel,
                    }}
                    selecting={selecting}
                    onSelect={(asset) => void select(asset)}
                    onPreview={setPreviewAsset}
                />
            </Flex>
            {previewAsset && (
                <SpeechLibraryPreview
                    key={previewAsset.id}
                    asset={previewAsset}
                    assets={speech.state?.assets ?? []}
                    speed={preferences.voiceSpeechRate}
                    onClose={() => setPreviewAsset(undefined)}
                />
            )}
        </ScrollArea>
    );
}
