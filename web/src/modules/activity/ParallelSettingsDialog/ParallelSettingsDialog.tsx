import {Box, Dialog, Flex, Label, RadioGroup, Text} from '@gravity-ui/uikit';
import {useState} from 'react';
import {useTranslations} from 'next-intl';
import {api, errorMessage} from '@/shared/api/client';
import {SaveDialogFooter} from '@/shared/ui/SaveDialogFooter/SaveDialogFooter';
import {useSaveFeedback} from '@/shared/ui/SaveDialogFooter/useSaveFeedback';
import styles from '@/modules/activity/ParallelSettingsDialog/ParallelSettingsDialog.module.css';
export function ParallelSettingsDialog({
    value,
    onClose,
    onRefresh,
}: {
    readonly value: number;
    readonly onClose: () => void;
    readonly onRefresh: () => Promise<void>;
}) {
    const t = useTranslations('events.parallel');
    const common = useTranslations();
    const [count, setCount] = useState(value);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [warning, setWarning] = useState('');
    const feedback = useSaveFeedback(count);
    async function save() {
        setBusy(true);
        setError('');
        setWarning('');
        feedback.clearSaved();
        try {
            await api.settings.settings({
                settingsUpdate: {maxParallelTasks: count},
            });
            feedback.markSaved();
            void onRefresh().catch(() => setWarning(common('settingsCenter.refreshWarning')));
        } catch (cause) {
            setError(errorMessage(cause));
        } finally {
            setBusy(false);
        }
    }
    const load = ['light', 'medium', 'high', 'veryHigh'][count - 1] ?? 'light';
    return (
        <Dialog open onClose={onClose} maxWidth="s" fullWidth contentOverflow="auto" aria-label={t('title')}>
            <Dialog.Header caption={t('title')} />
            <Dialog.Body>
                <Flex direction="column" gap={5}>
                    <Text color="secondary">{t('description')}</Text>
                    <Text variant="subheader-2">{t('limit')}</Text>
                    <RadioGroup
                        size="l"
                        direction="horizontal"
                        value={String(count)}
                        onUpdate={(value) => setCount(Number(value))}
                        options={[1, 2, 3, 4].map((value) => ({
                            value: String(value),
                            content: String(value),
                        }))}
                        aria-label={t('limit')}
                    />
                    <Text color="secondary">{t('gpuHint')}</Text>
                    {error && (
                        <Text color="danger" role="alert">
                            {error}
                        </Text>
                    )}
                    {warning && (
                        <Text color="warning" role="alert">
                            {warning}
                        </Text>
                    )}
                </Flex>
            </Dialog.Body>
            <Box className={styles.resources}>
                <Flex gap={2} alignItems="center" wrap>
                    <Text>{t('load')}</Text>
                    <Label
                        theme={
                            count >= 4 ? 'danger' : count === 3 ? 'warning' : count === 2 ? 'info' : 'normal'
                        }
                    >
                        {t('levels.' + load)}
                    </Label>
                </Flex>
                <Text color="secondary">{t('estimateHint')}</Text>
            </Box>
            <SaveDialogFooter
                saved={feedback.saved}
                busy={busy}
                saveLabel={common('save')}
                cancelLabel={common('close')}
                onSave={() => void save()}
                onClose={onClose}
            />
        </Dialog>
    );
}
