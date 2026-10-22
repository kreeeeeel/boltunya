import {ArrowDownToLine, Check, Cpu, Microphone, Play, Volume} from '@gravity-ui/icons';
import {Box, Button, Flex, Icon, Label, Link, Text, Tooltip} from '@gravity-ui/uikit';
import {useLocale, useTranslations} from 'next-intl';
import type {SpeechAsset, SpeechDownload} from '@aigram/api-client';
import {speechLanguageName} from '@/modules/speech/model/speechLanguageName';
import {formatSpeechSize} from '@/modules/speech/model/formatSpeechSize';
import styles from '@/modules/speech/SpeechAssetCard/SpeechAssetCard.module.css';

export function SpeechAssetCard({
    asset,
    download,
    missing,
    busy,
    selected,
    onInstall,
    onCancel,
    onSelect,
    onPreview,
    requiredDownloadBytes,
}: {
    readonly asset: SpeechAsset;
    readonly download?: SpeechDownload;
    readonly missing: string[];
    readonly busy: boolean;
    readonly selected?: boolean;
    readonly onInstall: () => void;
    readonly onCancel: () => void;
    readonly onSelect?: () => void;
    readonly onPreview?: () => void;
    readonly requiredDownloadBytes?: number;
}) {
    const t = useTranslations('voice');
    const locale = useLocale();
    const active = !!download && ['queued', 'downloading', 'extracting'].includes(download.phase);
    const progress = download?.totalBytes
        ? Math.max(0, Math.min(100, Math.round((download.downloadedBytes * 100) / download.totalBytes)))
        : 0;
    const ready = asset.installed && !missing.length;
    const icon = asset.kind === 'runtime' ? Cpu : asset.kind === 'recognition' ? Microphone : Volume;

    return (
        <Flex
            direction="column"
            gap={3}
            className={`${styles.card} ${selected ? styles.selected : ''}`}
            qa="speech-asset"
        >
            <Flex gap={3} alignItems="flex-start">
                <Flex alignItems="center" justifyContent="center" className={styles.icon}>
                    <Icon data={icon} size={22} />
                </Flex>
                <Flex direction="column" gap={1} className={styles.identity}>
                    <Text variant="subheader-2" className={styles.title}>
                        {asset.title}
                    </Text>
                    <Text color="secondary">{t('engines.' + (asset.engine ?? 'sherpa'))}</Text>
                </Flex>
            </Flex>
            <Flex gap={2} wrap="wrap" alignItems="center">
                <Label>{t('assetKinds.' + asset.kind)}</Label>
                {asset.installed && (
                    <Label theme="success" icon={<Icon data={Check} size={14} />}>
                        {t(asset.userProvided ? 'imported' : 'installed')}
                    </Label>
                )}
            </Flex>
            <Flex direction="column" className={styles.features}>
                {[
                    {
                        label: t('downloadSize'),
                        value: formatSpeechSize(requiredDownloadBytes ?? asset.sizeBytes, locale),
                    },
                    {
                        label: t('modelLanguages'),
                        value: asset.languages.length
                            ? asset.languages
                                  .filter((language) => language === 'ru' || language === 'en')
                                  .map((language) => speechLanguageName(language, locale))
                                  .join(' · ') ||
                              asset.languages
                                  .map((language) => speechLanguageName(language, locale))
                                  .join(' · ')
                            : t('languageIndependent'),
                    },
                    {
                        label: t('modelFormat'),
                        value:
                            asset.format ||
                            (asset.kind === 'runtime'
                                ? 'Windows x64'
                                : asset.kind === 'recognition'
                                  ? 'Whisper'
                                  : 'VITS / Piper'),
                    },
                ].map(({label, value}) => (
                    <Flex
                        key={label}
                        alignItems="center"
                        justifyContent="space-between"
                        gap={3}
                        className={styles.feature}
                    >
                        <Text color="secondary">{label}</Text>
                        <Text className={styles.featureValue}>{value}</Text>
                    </Flex>
                ))}
            </Flex>
            {!!missing.length && (
                <Text color="secondary">{t('dependencies', {names: missing.join(', ')})}</Text>
            )}
            {download?.phase === 'error' && (
                <Text color="danger" role="alert">
                    {!download.error || download.error === 'SPEECH_INSTALL_FAILED'
                        ? t('downloadFailed')
                        : download.error}
                </Text>
            )}
            {download?.phase === 'cancelled' && !asset.installed && (
                <Text color="secondary">{t('phases.cancelled')}</Text>
            )}
            <Flex gap={2} className={styles.actions}>
                {active ? (
                    <Box
                        as="button"
                        type="button"
                        className={styles.download}
                        disabled={busy}
                        onClick={onCancel}
                        aria-label={t('cancelAsset', {name: asset.title})}
                    >
                        <Box
                            as="span"
                            className={styles.fill}
                            style={{width: `${progress}%`}}
                            aria-hidden="true"
                        />
                        <Box
                            as="span"
                            className={styles.progress}
                            role="progressbar"
                            aria-label={t('assetProgress', {name: asset.title})}
                            aria-valuemin={0}
                            aria-valuemax={100}
                            aria-valuenow={progress}
                        >
                            <Text className={styles.percent}>
                                {download.phase === 'downloading'
                                    ? progress + '%'
                                    : t('phases.' + download.phase)}
                            </Text>
                            <Text className={styles.cancel}>{t('cancel')}</Text>
                        </Box>
                    </Box>
                ) : !asset.installed ? (
                    <Button
                        className={styles.action}
                        size="l"
                        view="flat-info"
                        loading={busy}
                        onClick={onInstall}
                        aria-label={t('installAsset', {name: asset.title})}
                    >
                        <Icon data={ArrowDownToLine} size={16} />
                        {t('download')}
                    </Button>
                ) : onSelect ? (
                    <Button
                        className={styles.action}
                        size="l"
                        view={selected ? 'flat-info' : 'flat-secondary'}
                        disabled={selected || !ready || busy}
                        onClick={onSelect}
                        aria-label={t(selected ? 'selectedAsset' : 'selectAsset', {name: asset.title})}
                    >
                        {selected && <Icon data={Check} size={16} />}
                        {t(selected ? 'selected' : 'select')}
                    </Button>
                ) : (
                    <Text color="secondary" className={styles.runtimeReady}>
                        {t('runtimeReady')}
                    </Text>
                )}
                {asset.kind === 'synthesis' && asset.installed && onPreview && (
                    <Button
                        className={styles.action}
                        size="l"
                        view="flat-secondary"
                        disabled={!ready}
                        onClick={onPreview}
                        aria-label={t('previewAsset', {name: asset.title})}
                    >
                        <Icon data={Play} size={16} />
                        {t('previewCheck')}
                    </Button>
                )}
            </Flex>
            <Box className={styles.source}>
                {asset.sourceUrl ? (
                    <Tooltip content={asset.license}>
                        <Link href={asset.sourceUrl} target="_blank" rel="noreferrer">
                            {t('sourceAndLicense')}
                        </Link>
                    </Tooltip>
                ) : asset.license ? (
                    <Text color="secondary" ellipsis title={asset.license}>
                        {asset.license}
                    </Text>
                ) : null}
            </Box>
        </Flex>
    );
}
