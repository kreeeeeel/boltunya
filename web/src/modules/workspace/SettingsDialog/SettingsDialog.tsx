import {Box, Dialog, Flex, Select, Slider, Switch, Text} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import {useState} from 'react';

import {api, dataOrThrow, errorMessage} from '@/shared/api/client';
import type {AppState} from '@/shared/api/client';
import styles from '@/modules/workspace/SettingsDialog/SettingsDialog.module.css';
import {desktopBridge} from '@/shared/desktop/bridge';
import {CachePanel} from '@/modules/workspace/CachePanel/CachePanel';
import {SearchConnectionPanel} from '@/modules/workspace/SearchConnectionPanel';
import {SaveDialogFooter} from '@/shared/ui/SaveDialogFooter/SaveDialogFooter';
import {useSaveFeedback} from '@/shared/ui/SaveDialogFooter/useSaveFeedback';
import {StorageLocations} from '@/modules/storage/StorageLocations/StorageLocations';
import {ComputeDevicePanel} from '@/modules/workspace/ComputeDevicePanel/ComputeDevicePanel';

export function SettingsDialog({
    settings,
    onClose,
    onRefresh,
}: {
    readonly settings: AppState['settings'];
    readonly onClose: () => void;
    readonly onRefresh: () => Promise<void>;
}) {
    const t = useTranslations();
    const [value, setValue] = useState({
        replyDelaySeconds: settings.replyDelaySeconds ?? 3,
        replyIntervalSeconds: settings.replyIntervalSeconds ?? 3,
        showTyping: settings.showTyping ?? true,
        ignoreAssistantTasks: settings.ignoreAssistantTasks ?? true,
        memoryEnabled: settings.memoryEnabled ?? true,
        memoryIntervalHours: settings.memoryIntervalHours ?? 6,
        autoDownloadPhotos: settings.autoDownloadPhotos ?? true,
        autoDownloadVideos: settings.autoDownloadVideos ?? false,
        autoDownloadAudio: settings.autoDownloadAudio ?? false,
        autoDownloadFiles: settings.autoDownloadFiles ?? false,
        autoDownloadMaxMb: settings.autoDownloadMaxMb ?? 20,
        groupReplies: settings.groupReplies ?? 'disabled',
        closeToTray: settings.closeToTray ?? true,
        launchAtLogin: settings.launchAtLogin ?? false,
        resumeOnStartup: settings.resumeOnStartup ?? false,
        ollamaDevice: settings.ollamaDevice ?? 'auto',
    });
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const feedback = useSaveFeedback(value);
    async function save() {
        setBusy(true);
        setError('');
        feedback.clearSaved();
        try {
            dataOrThrow(await api.PATCH('/api/settings', {body: value}));
            await desktopBridge()?.sync();
            await onRefresh();
            feedback.markSaved();
        } catch (cause) {
            setError(errorMessage(cause));
        } finally {
            setBusy(false);
        }
    }
    return (
        <Dialog
            contentOverflow="auto"
            open
            onClose={onClose}
            maxWidth="s"
            fullWidth
            aria-label={t('settings')}
        >
            <Dialog.Header caption={t('settings')} />
            <Dialog.Body>
                <Flex direction="column" gap={5}>
                    <Flex direction="column" gap={4}>
                        <Text variant="subheader-2">{t('replySettings')}</Text>
                        <Text color="secondary">{t('queueHint')}</Text>
                        {(['replyDelaySeconds', 'replyIntervalSeconds'] as const).map((key) => (
                            <Box key={key}>
                                <Flex justifyContent="space-between" className={styles.label}>
                                    <Text>{t(key)}</Text>
                                    <Text color="secondary">{t('seconds', {count: value[key]})}</Text>
                                </Flex>
                                <Slider
                                    min={0}
                                    max={120}
                                    value={value[key]}
                                    aria-label={t(key)}
                                    onUpdate={(next) => setValue({...value, [key]: next})}
                                />
                            </Box>
                        ))}
                        <Switch
                            checked={value.showTyping}
                            onUpdate={(showTyping) => setValue({...value, showTyping})}
                        >
                            {t('showTyping')}
                        </Switch>
                    </Flex>
                    <Box>
                        <Switch
                            checked={value.ignoreAssistantTasks}
                            onUpdate={(ignoreAssistantTasks) => setValue({...value, ignoreAssistantTasks})}
                        >
                            {t('ignoreAssistantTasks')}
                        </Switch>
                        <Text className={styles.hint}>{t('ignoreAssistantTasksHint')}</Text>
                    </Box>
                    <Box>
                        <Text className={styles.label}>{t('groupReplies')}</Text>
                        <Select
                            size="l"
                            width="max"
                            value={[value.groupReplies]}
                            options={['disabled', 'mentions', 'all'].map((mode) => ({
                                value: mode,
                                content: t('groupMode.' + mode),
                            }))}
                            onUpdate={([groupReplies]) =>
                                setValue({
                                    ...value,
                                    groupReplies: groupReplies as NonNullable<
                                        AppState['settings']['groupReplies']
                                    >,
                                })
                            }
                            aria-label={t('groupReplies')}
                        />
                        <Text className={styles.hint}>{t('groupRepliesHint')}</Text>
                    </Box>
                    <Flex direction="column" gap={3} className={styles.section}>
                        <Text variant="subheader-2">{t('memory.title')}</Text>
                        <Switch
                            checked={value.memoryEnabled}
                            onUpdate={(memoryEnabled) => setValue({...value, memoryEnabled})}
                        >
                            {t('memory.enabled')}
                        </Switch>
                        <Text color="secondary">{t('memory.scheduleHint')}</Text>
                        <Select
                            size="l"
                            width="max"
                            disabled={!value.memoryEnabled}
                            value={[String(value.memoryIntervalHours)]}
                            options={Array.from(
                                new Set([1, 3, 6, 12, 24, 48, 168, value.memoryIntervalHours]),
                            )
                                .sort((a, b) => a - b)
                                .map((hours) => ({
                                    value: String(hours),
                                    content: t('memory.hours', {count: hours}),
                                }))}
                            onUpdate={([hours]) => setValue({...value, memoryIntervalHours: Number(hours)})}
                            aria-label={t('memory.interval')}
                        />
                    </Flex>
                    <Flex direction="column" gap={4} className={styles.section}>
                        <Text variant="subheader-2">{t('applicationSettings')}</Text>
                        <Switch
                            checked={value.closeToTray}
                            onUpdate={(closeToTray) => setValue({...value, closeToTray})}
                        >
                            {t('closeToTray')}
                        </Switch>
                        <Switch
                            checked={value.launchAtLogin}
                            onUpdate={(launchAtLogin) => setValue({...value, launchAtLogin})}
                        >
                            {t('launchAtLogin')}
                        </Switch>
                        <Box>
                            <Switch
                                checked={value.resumeOnStartup}
                                onUpdate={(resumeOnStartup) => setValue({...value, resumeOnStartup})}
                            >
                                {t('resumeOnStartup')}
                            </Switch>
                            <Text className={styles.hint}>{t('resumeOnStartupHint')}</Text>
                        </Box>
                    </Flex>
                    <Flex direction="column" gap={3} className={styles.section}>
                        <ComputeDevicePanel
                            value={value.ollamaDevice}
                            savedValue={settings.ollamaDevice ?? 'auto'}
                            onUpdate={(ollamaDevice) => setValue({...value, ollamaDevice})}
                        />
                    </Flex>
                    <Flex direction="column" gap={3} className={styles.section}>
                        <Text variant="subheader-2">{t('storage.autoTitle')}</Text>
                        {(
                            [
                                'autoDownloadPhotos',
                                'autoDownloadVideos',
                                'autoDownloadAudio',
                                'autoDownloadFiles',
                            ] as const
                        ).map((key) => (
                            <Switch
                                key={key}
                                checked={value[key]}
                                onUpdate={(enabled) => setValue({...value, [key]: enabled})}
                            >
                                {t('storage.' + key)}
                            </Switch>
                        ))}
                        <Text>{t('storage.limit')}</Text>
                        <Select
                            width="max"
                            value={[String(value.autoDownloadMaxMb)]}
                            options={Array.from(new Set([5, 10, 20, 50, 100, value.autoDownloadMaxMb]))
                                .sort((a, b) => a - b)
                                .map((size) => ({
                                    value: String(size),
                                    content: t('storage.megabytes', {count: String(size)}),
                                }))}
                            onUpdate={([size]) => setValue({...value, autoDownloadMaxMb: Number(size)})}
                            aria-label={t('storage.limit')}
                        />
                        <Text color="secondary">{t('storage.autoHint')}</Text>
                    </Flex>
                    <Flex direction="column" gap={3} className={styles.section}>
                        <CachePanel />
                    </Flex>
                    <Flex direction="column" gap={3} className={styles.section}>
                        <StorageLocations />
                    </Flex>
                    <Flex direction="column" gap={3} className={styles.section}>
                        <SearchConnectionPanel />
                    </Flex>
                    {error && <Text color="danger">{error}</Text>}
                </Flex>
            </Dialog.Body>
            <SaveDialogFooter
                saved={feedback.saved}
                saveLabel={t('save')}
                cancelLabel={t('cancel')}
                onSave={() => void save()}
                onClose={onClose}
                busy={busy}
            />
        </Dialog>
    );
}
