import {ArrowDownToLine, Check, Xmark} from '@gravity-ui/icons';
import {Box, Button, Flex, Icon, Label, Link, Loader, Progress, Skeleton, Text} from '@gravity-ui/uikit';
import {useLocale, useTranslations} from 'next-intl';
import type {useSpeechState} from '@/modules/speech/model/useSpeechState';
import styles from '@/modules/speech/SpeechAssets/SpeechAssets.module.css';

export function SpeechAssets({
    state,
    error,
    busy,
    reload,
    install,
    cancel,
}: ReturnType<typeof useSpeechState>) {
    const t = useTranslations('voice');
    const locale = useLocale();
    return (
        <Flex direction="column" gap={3} qa="speech-assets">
            <Text variant="subheader-2">{t('assetsTitle')}</Text>
            <Text color="secondary">{t('assetsHint')}</Text>
            {!state && !error && <Skeleton height={168} />}
            {error && (
                <Flex direction="column" gap={2}>
                    <Text role="alert" color="danger">
                        {error}
                    </Text>
                    <Button onClick={reload}>{t('retry')}</Button>
                </Flex>
            )}
            {state?.assets.map((asset) => {
                const download = state.downloads.find((value) => value.assetId === asset.id);
                const active = download && ['queued', 'downloading', 'extracting'].includes(download.phase);
                const progress = download?.totalBytes
                    ? Math.min(100, Math.round((download.downloadedBytes * 100) / download.totalBytes))
                    : 0;
                const missing = asset.dependencies.filter(
                    (id) => !state.assets.some((candidate) => candidate.id === id && candidate.installed),
                );
                return (
                    <Flex
                        key={asset.id}
                        direction="column"
                        gap={3}
                        className={styles.asset}
                        qa="speech-asset"
                    >
                        <Flex gap={3} justifyContent="space-between" wrap="wrap">
                            <Flex direction="column" gap={1} className={styles.identity}>
                                <Text variant="subheader-2">{asset.title}</Text>
                                <Flex gap={2} wrap="wrap" alignItems="center">
                                    <Label>{t('assetKinds.' + asset.kind)}</Label>
                                    <Text color="secondary">
                                        {t('assetSize', {
                                            size: new Intl.NumberFormat(locale, {
                                                maximumFractionDigits: 1,
                                            }).format(asset.sizeBytes / 1024 / 1024),
                                        })}
                                    </Text>
                                </Flex>
                            </Flex>
                            {asset.userProvided ? (
                                <Label theme="success" icon={<Icon data={Check} size={14} />}>
                                    {t('imported')}
                                </Label>
                            ) : asset.installed ? (
                                <Label theme="success" icon={<Icon data={Check} size={14} />}>
                                    {t('installed')}
                                </Label>
                            ) : active ? (
                                <Button
                                    size="m"
                                    view="flat-secondary"
                                    loading={busy === asset.id}
                                    onClick={() => void cancel(asset.id)}
                                    aria-label={t('cancelAsset', {name: asset.title})}
                                >
                                    <Icon data={Xmark} size={16} />
                                    {t('cancel')}
                                </Button>
                            ) : (
                                <Button
                                    size="m"
                                    view="outlined-info"
                                    loading={busy === asset.id}
                                    onClick={() => void install(asset.id)}
                                    aria-label={t('installAsset', {name: asset.title})}
                                >
                                    <Icon data={ArrowDownToLine} size={16} />
                                    {t('download')}
                                </Button>
                            )}
                        </Flex>
                        {!!asset.languages.length && (
                            <Text color="secondary">
                                {t('languages', {languages: asset.languages.join(', ')})}
                            </Text>
                        )}
                        {!!missing.length && (
                            <Text color="secondary">
                                {t('dependencies', {
                                    names: missing
                                        .map(
                                            (id) =>
                                                state.assets.find((candidate) => candidate.id === id)
                                                    ?.title ?? id,
                                        )
                                        .join(', '),
                                })}
                            </Text>
                        )}
                        {active && (
                            <Flex direction="column" gap={2} role="status">
                                <Flex gap={2} alignItems="center">
                                    <Loader size="s" />
                                    <Text>
                                        {t('phases.' + download.phase)}
                                        {download.phase === 'downloading' && download.totalBytes > 0
                                            ? ' · ' + progress + '%'
                                            : ''}
                                    </Text>
                                </Flex>
                                <Box
                                    role="progressbar"
                                    aria-label={t('assetProgress', {name: asset.title})}
                                    aria-valuemin={0}
                                    aria-valuemax={100}
                                    aria-valuenow={progress}
                                >
                                    <Progress value={progress} />
                                </Box>
                            </Flex>
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
                        <Box>
                            {asset.sourceUrl ? (
                                <Link href={asset.sourceUrl} target="_blank" rel="noreferrer">
                                    {asset.license}
                                </Link>
                            ) : asset.license ? (
                                <Text color="secondary">{asset.license}</Text>
                            ) : null}
                        </Box>
                    </Flex>
                );
            })}
        </Flex>
    );
}
