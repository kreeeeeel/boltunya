import {Magnifier} from '@gravity-ui/icons';
import {Box, Button, Flex, Icon, Select, Skeleton, Text, TextInput} from '@gravity-ui/uikit';
import {useLocale, useTranslations} from 'next-intl';
import {useState} from 'react';
import type {SpeechAsset} from '@aigram/api-client';
import type {useSpeechState} from '@/modules/speech/model/useSpeechState';
import {filterSpeechAssets} from '@/modules/speech/model/filterSpeechAssets';
import {speechLanguageName} from '@/modules/speech/model/speechLanguageName';
import {SpeechAssetCard} from '@/modules/speech/SpeechAssetCard/SpeechAssetCard';
import styles from '@/modules/speech/SpeechAssets/SpeechAssets.module.css';

export function SpeechAssets({
    state,
    error,
    busy,
    reload,
    install,
    cancel,
    kind,
    selected,
    onSelect,
    onPreview,
}: ReturnType<typeof useSpeechState> & {
    readonly kind?: SpeechAsset['kind'];
    readonly selected?: Partial<Record<SpeechAsset['kind'], string>>;
    readonly onSelect?: (asset: SpeechAsset) => void;
    readonly onPreview?: (asset: SpeechAsset) => void;
}) {
    const t = useTranslations('voice');
    const locale = useLocale();
    const [filters, setFilters] = useState<Record<string, {query: string; language: string}>>({});
    const key = kind ?? 'all';
    const current = filters[key] ?? {query: '', language: ''};
    const matching = state?.assets.filter((asset) => !kind || asset.kind === kind) ?? [];
    const languages = Array.from(new Set(matching.flatMap((asset) => asset.languages))).sort((left, right) =>
        speechLanguageName(left, locale).localeCompare(speechLanguageName(right, locale), locale),
    );
    const visible = filterSpeechAssets(state?.assets ?? [], kind, current.query, current.language, locale);

    return (
        <Flex direction="column" gap={3} qa="speech-assets">
            <Flex direction="column" gap={1}>
                <Text variant="subheader-2">{t('assetsTitle')}</Text>
                <Text color="secondary">{t('assetsHint')}</Text>
            </Flex>
            <Flex gap={3} wrap="wrap" className={styles.filters}>
                <TextInput
                    className={styles.search}
                    size="l"
                    value={current.query}
                    onUpdate={(query) =>
                        setFilters((previous) => ({...previous, [key]: {...current, query}}))
                    }
                    startContent={<Icon data={Magnifier} size={18} />}
                    placeholder={t('searchModels')}
                    controlProps={{'aria-label': t('searchModels')}}
                    hasClear
                />
                <Select
                    className={styles.language}
                    size="l"
                    value={[current.language]}
                    options={[
                        {value: '', content: t('allLanguages')},
                        ...languages.map((language) => ({
                            value: language,
                            content: speechLanguageName(language, locale),
                        })),
                    ]}
                    disabled={!languages.length}
                    aria-label={t('languageFilter')}
                    onUpdate={([language = '']) =>
                        setFilters((previous) => ({...previous, [key]: {...current, language}}))
                    }
                />
            </Flex>
            <Text color="secondary" role="status">
                {t('modelCount', {count: visible.length})}
            </Text>
            {!state && !error && (
                <Box className={styles.grid}>
                    {[0, 1].map((id) => (
                        <Skeleton key={id} height={244} />
                    ))}
                </Box>
            )}
            {error && (
                <Flex direction="column" gap={2}>
                    <Text role="alert" color="danger">
                        {error}
                    </Text>
                    <Box>
                        <Button view="flat-secondary" onClick={reload}>
                            {t('retry')}
                        </Button>
                    </Box>
                </Flex>
            )}
            {!!state && !visible.length && (
                <Flex direction="column" gap={1} alignItems="center" className={styles.empty}>
                    <Text variant="subheader-2">{t('noModels')}</Text>
                    <Text color="secondary">{t('noModelsHint')}</Text>
                </Flex>
            )}
            <Box className={styles.grid}>
                {visible.map((asset) => (
                    <SpeechAssetCard
                        key={asset.id}
                        asset={asset}
                        download={state?.downloads.find((download) => download.assetId === asset.id)}
                        busy={busy === asset.id}
                        selected={selected?.[asset.kind] === asset.id}
                        missing={asset.dependencies
                            .filter(
                                (id) =>
                                    !state?.assets.some(
                                        (candidate) => candidate.id === id && candidate.installed,
                                    ),
                            )
                            .map((id) => state?.assets.find((candidate) => candidate.id === id)?.title ?? id)}
                        onInstall={() => void install(asset.id)}
                        onCancel={() => void cancel(asset.id)}
                        onSelect={onSelect && asset.kind !== 'runtime' ? () => onSelect(asset) : undefined}
                        onPreview={onPreview ? () => onPreview(asset) : undefined}
                    />
                ))}
            </Box>
        </Flex>
    );
}
