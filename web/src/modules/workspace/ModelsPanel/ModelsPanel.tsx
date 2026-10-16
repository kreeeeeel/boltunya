import {Box, Button, Flex, Icon, Label, Loader, Text, TextInput} from '@gravity-ui/uikit';
import {Check, ArrowDownToLine, HardDrive, Sparkles, Thunderbolt, Magnifier} from '@gravity-ui/icons';
import {useLocale, useTranslations} from 'next-intl';
import {useState} from 'react';

import type {ModelsPanelProps} from '@/modules/workspace/ModelsPanel/ModelsPanel.types';
import styles from '@/modules/workspace/ModelsPanel/ModelsPanel.module.css';
import {api, dataOrThrow, errorMessage} from '@/shared/api/client';
import {DownloadProgress} from '@/modules/workspace/ModelsPanel/DownloadProgress';
import {defaultRule} from '@/shared/api/client';
import type {Reply} from '@/shared/api/client';
import {ScrollArea} from '@/shared/ui/ScrollArea/ScrollArea';
import {PanelSkeleton} from '@/shared/ui/PanelSkeleton/PanelSkeleton';

export function ModelsPanel({engine, defaultModel, onRefresh, embedded = false}: ModelsPanelProps) {
    const t = useTranslations();
    const locale = useLocale();
    const [busy, setBusy] = useState('');
    const [error, setError] = useState('');
    const [search, setSearch] = useState('');
    const [measurements, setMeasurements] = useState<Record<string, Reply>>({});

    async function benchmark(model: string) {
        await action('benchmark-' + model, async () => {
            const reply = dataOrThrow(
                await api.POST('/api/preview', {
                    body: {
                        text: t('benchmarkInput'),
                        rule: {...defaultRule, model, style: 'concise', maxTokens: 64},
                    },
                }),
            );
            setMeasurements((previous) => ({...previous, [model]: reply}));
        });
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

    if (!engine) {
        return <PanelSkeleton />;
    }

    const Container = embedded ? Box : ScrollArea;
    const query = search.trim().toLocaleLowerCase(locale);
    const models = engine.models.filter((model) =>
        [model.id, model.name, model.description].some((value) =>
            value.toLocaleLowerCase(locale).includes(query),
        ),
    );
    return (
        <Container className={[styles.root, embedded ? styles.embedded : ''].join(' ')}>
            <Box className={styles.content}>
                <Flex alignItems="center" gap={2} className={styles.eyebrow}>
                    <Icon data={Sparkles} size={16} />
                    <Text>{t('models')}</Text>
                </Flex>
                <Box className={styles.engineCard}>
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
                                    {t(engine?.online ? 'engineOnline' : 'engineOffline')}
                                </Text>
                                <Text className={styles.hint}>
                                    {t(engine.online ? 'engineReadyHint' : 'engineHint')}
                                </Text>
                            </Flex>
                        </Flex>
                        {!engine?.online && (
                            <Flex gap={2}>
                                <Button
                                    loading={busy === 'start'}
                                    onClick={() =>
                                        void action('start', async () =>
                                            dataOrThrow(await api.POST('/api/engine/start')),
                                        )
                                    }
                                >
                                    {t('startEngine')}
                                </Button>
                                <Button
                                    view="action"
                                    loading={busy === 'install'}
                                    disabled={!!engine?.downloading}
                                    onClick={() =>
                                        void action('install', async () =>
                                            dataOrThrow(await api.POST('/api/engine/install')),
                                        )
                                    }
                                >
                                    {t('installEngine')}
                                </Button>
                            </Flex>
                        )}
                        {engine?.online && (
                            <Label theme="success" size="m" icon={<Icon data={Check} size={16} />}>
                                {t('installed')}
                            </Label>
                        )}
                    </Flex>
                    {engine.downloading === 'Ollama' && <DownloadProgress engine={engine} />}
                </Box>
                {(error || engine?.error) && <Text color="danger">{error || engine?.error}</Text>}
                {engine?.warming && (
                    <Flex gap={3} alignItems="center" className={styles.download}>
                        <Loader size="s" />
                        <Text>{t('warming', {name: engine.warming})}</Text>
                    </Flex>
                )}
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
                        {t('modelSearch.count', {count: models.length, total: engine.models.length})}
                    </Text>
                </Flex>
                {models.length === 0 && (
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
                        return (
                            <Flex
                                direction="column"
                                key={model.id}
                                qa="model-card"
                                data-model-id={model.id}
                                gap={4}
                                className={`${styles.card} ${model.recommended ? styles.recommended : ''} ${defaultModel === model.id ? styles.selected : ''}`}
                            >
                                <Flex alignItems="center" justifyContent="space-between">
                                    <Box className={styles.modelIcon}>
                                        <Icon data={index === 0 ? Thunderbolt : Sparkles} size={28} />
                                    </Box>
                                    {model.recommended && (
                                        <Label theme="info" size="m">
                                            {t('recommended')}
                                        </Label>
                                    )}
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
                                                model.speed
                                                    ? 'speed.' + model.speed
                                                    : ['speedFastest', 'speedFast', 'speedBalanced'][index],
                                            ),
                                        ],
                                        [
                                            'compareQuality',
                                            t(
                                                model.quality
                                                    ? 'quality.' + model.quality
                                                    : ['qualityBasic', 'qualityGood', 'qualityBest'][index],
                                            ),
                                        ],
                                        ['compareThinking', t(model.thinking ? 'thinkingOn' : 'thinkingOff')],
                                        [
                                            'compareContext',
                                            t('modelContextSize', {
                                                count: new Intl.NumberFormat(locale).format(
                                                    model.contextTokens ?? 8192,
                                                ),
                                            }),
                                        ],
                                        ['compareMemory', t('memoryRequirement', {gb: model.memoryGb ?? 8})],
                                        ['compareLicense', model.license ?? '—'],
                                        ['compareSize', model.size],
                                        [
                                            'compareLatency',
                                            measurements[model.id]
                                                ? t('benchmarkLatency', {
                                                      ms: measurements[model.id].durationMs,
                                                  })
                                                : '—',
                                        ],
                                        [
                                            'compareGeneration',
                                            measurements[model.id]
                                                ? t('benchmarkSpeed', {
                                                      speed: measurements[model.id].tokensPerSecond.toFixed(
                                                          1,
                                                      ),
                                                  })
                                                : '—',
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
                                        {measurements[model.id] ? t('benchmarkResult') : model.size}
                                    </Text>
                                    <Label theme={model.installed ? 'success' : 'normal'} size="m">
                                        {t(model.installed ? 'installed' : 'notInstalled')}
                                    </Label>
                                </Flex>
                                {engine.downloading === model.id && <DownloadProgress engine={engine} />}
                                {model.installed ? (
                                    <Button
                                        size="l"
                                        view={defaultModel === model.id ? 'outlined-info' : 'action'}
                                        disabled={defaultModel === model.id || !!busy}
                                        loading={busy === model.id}
                                        onClick={() =>
                                            void action(model.id, async () =>
                                                dataOrThrow(
                                                    await api.PATCH('/api/settings', {
                                                        body: {defaultModel: model.id},
                                                    }),
                                                ),
                                            )
                                        }
                                    >
                                        {t(defaultModel === model.id ? 'chosen' : 'choose')}
                                    </Button>
                                ) : (
                                    <Button
                                        size="l"
                                        view="action"
                                        disabled={!engine.online || !!engine.downloading || !!busy}
                                        loading={busy === model.id}
                                        onClick={() =>
                                            void action(model.id, async () =>
                                                dataOrThrow(
                                                    await api.POST('/api/models/pull', {
                                                        body: {model: model.id},
                                                    }),
                                                ),
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
                                    onClick={() => void benchmark(model.id)}
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
