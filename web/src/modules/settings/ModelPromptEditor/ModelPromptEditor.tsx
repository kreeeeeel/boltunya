import {Check} from '@gravity-ui/icons';
import {Button, Flex, Icon, Select, Skeleton, Text, TextArea, TextInput} from '@gravity-ui/uikit';
import {useEffect, useState} from 'react';
import {useTranslations} from 'next-intl';
import {api, defaultRule, errorMessage} from '@/shared/api/client';
import type {Engine} from '@/shared/api/client';
import type * as Contract from '@aigram/api-client';
import {useModelProfiles} from '@/modules/settings/model/useModelProfiles';
import {PromptBudgetNotice} from '@/modules/settings/PromptBudgetNotice/PromptBudgetNotice';
import {useSaveFeedback} from '@/shared/ui/SaveDialogFooter/useSaveFeedback';
import styles from '@/modules/settings/ModelPromptEditor/ModelPromptEditor.module.css';
type ModelPrompt = Contract.ModelPromptSettings;
const modelKey = (value: Pick<ModelPrompt, 'model' | 'profileId'>) =>
    JSON.stringify([value.profileId, value.model]);
export function ModelPromptEditor({
    engine,
    defaultModel,
    defaultProfileId,
    globalPrompt,
}: {
    readonly engine?: Engine;
    readonly defaultModel: string;
    readonly defaultProfileId: string;
    readonly globalPrompt: string;
}) {
    const t = useTranslations('modelPrompts');
    const common = useTranslations();
    const profiles = useModelProfiles();
    const [selected, setSelected] = useState(
        defaultProfileId ? 'profile:' + defaultProfileId : 'local:' + defaultModel,
    );
    const [drafts, setDrafts] = useState<Record<string, ModelPrompt>>({});
    const [loaded, setLoaded] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const profileId = selected.startsWith('profile:') ? selected.slice(8) : '';
    const profile = profiles.profiles.find((item) => item.id === profileId);
    const model = profileId ? (profile?.model ?? defaultModel) : selected.slice(6);
    const preferenceKey = modelKey({model, profileId});
    const value: ModelPrompt = {
        ...(drafts[preferenceKey] ?? {
            model,
            profileId,
            instructions: '',
            contextWindow: null,
            tokenEncoding: 'auto',
        }),
        model,
        profileId,
    };
    const feedback = useSaveFeedback(JSON.stringify(value));
    useEffect(() => {
        const controller = new AbortController();
        void api.models
            .getModelPrompts({
                signal: controller.signal,
            })
            .then((report) => {
                if (!controller.signal.aborted) {
                    setDrafts(Object.fromEntries(report.prompts.map((prompt) => [modelKey(prompt), prompt])));
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
    function update(next: Partial<ModelPrompt>) {
        setDrafts({...drafts, [preferenceKey]: {...value, ...next}});
    }
    async function save() {
        setBusy(true);
        setError('');
        try {
            const saved = await api.models.saveModelPrompts({
                modelPromptSettings: {...value, contextWindow: profileId ? null : value.contextWindow},
            });
            setDrafts((previous) => ({...previous, [modelKey(saved)]: saved}));
            feedback.markValueSaved(JSON.stringify(saved));
        } catch (cause) {
            setError(errorMessage(cause));
        } finally {
            setBusy(false);
        }
    }
    const localModels = engine?.models ?? [{id: defaultModel, name: defaultModel}];
    return (
        <Flex direction="column" gap={4} className={styles.root}>
            <Text variant="subheader-2">{t('title')}</Text>
            <Text color="secondary">{t('hint')}</Text>
            <Select
                size="l"
                width="max"
                value={[selected]}
                options={[
                    ...localModels.map((item) => ({value: 'local:' + item.id, content: item.name})),
                    ...profiles.profiles
                        .filter((item) => item.enabled)
                        .map((item) => ({value: 'profile:' + item.id, content: item.name + ' · API'})),
                ]}
                onUpdate={([next]) => setSelected(next)}
                aria-label={t('model')}
            />
            {!loaded ? (
                !error && <Skeleton height={200} />
            ) : (
                <>
                    <Flex direction="column" gap={2}>
                        <Text>{t('instructions')}</Text>
                        <TextArea
                            value={value.instructions}
                            onUpdate={(instructions) => update({instructions})}
                            minRows={5}
                            maxRows={12}
                            controlProps={{'aria-label': t('instructions'), maxLength: 20000}}
                        />
                    </Flex>
                    <Flex direction="column" gap={2}>
                        <Text>{t('contextWindow')}</Text>
                        <TextInput
                            type="number"
                            size="l"
                            disabled={!!profileId}
                            value={String(
                                profileId
                                    ? (profile?.options.contextWindow ?? '')
                                    : (value.contextWindow ?? ''),
                            )}
                            onUpdate={(next) => update({contextWindow: next ? Number(next) : null})}
                            controlProps={{'aria-label': t('contextWindow'), min: 256, step: 256}}
                        />
                        <Text color="secondary">{t(profileId ? 'apiContextHint' : 'contextHint')}</Text>
                    </Flex>
                    <Flex direction="column" gap={2}>
                        <Text>{t('encoding')}</Text>
                        <Select
                            size="l"
                            width="max"
                            value={[value.tokenEncoding ?? 'auto']}
                            options={['auto', 'cl100k', 'o200k'].map((encoding) => ({
                                value: encoding,
                                content: encoding === 'auto' ? t('auto') : encoding,
                            }))}
                            onUpdate={([tokenEncoding]) =>
                                update({tokenEncoding: tokenEncoding as ModelPrompt['tokenEncoding']})
                            }
                            aria-label={t('encoding')}
                        />
                    </Flex>
                    <PromptBudgetNotice
                        request={{
                            globalPrompt,
                            rule: {...defaultRule, model, modelProfileId: profileId, useDefaultModel: false},
                            preferences: value,
                        }}
                    />
                    <Button
                        view="action"
                        size="l"
                        className={styles.save}
                        loading={busy}
                        disabled={
                            !!value.contextWindow &&
                            (!Number.isInteger(value.contextWindow) || value.contextWindow < 256)
                        }
                        onClick={() => void save()}
                        aria-label={feedback.saved ? common('saved') : t('save')}
                    >
                        {feedback.saved && <Icon data={Check} />}
                        <Text role={feedback.saved ? 'status' : undefined}>
                            {feedback.saved ? common('saved') : t('save')}
                        </Text>
                    </Button>
                </>
            )}
            {(error || profiles.error) && (
                <Text role="alert" color="danger">
                    {error || profiles.error}
                </Text>
            )}
        </Flex>
    );
}
