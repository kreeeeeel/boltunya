import {ArrowDownToLine, Check, Cpu, Microphone, Play, Volume} from '@gravity-ui/icons';
import {Box, Button, Flex, Icon, Label, Link, Text, Tooltip} from '@gravity-ui/uikit';
import {useLocale, useTranslations} from 'next-intl';
import type {SpeechAsset, SpeechDownload} from '@aigram/api-client';
import {speechLanguageName} from '@/modules/speech/model/speechLanguageName';
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
                    <Text color="secondary">
                        {t('assetSize', {
                            size: new Intl.NumberFormat(locale, {maximumFractionDigits: 1}).format(
                                asset.sizeBytes / 1024 / 1024,
                            ),
                        })}
                    </Text>
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
            <Text color="secondary" className={styles.languages}>
                {asset.languages.length
                    ? asset.languages.map((language) => speechLanguageName(language, locale)).join(' · ')
                    : t('allLanguages')}
            </Text>
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
                        disabled={selected || !ready}
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
