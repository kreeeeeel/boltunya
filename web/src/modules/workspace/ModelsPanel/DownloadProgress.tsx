import {Box, Flex, Loader, Progress, Text} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import type {Engine} from '@/shared/api/client';
import styles from '@/modules/workspace/ModelsPanel/ModelsPanel.module.css';

export function DownloadProgress({engine}: {readonly engine: Engine}) {
    const t = useTranslations();
    return (
        <Flex direction="column" gap={3} className={styles.download} role="status">
            <Flex justifyContent="space-between" gap={2}>
                <Text>
                    {engine.downloadStage === 'extract'
                        ? t('extractingEngine')
                        : t('downloading', {name: engine.downloading, progress: engine.progress})}
                </Text>
                {engine.downloading === 'Ollama' &&
                    !engine.totalBytes &&
                    engine.downloadStage !== 'extract' && <Loader size="s" />}
            </Flex>
            <Box
                role="progressbar"
                aria-label={t('downloadProgress')}
                aria-valuemin={0}
                aria-valuemax={100}
                aria-valuenow={engine.progress}
            >
                <Progress value={engine.progress} />
            </Box>
            {!!engine.downloadedBytes && (
                <Text color="secondary">
                    {(engine.downloadedBytes / 1048576).toFixed(1)} /{' '}
                    {engine.totalBytes ? (engine.totalBytes / 1048576).toFixed(1) : '…'} {t('megabytes')}
                </Text>
            )}
        </Flex>
    );
}
