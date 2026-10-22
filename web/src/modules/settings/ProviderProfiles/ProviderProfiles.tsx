import {Box, Button, Flex, Icon, Label, Loader, Select, Switch, Text, TextInput} from '@gravity-ui/uikit';
import {ArrowLeft, Check, Plus, TrashBin} from '@gravity-ui/icons';
import {useTranslations} from 'next-intl';
import {useState} from 'react';
import {api, errorMessage} from '@/shared/api/client';
import type * as Contract from '@aigram/api-client';
import {SettingsSection} from '@/modules/settings/SettingsSection/SettingsSection';
import {ProfileOptions} from '@/modules/settings/ProfileOptions/ProfileOptions';
import {useModelProfiles} from '@/modules/settings/model/useModelProfiles';
import type {ModelProfileDraft} from '@/modules/settings/model/ModelProfileDraft';
import {profileDraft, profilePayload} from '@/modules/settings/model/profileDraft';
import {useSaveFeedback} from '@/shared/ui/SaveDialogFooter/useSaveFeedback';
import styles from '@/modules/settings/ProviderProfiles/ProviderProfiles.module.css';
export function ProviderProfiles({onRefresh}: {readonly onRefresh: () => Promise<void>}) {
    const t = useTranslations('modelProfiles');
    const common = useTranslations();
    const report = useModelProfiles();
    const [selected, setSelected] = useState<string | null>(null);
    const [value, setValue] = useState<ModelProfileDraft | null>(null);
    const [key, setKey] = useState('');
    const [error, setError] = useState('');
    const [busy, setBusy] = useState('');
    const [result, setResult] = useState<Contract.ModelProfileTestResult | null>(null);
    const feedback = useSaveFeedback(JSON.stringify([value, key]));
    function edit(id: string) {
        const item = report.profiles.find((profile) => profile.id === id) ?? report.presets[0];
        if (!item) {
            return;
        }
        setSelected(id);
        setValue(profileDraft(item));
        setKey('');
        setError('');
        setResult(null);
    }
    async function save() {
        if (!value) {
            return;
        }
        setBusy('save');
        setError('');
        feedback.clearSaved();
        try {
            const body = profilePayload(value, key);
            const saved =
                selected === 'new'
                    ? await api.models.createModelProfile({
                          modelProfileUpdate: body,
                      })
                    : await api.models.updateModelProfile({
                          id: selected ?? '',
                          modelProfileUpdate: body,
                      });
            setSelected(saved.id);
            const next = profileDraft(saved);
            setValue(next);
            setKey('');
            feedback.markValueSaved(JSON.stringify([next, '']));
            await report.refresh();
            void onRefresh().catch(() => setError(t('refreshFailed')));
        } catch (cause) {
            setError(errorMessage(cause));
        } finally {
            setBusy('');
        }
    }
    async function remove() {
        if (!selected || selected === 'new') {
            return;
        }
        setBusy('delete');
        setError('');
        try {
            await api.models.deleteModelProfile({
                id: selected,
            });
            setSelected(null);
            setValue(null);
            await report.refresh();
            void onRefresh().catch(() => setError(t('refreshFailed')));
        } catch (cause) {
            setError(errorMessage(cause));
        } finally {
            setBusy('');
        }
    }
    async function test() {
        if (!selected || selected === 'new') {
            return;
        }
        setBusy('test');
        setError('');
        setResult(null);
        try {
            setResult(
                await api.models.testModelProfile({
                    id: selected,
                    modelProfileTestRequest: {confirmGeneration: true},
                }),
            );
        } catch (cause) {
            setError(errorMessage(cause));
        } finally {
            setBusy('');
        }
    }
    const savedProfile = report.profiles.find((profile) => profile.id === selected);
    const empty = !value && !report.loading && !report.profiles.length && !report.error && !error;
    if (empty) {
        return (
            <Flex direction="column" className={styles.emptyPage}>
                <Flex
                    direction="column"
                    alignItems="center"
                    gap={4}
                    className={styles.empty}
                    qa="model-connections-empty"
                >
                    <Box
                        as="img"
                        src="/assets/illustrations/model-connections.png"
                        alt=""
                        className={styles.illustration}
                    />
                    <Text variant="header-2">{t('emptyTitle')}</Text>
                    <Text color="secondary" className={styles.description}>
                        {t('emptyDescription')}
                    </Text>
                    <Button
                        size="l"
                        view="action"
                        disabled={!report.presets.length}
                        onClick={() => edit('new')}
                    >
                        <Icon data={Plus} size={18} />
                        {t('create')}
                    </Button>
                </Flex>
            </Flex>
        );
    }
    return (
        <SettingsSection
            id="providers"
            actions={
                value ? (
                    <>
                        <Button
                            size="l"
                            view="flat-secondary"
                            onClick={() => {
                                setValue(null);
                                setSelected(null);
                            }}
                        >
                            <Icon data={ArrowLeft} />
                            {t('back')}
                        </Button>
                        {selected !== 'new' && (
                            <Button
                                size="l"
                                view="flat-danger"
                                disabled={!!busy}
                                onClick={() => void remove()}
                                aria-label={t('delete')}
                            >
                                <Icon data={TrashBin} />
                            </Button>
                        )}
                        <Button
                            size="l"
                            view="action"
                            loading={busy === 'save'}
                            disabled={
                                !!busy || !value.name.trim() || !value.model.trim() || !value.baseUrl.trim()
                            }
                            onClick={() => void save()}
                            aria-label={common(feedback.saved ? 'saved' : 'save')}
                        >
                            {feedback.saved && <Icon data={Check} />}
                            <Text role={feedback.saved ? 'status' : undefined}>
                                {common(feedback.saved ? 'saved' : 'save')}
                            </Text>
                        </Button>
                    </>
                ) : (
                    <Button
                        size="l"
                        view="action"
                        disabled={!report.presets.length}
                        onClick={() => edit('new')}
                    >
                        <Icon data={Plus} />
                        {t('create')}
                    </Button>
                )
            }
        >
            {report.loading && <Loader size="m" />}
            {(error || report.error) && (
                <Text color="danger" role="alert">
                    {error || report.error}
                </Text>
            )}
            {!value && !report.loading && (
                <Flex direction="column" gap={3}>
                    {report.profiles.map((profile) => (
                        <Button
                            key={profile.id}
                            view="outlined"
                            className={styles.profile}
                            onClick={() => edit(profile.id)}
                        >
                            <Flex alignItems="center" justifyContent="space-between" gap={3}>
                                <Flex direction="column" gap={1}>
                                    <Text variant="subheader-2">{profile.name}</Text>
                                    <Text color="secondary">{profile.model}</Text>
                                </Flex>
                                <Label theme={profile.enabled ? 'success' : 'normal'}>
                                    {t(profile.enabled ? 'enabled' : 'disabled')}
                                </Label>
                            </Flex>
                        </Button>
                    ))}
                </Flex>
            )}
            {value && (
                <Flex direction="column" gap={5}>
                    <Flex direction="column" gap={2}>
                        <Text>{t('preset')}</Text>
                        <Select
                            size="l"
                            width="max"
                            value={[value.preset]}
                            options={report.presets.map((preset) => ({
                                value: preset.id,
                                content: preset.name,
                            }))}
                            onUpdate={([id]) => {
                                const preset = report.presets.find((item) => item.id === id);
                                if (preset) {
                                    setValue(profileDraft(preset));
                                    setKey('');
                                }
                            }}
                            aria-label={t('preset')}
                        />
                    </Flex>
                    <Flex direction="column" gap={2}>
                        <Text>{t('name')}</Text>
                        <TextInput
                            size="l"
                            value={value.name}
                            onUpdate={(name) => setValue({...value, name})}
                            controlProps={{'aria-label': t('name'), maxLength: 100}}
                        />
                    </Flex>
                    <Flex direction="column" gap={2}>
                        <Text>{t('baseUrl')}</Text>
                        <TextInput
                            size="l"
                            value={value.baseUrl}
                            onUpdate={(baseUrl) => setValue({...value, baseUrl})}
                            controlProps={{'aria-label': t('baseUrl')}}
                        />
                    </Flex>
                    <Flex direction="column" gap={2}>
                        <Text>{t('model')}</Text>
                        <TextInput
                            size="l"
                            value={value.model}
                            onUpdate={(model) => setValue({...value, model})}
                            controlProps={{'aria-label': t('model')}}
                        />
                    </Flex>
                    {value.provider !== 'ollama' && (
                        <Flex direction="column" gap={2}>
                            <Flex justifyContent="space-between" alignItems="center">
                                <Text>{t('apiKey')}</Text>
                                {savedProfile?.apiKeyConfigured && (
                                    <Label theme="success">{t('keyConfigured')}</Label>
                                )}
                            </Flex>
                            <TextInput
                                size="l"
                                type="password"
                                value={key}
                                onUpdate={setKey}
                                controlProps={{'aria-label': t('apiKey'), autoComplete: 'new-password'}}
                            />
                            <Text color="secondary">{t('keyHint')}</Text>
                            {savedProfile?.apiKeyConfigured && (
                                <Switch
                                    checked={value.clearApiKey}
                                    onUpdate={(clearApiKey) => {
                                        setValue({...value, clearApiKey});
                                        if (clearApiKey) {
                                            setKey('');
                                        }
                                    }}
                                >
                                    {t('clearKey')}
                                </Switch>
                            )}
                        </Flex>
                    )}
                    <Switch checked={value.enabled} onUpdate={(enabled) => setValue({...value, enabled})}>
                        {t('profileEnabled')}
                    </Switch>
                    <ProfileOptions value={value} onUpdate={setValue} />
                    <Box className={styles.test}>
                        <Flex direction="column" gap={3}>
                            <Text variant="subheader-2">{t('test')}</Text>
                            <Text color="secondary">{t('testHint')}</Text>
                            <Button
                                size="l"
                                view="outlined"
                                loading={busy === 'test'}
                                disabled={
                                    !!busy ||
                                    !savedProfile ||
                                    JSON.stringify(profileDraft(savedProfile)) !== JSON.stringify(value) ||
                                    !!key
                                }
                                onClick={() => void test()}
                            >
                                {t('testAction')}
                            </Button>
                            {selected === 'new' && <Text color="secondary">{t('saveBeforeTest')}</Text>}
                            {result && (
                                <Flex direction="column" gap={2}>
                                    <Label theme={result.ok ? 'success' : 'danger'}>{result.message}</Label>
                                    <Text>{result.preview}</Text>
                                    <Text color="secondary">
                                        {t('testMetrics', {
                                            duration: result.durationMs,
                                            input: result.inputTokens,
                                            output: result.outputTokens,
                                        })}
                                    </Text>
                                </Flex>
                            )}
                        </Flex>
                    </Box>
                </Flex>
            )}
        </SettingsSection>
    );
}
