import {Check, Xmark} from '@gravity-ui/icons';
import {Box, Button, Dialog, Flex, Icon, Select, Text} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import {useState} from 'react';
import {api, dataOrThrow, errorMessage} from '@/shared/api/client';
import type {AppState, Engine} from '@/shared/api/client';
import {desktopBridge} from '@/shared/desktop/bridge';
import {useSaveFeedback} from '@/shared/ui/SaveDialogFooter/useSaveFeedback';
import {SettingsNavigation} from '@/modules/settings/SettingsNavigation/SettingsNavigation';
import {SettingsSection} from '@/modules/settings/SettingsSection/SettingsSection';
import {settingsSections} from '@/modules/settings/model/settingsSections';
import type {SettingsSectionId} from '@/modules/settings/model/settingsSections';
import {createSettingsDraft} from '@/modules/settings/model/settingsDraft';
import {ApplicationSettings} from '@/modules/settings/ApplicationSettings/ApplicationSettings';
import {ReplySettings} from '@/modules/settings/ReplySettings/ReplySettings';
import {MediaSettings} from '@/modules/settings/MediaSettings/MediaSettings';
import {PromptSettings} from '@/modules/settings/PromptSettings/PromptSettings';
import {LanguageSettings} from '@/modules/settings/LanguageSettings/LanguageSettings';
import {StylesEditor} from '@/modules/settings/StylesEditor/StylesEditor';
import {MemorySettings} from '@/modules/settings/MemorySettings/MemorySettings';
import {MemoryCollectionSettings} from '@/modules/settings/MemoryCollectionSettings/MemoryCollectionSettings';
import {AppearanceSettings} from '@/modules/settings/AppearanceSettings/AppearanceSettings';
import {ProviderProfiles} from '@/modules/settings/ProviderProfiles/ProviderProfiles';
import {ModelRoutingSettings} from '@/modules/settings/ModelRoutingSettings/ModelRoutingSettings';
import {CachePanel} from '@/modules/workspace/CachePanel/CachePanel';
import {SearchConnectionPanel} from '@/modules/workspace/SearchConnectionPanel';
import {StorageLocations} from '@/modules/storage/StorageLocations/StorageLocations';
import {ComputeDevicePanel} from '@/modules/workspace/ComputeDevicePanel/ComputeDevicePanel';
import styles from '@/modules/settings/SettingsCenter/SettingsCenter.module.css';

export function SettingsCenter({
    settings,
    engine,
    initialSection = 'application',
    onClose,
    onRefresh,
}: {
    readonly settings: AppState['settings'];
    readonly engine?: Engine;
    readonly initialSection?: SettingsSectionId;
    readonly onClose: () => void;
    readonly onRefresh: () => Promise<void>;
}) {
    const t = useTranslations();
    const [section, setSection] = useState<SettingsSectionId>(initialSection);
    const [visited, setVisited] = useState<SettingsSectionId[]>([initialSection]);
    const [value, setValue] = useState(() => createSettingsDraft(settings));
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [warning, setWarning] = useState('');
    const feedback = useSaveFeedback(value);
    function select(next: SettingsSectionId) {
        setSection(next);
        setVisited((previous) => (previous.includes(next) ? previous : [...previous, next]));
    }
    async function save() {
        if (
            value.customStyles.some(
                (style) => !style.name.trim() || !style.emoji.trim() || !style.instructions.trim(),
            )
        ) {
            setError(t('styleRequired'));
            select('styles');
            return;
        }
        setBusy(true);
        setError('');
        setWarning('');
        feedback.clearSaved();
        try {
            dataOrThrow(await api.PATCH('/api/settings', {body: value}));
            feedback.markSaved();
            void onRefresh().catch(() => setWarning(t('settingsCenter.refreshWarning')));
            const desktop = desktopBridge();
            if (desktop) {
                void desktop.sync().catch(() => setWarning(t('settingsCenter.refreshWarning')));
            }
        } catch (cause) {
            setError(errorMessage(cause));
        } finally {
            setBusy(false);
        }
    }
    const common = {value, onUpdate: setValue};
    const actions = (
        <>
            <Text color="secondary" className={styles.saveHint}>
                {t('settingsCenter.saveHint')}
            </Text>
            <Button size="l" view="flat-secondary" onClick={onClose}>
                {t('cancel')}
            </Button>
            <Button
                size="l"
                view="action"
                className={styles.save}
                loading={busy}
                onClick={() => void save()}
                aria-label={t(feedback.saved ? 'saved' : 'save')}
            >
                {feedback.saved && <Icon data={Check} size={18} />}
                <Text role={feedback.saved ? 'status' : undefined}>
                    {t(feedback.saved ? 'saved' : 'save')}
                </Text>
            </Button>
        </>
    );
    function content(id: SettingsSectionId) {
        if (id === 'memory') {
            return <MemorySettings />;
        }
        if (id === 'appearance') {
            return <AppearanceSettings />;
        }
        if (id === 'providers') {
            return <ProviderProfiles onRefresh={onRefresh} />;
        }
        if (id === 'routing') {
            return <ModelRoutingSettings engine={engine} />;
        }
        return (
            <SettingsSection
                id={id}
                actions={
                    ['application', 'replies', 'prompt', 'styles', 'compute', 'media', 'collection'].includes(
                        id,
                    )
                        ? actions
                        : undefined
                }
            >
                {id === 'application' && <ApplicationSettings {...common} />}
                {id === 'replies' && <ReplySettings {...common} />}
                {id === 'prompt' && (
                    <PromptSettings
                        {...common}
                        engine={engine}
                        defaultModel={settings.defaultModel}
                        defaultProfileId={settings.defaultModelProfileId ?? ''}
                    />
                )}
                {id === 'styles' && (
                    <StylesEditor
                        value={value.customStyles}
                        onUpdate={(customStyles) => setValue({...value, customStyles})}
                    />
                )}
                {id === 'compute' && (
                    <ComputeDevicePanel
                        value={value.ollamaDevice}
                        savedValue={settings.ollamaDevice ?? 'auto'}
                        onUpdate={(ollamaDevice) => setValue({...value, ollamaDevice})}
                    />
                )}
                {id === 'media' && <MediaSettings {...common} />}
                {id === 'collection' && <MemoryCollectionSettings {...common} />}
                {id === 'language' && <LanguageSettings />}
                {id === 'search' && <SearchConnectionPanel />}
                {id === 'cache' && <CachePanel />}
                {id === 'storage' && <StorageLocations />}
                {error && id === section && (
                    <Text color="danger" role="alert">
                        {error}
                    </Text>
                )}
                {warning && id === section && (
                    <Text color="warning" role="alert">
                        {warning}
                    </Text>
                )}
            </SettingsSection>
        );
    }
    return (
        <Dialog
            open
            contentOverflow="visible"
            onClose={onClose}
            hasCloseButton={false}
            modalClassName={styles.modal}
            className={styles.dialog}
            aria-label={t('settings')}
            qa="settings-center"
        >
            <Flex className={styles.shell}>
                <Flex direction="column" className={styles.sidebar}>
                    <SettingsNavigation selected={section} onSelect={select} />
                </Flex>
                <Flex direction="column" className={styles.main}>
                    <Flex className={styles.mobileHeading} alignItems="center" gap={3}>
                        <Text variant="subheader-3">{t('settings')}</Text>
                        <Select
                            size="l"
                            width="max"
                            value={[section]}
                            options={settingsSections.map((id) => ({
                                value: id,
                                content: t('settingsCenter.sections.' + id),
                            }))}
                            onUpdate={([id]) => select(id as SettingsSectionId)}
                            aria-label={t('settingsCenter.navigation')}
                        />
                    </Flex>
                    {visited.map((id) => (
                        <Box
                            key={id}
                            hidden={id !== section}
                            className={styles.panel}
                            role="region"
                            aria-label={t('settingsCenter.sections.' + id)}
                        >
                            {content(id)}
                        </Box>
                    ))}
                </Flex>
                <Button
                    view="flat-secondary"
                    size="l"
                    className={styles.close}
                    onClick={onClose}
                    aria-label={t('close')}
                >
                    <Icon data={Xmark} size={21} />
                </Button>
            </Flex>
        </Dialog>
    );
}
