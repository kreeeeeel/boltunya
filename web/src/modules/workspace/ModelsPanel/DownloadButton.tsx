import {useTranslations} from 'next-intl';
import {Box, Text} from '@gravity-ui/uikit';
import type {Engine} from '@/shared/api/client';
import styles from '@/modules/workspace/ModelsPanel/DownloadButton.module.css';

export function DownloadButton({
    engine,
    onCancel,
    busy,
}: {
    readonly engine: Engine;
    readonly onCancel: () => void;
    readonly busy: boolean;
}) {
    const t = useTranslations();
    const progress = Math.max(0, Math.min(100, engine.progress));
    return (
        <Box
            as="button"
            type="button"
            className={styles.button}
            onClick={onCancel}
            disabled={busy}
            aria-label={t('cancelDownload')}
            qa="download-button"
        >
            <Box as="span" className={styles.fill} style={{width: `${progress}%`}} aria-hidden="true" />
            <Box
                as="span"
                className={styles.content}
                role="progressbar"
                aria-label={t('downloadProgress')}
                aria-valuemin={0}
                aria-valuemax={100}
                aria-valuenow={progress}
            >
                <Text className={styles.percent}>{progress}%</Text>
                <Text className={styles.cancel}>
                    {t(busy ? 'cancellingDownload' : 'cancelDownloadAction')}
                </Text>
            </Box>
        </Box>
    );
}
