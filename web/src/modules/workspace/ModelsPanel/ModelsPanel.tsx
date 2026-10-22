import {Box, Button, Flex, Icon, Label, Loader, Skeleton, Switch, Text, TextInput} from '@gravity-ui/uikit';
import {
    Check,
    ArrowDownToLine,
    HardDrive,
    Sparkles,
    Thunderbolt,
    Magnifier,
    ArrowRotateRight,
} from '@gravity-ui/icons';
import {useLocale, useTranslations} from 'next-intl';
import {useState} from 'react';
import type {ModelsPanelProps} from '@/modules/workspace/ModelsPanel/ModelsPanel.types';
import styles from '@/modules/workspace/ModelsPanel/ModelsPanel.module.css';
import {api, errorMessage} from '@/shared/api/client';
import {DownloadButton} from '@/modules/workspace/ModelsPanel/DownloadButton';
import {defaultRule} from '@/shared/api/client';
import type {Reply} from '@/shared/api/client';
import {ScrollArea} from '@/shared/ui/ScrollArea/ScrollArea';
import {ProfileModelCards} from '@/modules/workspace/ModelsPanel/ProfileModelCards';
import {useModelProfiles} from '@/modules/settings/model/useModelProfiles';
import {PromptBudgetNotice} from '@/modules/settings/PromptBudgetNotice/PromptBudgetNotice';
import {SegmentedTabs} from '@/shared/ui/SegmentedTabs/SegmentedTabs';
import {StatusNotice} from '@/shared/ui/StatusNotice/StatusNotice';
export function ModelsPanel({
    engine,
    defaultModel,
    defaultModelProfileId = '',
    onRefresh,
    embedded = false,
}: ModelsPanelProps) {
    const t = useTranslations();
    const connections = useModelProfiles();
    const locale = useLocale();
    const [busy, setBusy] = useState('');
    const [error, setError] = useState('');
    const [search, setSearch] = useState('');
    const [category, setCategory] = useState('all');
    const [installedOnly, setInstalledOnly] = useState(false);
    const [measurements, setMeasurements] = useState<Record<string, Reply>>({});
    async function measure(model: string) {
        const reply = await api.models.preview({
            preview: {
                text: t('benchmarkInput'),
                rule: {
                    ...defaultRule,
                    model,
                    useDefaultModel: false,
                    style: 'concise',
                    maxTokens: 64,
                    useMemory: false,
                    matchMyStyle: false,
                },
            },
        });
        setMeasurements((previous) => ({...previous, [model]: reply}));
    }
    async function cancelDownload() {
        await action('cancel-download', async () => await api.models.cancelModelDownload());
    }
    async function action(name: string, callback: () => Promise<unknown>) {
        setBusy(name);
        setError('');
        try {
            await callback();
            await onRefresh();
        } catch (cause) {
            setError(errorMessage(cause));
        } finally {
            setBusy('');
        }
    }
    const Container = embedded ? Box : ScrollArea;
    if (!engine) {
        return (
            <Container className={[styles.root, embedded ? styles.embedded : ''].join(' ')}>
                <Box className={styles.content} role="status" aria-label={t('loading')} qa="models-skeleton">
                    <Flex className={styles.eyebrow} alignItems="center" gap={2}>
                        <Skeleton width={96} height={21} />
                    </Flex>
                    <Box className={styles.engineCard} qa="skeleton-engine-card">
                        <Flex
                            className={styles.engine}
                            alignItems="center"
                            justifyContent="space-between"
                            gap={4}
                        >
                            <Flex gap={3} alignItems="center">
                                <Skeleton width={23} height={23} />
                                <Flex direction="column" className={styles.engineText}>
                                    <Skeleton width={160} height={20} />
                                    <Skeleton width={220} height={20} />
                                </Flex>
                            </Flex>
                            <Flex
                                className={`${styles.engineActions} ${styles.engineActionsOnline}`}
                                direction="row"
                                gap={2}
                                alignItems="center"
                            >
                                <Skeleton width={180} height={28} />
                                <Skeleton width={124} height={28} />
                            </Flex>
                        </Flex>
                    </Box>
                    <Flex
                        className={styles.searchRow}
                        alignItems="center"
                        justifyContent="space-between"
                        gap={3}
                    >
                        <Skeleton className={styles.searchInput} height={36} />
                        <Skeleton width={100} height={20} />
                    </Flex>
                    <Box className={styles.filters}>
                        <Skeleton className={styles.filterSkeleton} height={36} />
                        <Skeleton width={160} height={20} />
                    </Box>
                    <Box className={styles.cards}>
                        {Array.from({length: 12}, (_, index) => (
                            <Flex
                                key={index}
                                className={styles.card}
                                direction="column"
                                gap={4}
                                qa="model-card-skeleton"
                            >
                                <Flex alignItems="center" justifyContent="space-between">
                                    <Skeleton width={48} height={48} />
                                    <Skeleton width={90} height={28} />
                                </Flex>
                                <Flex direction="column" gap={2}>
                                    <Skeleton width="65%" height={24} />
                                    <Skeleton width="40%" height={20} />
                                </Flex>
                                <Skeleton height={45} />
                                <Flex direction="column" className={styles.comparison}>
                                    {Array.from({length: 10}, (_, feature) => (
                                        <Flex
                                            key={feature}
                                            className={styles.feature}
                                            justifyContent="space-between"
                                            gap={3}
                                        >
                                            <Skeleton width="42%" height={21} />
                                            <Skeleton width="36%" height={21} />
                                        </Flex>
                                    ))}
                                </Flex>
                                <Flex justifyContent="space-between" alignItems="center">
                                    <Skeleton width={64} height={20} />
                                    <Skeleton width={100} height={28} />
                                </Flex>
                                <Skeleton height={36} />
                            </Flex>
                        ))}
                    </Box>
                </Box>
            </Container>
        );
    }
    const query = search.trim().toLocaleLowerCase(locale);
    const engineInstalled = engine.installed ?? engine.online;
    const installingEngine = engine.downloading === 'Ollama';
    const models = engine.models.filter(
        (model) =>
            (!installedOnly || model.installed) &&
            (category === 'all' || (category === 'vision' ? model.supportsVision : !model.supportsVision)) &&
            [model.id, model.name, model.description].some((value) =>
                value.toLocaleLowerCase(locale).includes(query),
            ),
    );
    const enabledProfiles = connections.profiles.filter((profile) => profile.enabled);
    const profiles = enabledProfiles.filter(
        (profile) =>
            !installedOnly &&
            (category === 'all' ||
                (category === 'vision' ? profile.capabilities.vision : !profile.capabilities.vision)) &&
            [profile.name, profile.model].some((value) => value.toLocaleLowerCase(locale).includes(query)),
    );
    return (
        <Container className={[styles.root, embedded ? styles.embedded : ''].join(' ')}>
            <Box className={styles.content}>
                <Flex alignItems="center" gap={2} className={styles.eyebrow}>
                    <Icon data={Sparkles} size={16} />
                    <Text>{t('models')}</Text>
                </Flex>
                <Box className={styles.engineCard} qa="engine-card">
                    <Flex
                        className={styles.engine}
                        alignItems="center"
                        justifyContent="space-between"
                        gap={4}
                    >
                        <Flex gap={3} alignItems="center">
                            <Box className={styles.engineIcon}>
                                <Icon data={HardDrive} size={23} />
                            </Box>
                            <Flex direction="column" className={styles.engineText}>
                                <Text variant="subheader-2">
                                    {t(
                                        engine.online
                                            ? 'engineOnline'
                                            : engineInstalled
                                              ? 'engineOffline'
                                              : 'engineMissing',
                                    )}
                                </Text>
                                <Flex gap={2} alignItems="center">
                                    {engine.warming && <Loader size="s" />}
                                    <Text className={styles.hint}>
                                        {engine.warming
                                            ? t('warming', {name: engine.warming})
                                            : t(
                                                  engine.online
                                                      ? 'engineReadyHint'
                                                      : engineInstalled
                                                        ? 'engineStoppedHint'
                                                        : 'engineHint',
                                              )}
                                    </Text>
                                </Flex>
                            </Flex>
                        </Flex>
                        <Flex
                            className={`${styles.engineActions} ${engine.online ? styles.engineActionsOnline : ''}`}
                            direction={engine.online ? 'row' : 'column'}
                            qa={engine.online ? 'engine-online-actions' : undefined}
                            gap={2}
                            justifyContent="flex-end"
                            alignItems="center"
                        >
                            {engine.online && (
                                <Button
                                    className={styles.engineRestart}
                                    size="m"
                                    view="outlined"
                                    loading={busy === 'restart'}
                                    disabled={!engine.canRestart || !!busy || !!engine.downloading}
                                    title={!engine.canRestart ? t('engineRestartExternal') : undefined}
                                    onClick={() => void action('restart', () => api.models.restartEngine())}
                                >
                                    <Icon data={ArrowRotateRight} size={16} />
                                    {t('restartEngine')}
                                </Button>
                            )}
                            {installingEngine ? (
                                <DownloadButton
                                    engine={engine}
                                    busy={busy === 'cancel-download'}
                                    onCancel={() => void cancelDownload()}
                                />
                            ) : engine.online ? (
                                <Label
                                    className={styles.engineInstalled}
                                    qa="engine-installed"
                                    theme="success"
                                    size="m"
                                    icon={<Icon data={Check} size={16} />}
                                >
                                    {t('installed')}
                                </Label>
                            ) : engineInstalled ? (
                                <Button
                                    className={styles.engineAction}
                                    size="l"
                                    view="action"
                                    loading={busy === 'start'}
                                    disabled={!!engine.downloading}
                                    onClick={() => void action('start', async () => await api.models.start())}
                                >
                                    {t('startEngine')}
                                </Button>
                            ) : (
                                <Button
                                    className={styles.engineAction}
                                    size="l"
                                    view="action"
                                    loading={busy === 'install'}
                                    disabled={!!engine.downloading}
                                    onClick={() =>
                                        void action('install', async () => await api.models.install())
                                    }
                                >
                                    {t('installEngine')}
                                </Button>
                            )}
                        </Flex>
                    </Flex>
                </Box>
                {(error || engine.error) && (
                    <StatusNotice
                        className={styles.engineNotice}
                        title={t('engineErrorTitle')}
                        description={error || engine.error}
                    />
                )}
                <PromptBudgetNotice
                    warningsOnly
                    request={{
                        rule: {
                            ...defaultRule,
                            model: defaultModel,
                            modelProfileId: defaultModelProfileId,
                            useDefaultModel: false,
                        },
                    }}
                />
                <Flex
                    alignItems="center"
                    justifyContent="space-between"
                    gap={3}
                    wrap
                    className={styles.searchRow}
                >
                    <TextInput
                        size="l"
                        view="clear"
                        hasClear
                        value={search}
                        onUpdate={setSearch}
                        qa="model-search"
                        className={styles.searchInput}
                        placeholder={t('modelSearch.label')}
                        controlProps={{'aria-label': t('modelSearch.label')}}
                        startContent={
                            <Box className={styles.searchIcon}>
                                <Icon data={Magnifier} size={18} />
                            </Box>
                        }
                    />
                    <Text color="secondary">
                        {t('modelSearch.count', {
                            count: models.length + profiles.length,
                            total: engine.models.length + enabledProfiles.length,
                        })}
                    </Text>
                </Flex>
                <Box className={styles.filters}>
                    <SegmentedTabs
                        view="panel"
                        label={t('modelFilters.label')}
                        value={category}
                        onUpdate={setCategory}
                        items={['all', 'generation', 'vision'].map((value) => ({
                            value,
                            label: t('modelFilters.' + value),
                        }))}
                    />
                    <Switch checked={installedOnly} onUpdate={setInstalledOnly}>
                        {t('modelFilters.installed')}
                    </Switch>
                </Box>
                <ProfileModelCards
                    selected={defaultModelProfileId}
                    onRefresh={onRefresh}
                    profiles={profiles}
                    error={connections.error}
                />
                {models.length === 0 && profiles.length === 0 && !connections.loading && (
                    <Flex direction="column" alignItems="center" gap={3} className={styles.empty}>
                        <Text variant="subheader-2">{t('modelSearch.empty')}</Text>
                        <Button view="outlined" onClick={() => setSearch('')}>
                            {t('modelSearch.clear')}
                        </Button>
                    </Flex>
                )}
                <Box className={styles.cards}>
                    {models.map((model) => {
                        const index = engine.models.indexOf(model);
                        const measurement = measurements[model.id] ?? model.measurement;
                        const pendingMeasurement = t(
                            model.installed ? 'benchmarkPending' : 'benchmarkAfterInstall',
                        );
                        return (
                            <Flex
                                direction="column"
                                key={model.id}
                                qa="model-card"
                                data-model-id={model.id}
                                gap={4}
                                className={`${styles.card} ${!defaultModelProfileId && defaultModel === model.id ? styles.selected : ''}`}
                            >
                                <Flex alignItems="center" justifyContent="space-between" gap={2}>
                                    <Box className={styles.modelIcon}>
                                        <Icon data={index === 0 ? Thunderbolt : Sparkles} size={28} />
                                    </Box>
                                    <Flex gap={2} wrap justifyContent="flex-end">
                                        {model.supportsVision && (
                                            <Label theme="info" size="m">
                                                {t('modelFilters.vision')}
                                            </Label>
                                        )}
                                        {model.supportsAutomation === false ? (
                                            <Label theme="warning" size="m">
                                                {t('modelDraftOnly')}
                                            </Label>
                                        ) : (
                                            model.recommended && (
                                                <Label theme="info" size="m">
                                                    {t('recommended')}
                                                </Label>
                                            )
                                        )}
                                    </Flex>
                                </Flex>
                                <Flex direction="column" gap={2}>
                                    <Text variant="header-1">{model.name}</Text>
                                    <Text className={styles.hint}>
                                        {model.family
                                            ? t('modelFamily.' + model.family)
                                            : t(['modelSmall', 'modelMedium', 'modelLarge'][index])}
                                    </Text>
                                </Flex>
                                <Text className={styles.modelDescription}>
                                    {model.family
                                        ? t('modelDescription.' + model.family)
                                        : t(
                                              [
                                                  'modelSmallDescription',
                                                  'modelMediumDescription',
                                                  'modelLargeDescription',
                                              ][index],
                                          )}
                                </Text>
                                <Flex
                                    direction="column"
                                    className={styles.comparison}
                                    role="table"
                                    aria-label={model.name}
                                >
                                    {[
                                        [
                                            'compareSpeed',
                                            t(
                                                model.speed === 'unknown'
                                                    ? 'modelMetadataUnknown'
                                                    : model.speed
                                                      ? 'speed.' + model.speed
                                                      : ['speedFastest', 'speedFast', 'speedBalanced'][index],
                                            ),
                                        ],
                                        [
                                            'compareQuality',
                                            t(
                                                model.quality === 'unknown'
                                                    ? 'modelMetadataUnknown'
                                                    : model.quality
                                                      ? 'quality.' + model.quality
                                                      : ['qualityBasic', 'qualityGood', 'qualityBest'][index],
                                            ),
                                        ],
                                        [
                                            'compareThinking',
                                            model.family === 'custom'
                                                ? '—'
                                                : t(model.thinking ? 'thinkingOn' : 'thinkingOff'),
                                        ],
                                        [
                                            'modelProfiles.capability.vision',
                                            model.family === 'custom'
                                                ? t('modelMetadataUnknown')
                                                : t(
                                                      model.supportsVision
                                                          ? 'imagesSupported'
                                                          : 'imagesUnsupported',
                                                  ),
                                        ],
                                        [
                                            'compareContext',
                                            model.contextTokens === 0
                                                ? '—'
                                                : t('modelContextSize', {
                                                      count: new Intl.NumberFormat(locale).format(
                                                          model.contextTokens ?? 8192,
                                                      ),
                                                  }),
                                        ],
                                        [
                                            'compareMemory',
                                            model.memoryGb === 0
                                                ? '—'
                                                : t('memoryRequirement', {gb: model.memoryGb ?? 8}),
                                        ],
                                        ['compareLicense', model.license ?? '—'],
                                        ['compareSize', model.size],
                                        [
                                            'compareLatency',
                                            measurement
                                                ? t('benchmarkLatency', {
                                                      ms: measurement.durationMs,
                                                  })
                                                : pendingMeasurement,
                                        ],
                                        [
                                            'compareGeneration',
                                            measurement
                                                ? t('benchmarkSpeed', {
                                                      speed: measurement.tokensPerSecond.toFixed(1),
                                                  })
                                                : pendingMeasurement,
                                        ],
                                    ].map(([label, value]) => (
                                        <Flex
                                            key={label}
                                            justifyContent="space-between"
                                            gap={3}
                                            role="row"
                                            className={styles.feature}
                                        >
                                            <Text color="secondary" role="cell">
                                                {t(label)}
                                            </Text>
                                            <Text role="cell" className={styles.featureValue}>
                                                {value}
                                            </Text>
                                        </Flex>
                                    ))}
                                </Flex>
                                <Flex justifyContent="space-between" alignItems="center">
                                    <Text color="secondary">
                                        {measurement ? t('benchmarkResult') : model.size}
                                    </Text>
                                    <Label theme={model.installed ? 'success' : 'normal'} size="m">
                                        {t(model.installed ? 'installed' : 'notInstalled')}
                                    </Label>
                                </Flex>
                                {engine.downloading === model.id ? (
                                    <DownloadButton
                                        engine={engine}
                                        busy={busy === 'cancel-download'}
                                        onCancel={() => void cancelDownload()}
                                    />
                                ) : model.installed ? (
                                    <Button
                                        size="l"
                                        view={
                                            !defaultModelProfileId && defaultModel === model.id
                                                ? 'outlined-info'
                                                : 'action'
                                        }
                                        disabled={
                                            (!defaultModelProfileId && defaultModel === model.id) ||
                                            !!busy ||
                                            engine.warming === model.id
                                        }
                                        aria-busy={busy === model.id || engine.warming === model.id}
                                        onClick={() =>
                                            void action(model.id, async () => {
                                                await api.settings.settings({
                                                    settingsUpdate: {
                                                        defaultModel: model.id,
                                                        defaultModelProfileId: '',
                                                    },
                                                });
                                                await onRefresh();
                                                if (!measurement) {
                                                    await measure(model.id);
                                                }
                                            })
                                        }
                                    >
                                        {(busy === model.id || engine.warming === model.id) && (
                                            <Loader size="s" />
                                        )}
                                        {t(
                                            busy === model.id || engine.warming === model.id
                                                ? 'modelWarming'
                                                : !defaultModelProfileId && defaultModel === model.id
                                                  ? 'chosen'
                                                  : 'choose',
                                        )}
                                    </Button>
                                ) : (
                                    <Button
                                        size="l"
                                        view="action"
                                        disabled={!engine.online || !!engine.downloading || !!busy}
                                        loading={busy === model.id}
                                        onClick={() =>
                                            void action(
                                                model.id,
                                                async () =>
                                                    await api.models.pull({
                                                        modelRequest: {model: model.id},
                                                    }),
                                            )
                                        }
                                    >
                                        <Icon data={ArrowDownToLine} size={17} />
                                        {t('install')}
                                    </Button>
                                )}
                                <Button
                                    size="l"
                                    view="flat-info"
                                    loading={busy === 'benchmark-' + model.id}
                                    disabled={!model.installed || !engine.online || !!busy}
                                    onClick={() =>
                                        void action('benchmark-' + model.id, () => measure(model.id))
                                    }
                                >
                                    <Icon data={Thunderbolt} size={16} />
                                    {t('benchmark')}
                                </Button>
                            </Flex>
                        );
                    })}
                </Box>
                <Text className={styles.footer}>
                    {t('benchmarkHint')} {t('modelResourceHint')}
                </Text>
            </Box>
        </Container>
    );
}
