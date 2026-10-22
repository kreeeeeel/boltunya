import {Button, Flex, Icon, Loader, Select, Switch, Text} from '@gravity-ui/uikit';
import {Check} from '@gravity-ui/icons';
import {useEffect, useState} from 'react';
import {useTranslations} from 'next-intl';
import {api, errorMessage} from '@/shared/api/client';
import type * as Contract from '@aigram/api-client';
import {SettingsSection} from '@/modules/settings/SettingsSection/SettingsSection';
import {useModelProfiles} from '@/modules/settings/model/useModelProfiles';
import {useSaveFeedback} from '@/shared/ui/SaveDialogFooter/useSaveFeedback';
import type {Engine} from '@/shared/api/client';
const tasks = [
    {profile: 'replyProfileId', model: 'replyModel'},
    {profile: 'memoryProfileId', model: 'memoryModel'},
    {profile: 'visionProfileId', model: 'visionModel'},
] as const;
export function ModelRoutingSettings({engine}: {readonly engine?: Engine}) {
    const t = useTranslations('modelRouting');
    const common = useTranslations();
    const report = useModelProfiles();
    const [value, setValue] = useState<Contract.ModelTaskRouting | null>(null);
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);
    const feedback = useSaveFeedback(value);
    useEffect(() => {
        void api.models
            .modelRouting()
            .then((result) => setValue(result))
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
            const saved = await api.models.saveModelRouting({
                modelTaskRouting: value,
            });
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
                    <Text color="secondary">{t('localHint')}</Text>
                    {tasks.map((task) => {
                        const selectedProfile = value[task.profile];
                        const selectedModel = value[task.model] ?? '';
                        const selected = selectedProfile
                            ? 'profile:' + selectedProfile
                            : selectedModel
                              ? 'local:' + selectedModel
                              : '';
                        const options = [
                            {value: '', content: t('primary')},
                            ...(engine?.models ?? [])
                                .filter(
                                    (model) =>
                                        model.installed &&
                                        (task.model !== 'visionModel' || model.supportsVision),
                                )
                                .map((model) => ({
                                    value: 'local:' + model.id,
                                    content: t('localOption', {name: model.name}),
                                })),
                            ...report.profiles
                                .filter(
                                    (profile) =>
                                        profile.enabled &&
                                        (task.profile !== 'visionProfileId' || profile.capabilities.vision),
                                )
                                .map((profile) => ({
                                    value: 'profile:' + profile.id,
                                    content: profile.name + ' · ' + profile.model,
                                })),
                        ];
                        if (selected && !options.some((option) => option.value === selected)) {
                            options.push({
                                value: selected,
                                content: t('unavailable', {name: selectedModel || selectedProfile}),
                            });
                        }
                        return (
                            <Flex key={task.profile} direction="column" gap={2}>
                                <Text variant="subheader-2">{t('tasks.' + task.profile)}</Text>
                                <Select
                                    size="l"
                                    width="max"
                                    disabled={!value.enabled}
                                    value={[selected]}
                                    options={options}
                                    filterable
                                    onUpdate={([selection = '']) =>
                                        setValue({
                                            ...value,
                                            [task.profile]: selection.startsWith('profile:')
                                                ? selection.slice(8)
                                                : '',
                                            [task.model]: selection.startsWith('local:')
                                                ? selection.slice(6)
                                                : '',
                                        })
                                    }
                                    aria-label={t('tasks.' + task.profile)}
                                />
                            </Flex>
                        );
                    })}
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
