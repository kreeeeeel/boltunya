import {Dialog, Flex, Skeleton, Text, TextInput} from '@gravity-ui/uikit';
import {useEffect, useState} from 'react';
import {useTranslations} from 'next-intl';
import {api, dataOrThrow, errorMessage} from '@/shared/api/client';
import {SaveDialogFooter} from '@/shared/ui/SaveDialogFooter/SaveDialogFooter';
import {useSaveFeedback} from '@/shared/ui/SaveDialogFooter/useSaveFeedback';

export function HistorySettingsDialog({
    onClose,
    onRefresh,
}: {
    readonly onClose: () => void;
    readonly onRefresh: () => Promise<void>;
}) {
    const t = useTranslations('events.archive');
    const common = useTranslations();
    const [value, setValue] = useState({retentionDays: 0, maxStorageMb: 0});
    const [loaded, setLoaded] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const feedback = useSaveFeedback(value);
    useEffect(() => {
        const controller = new AbortController();
        void api
            .GET('/api/activity/history/settings', {signal: controller.signal})
            .then(dataOrThrow)
            .then((result) => {
                if (!controller.signal.aborted) {
                    setValue({retentionDays: result.retentionDays, maxStorageMb: result.maxStorageMb});
                    setLoaded(true);
                }
            })
            .catch((cause) => {
                if (!controller.signal.aborted) {
                    setError(errorMessage(cause));
                }
            });
        return () => controller.abort();
    }, []);
    async function save() {
        setBusy(true);
        setError('');
        try {
            dataOrThrow(await api.PUT('/api/activity/history/settings', {body: value}));
            feedback.markSaved();
            void onRefresh().catch(() => setError(common('settingsCenter.refreshWarning')));
        } catch (cause) {
            setError(errorMessage(cause));
        } finally {
            setBusy(false);
        }
    }
    return (
        <Dialog open onClose={onClose} maxWidth="s" fullWidth contentOverflow="auto" aria-label={t('title')}>
            <Dialog.Header caption={t('title')} />
            <Dialog.Body>
                <Flex direction="column" gap={4}>
                    <Text color="secondary">{t('hint')}</Text>
                    {loaded ? (
                        <>
                            {(['retentionDays', 'maxStorageMb'] as const).map((field) => (
                                <Flex key={field} direction="column" gap={2}>
                                    <Text>{t(field)}</Text>
                                    <TextInput
                                        type="number"
                                        size="l"
                                        value={String(value[field])}
                                        onUpdate={(next) => setValue({...value, [field]: Number(next)})}
                                        controlProps={{'aria-label': t(field), min: 0, step: 1}}
                                    />
                                </Flex>
                            ))}
                            <Text color="secondary">{t('zero')}</Text>
                        </>
                    ) : (
                        !error && <Skeleton height={140} />
                    )}
                    {error && (
                        <Text role="alert" color="danger">
                            {error}
                        </Text>
                    )}
                </Flex>
            </Dialog.Body>
            <SaveDialogFooter
                saved={feedback.saved}
                busy={busy}
                disabled={
                    !loaded || Object.values(value).some((number) => !Number.isInteger(number) || number < 0)
                }
                saveLabel={common('save')}
                cancelLabel={common('close')}
                onSave={() => void save()}
                onClose={onClose}
            />
        </Dialog>
    );
}
