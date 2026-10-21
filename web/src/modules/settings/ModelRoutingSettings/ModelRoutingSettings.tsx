import {Button, Flex, Icon, Loader, Select, Switch, Text} from '@gravity-ui/uikit';
import {Check} from '@gravity-ui/icons';
import {useEffect, useState} from 'react';
import {useTranslations} from 'next-intl';
import {api, dataOrThrow, errorMessage} from '@/shared/api/client';
import type {components} from '@/shared/api/schema';
import {SettingsSection} from '@/modules/settings/SettingsSection/SettingsSection';
import {useModelProfiles} from '@/modules/settings/model/useModelProfiles';
import {useSaveFeedback} from '@/shared/ui/SaveDialogFooter/useSaveFeedback';
const tasks = ['replyProfileId', 'memoryProfileId', 'researchProfileId', 'visionProfileId'] as const;
export function ModelRoutingSettings() {
    const t = useTranslations('modelRouting');
    const common = useTranslations();
    const report = useModelProfiles();
    const [value, setValue] = useState<components['schemas']['ModelTaskRouting'] | null>(null);
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);
    const feedback = useSaveFeedback(value);
    useEffect(() => {
        void api
            .GET('/api/model-routing')
            .then((result) => setValue(dataOrThrow(result)))
            .catch((cause) => setError(errorMessage(cause)));
    }, []);
    async function save() {
        if (!value) {
            return;
        }
        setBusy(true);
        setError('');
        feedback.clearSaved();
        try {
            const saved = dataOrThrow(await api.PUT('/api/model-routing', {body: value}));
            setValue(saved);
            feedback.markValueSaved(saved);
        } catch (cause) {
            setError(errorMessage(cause));
        } finally {
            setBusy(false);
        }
    }
    return (
        <SettingsSection
            id="routing"
            actions={
                <Button
                    size="l"
                    view="action"
                    disabled={!value}
                    loading={busy}
                    onClick={() => void save()}
                    aria-label={common(feedback.saved ? 'saved' : 'save')}
                >
                    {feedback.saved && <Icon data={Check} />}
                    <Text role={feedback.saved ? 'status' : undefined}>
                        {common(feedback.saved ? 'saved' : 'save')}
                    </Text>
                </Button>
            }
        >
            {(!value || report.loading) && !error && <Loader size="m" />}
            {(error || report.error) && (
                <Text color="danger" role="alert">
                    {error || report.error}
                </Text>
            )}
            {value && (
                <Flex direction="column" gap={5}>
                    <Switch checked={value.enabled} onUpdate={(enabled) => setValue({...value, enabled})}>
                        {t('enabled')}
                    </Switch>
                    <Text color="secondary">{t('priorityHint')}</Text>
                    {tasks.map((task) => (
                        <Flex key={task} direction="column" gap={2}>
                            <Text variant="subheader-2">{t('tasks.' + task)}</Text>
                            <Select
                                size="l"
                                width="max"
                                disabled={!value.enabled}
                                value={[value[task]]}
                                options={[
                                    {value: '', content: t('primary')},
                                    ...report.profiles
                                        .filter(
                                            (profile) =>
                                                profile.enabled &&
                                                (task !== 'visionProfileId' || profile.capabilities.vision),
                                        )
                                        .map((profile) => ({
                                            value: profile.id,
                                            content: profile.name + ' · ' + profile.model,
                                        })),
                                ]}
                                onUpdate={([id]) => setValue({...value, [task]: id})}
                                aria-label={t('tasks.' + task)}
                            />
                        </Flex>
                    ))}
                    <Switch
                        checked={value.parallelIndependentTasks}
                        disabled={!value.enabled}
                        onUpdate={(parallelIndependentTasks) =>
                            setValue({...value, parallelIndependentTasks})
                        }
                    >
                        {t('parallel')}
                    </Switch>
                    <Text color="secondary">{t('parallelHint')}</Text>
                </Flex>
            )}
        </SettingsSection>
    );
}
