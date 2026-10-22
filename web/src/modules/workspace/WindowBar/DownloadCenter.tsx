import {ArrowDownToLine, Xmark} from '@gravity-ui/icons';
import {Box, Icon, Text} from '@gravity-ui/uikit';
import {useLocale, useTranslations} from 'next-intl';
import {useEffect, useRef, useState} from 'react';
import type {Engine} from '@/shared/api/client';
import {api, errorMessage} from '@/shared/api/client';
import {useSpeechState} from '@/modules/speech/model/useSpeechState';
import {formatTransferSpeed} from '@/modules/workspace/model/formatTransferSpeed';
import styles from '@/modules/workspace/WindowBar/DownloadCenter.module.css';

export function DownloadCenter({
    engine,
    onError,
}: {
    readonly engine?: Engine;
    readonly onError: (message: string) => void;
}) {
    const t = useTranslations();
    const locale = useLocale();
    const speech = useSpeechState();
    const download = speech.state?.downloads.find((item) =>
        ['downloading', 'extracting'].includes(item.phase),
    );
    const model = engine?.downloading;
    const id = model ? 'model:' + model : download ? 'speech:' + download.assetId : '';
    const title = model
        ? (engine.models.find((item) => item.id === model)?.name ?? model)
        : (speech.state?.assets.find((item) => item.id === download?.assetId)?.title ?? '');
    const bytes = model ? (engine.downloadedBytes ?? 0) : (download?.downloadedBytes ?? 0);
    const total = download?.totalBytes ?? 0;
    const progress = Math.max(
        0,
        Math.min(100, model ? engine.progress : total ? Math.round((bytes * 100) / total) : 0),
    );
    const [pending, setPending] = useState(false);
    const [measured, setMeasured] = useState({id: '', speed: 0});
    const speed = measured.id === id ? measured.speed : 0;
    const currentBytes = useRef(bytes);

    useEffect(() => {
        currentBytes.current = bytes;
    }, [bytes]);
    useEffect(() => {
        if (!id) {
            return;
        }
        let previous = currentBytes.current;
        let measuredAt = performance.now();
        const timer = setInterval(() => {
            const now = performance.now();
            const current = currentBytes.current;
            if (current !== previous) {
                setMeasured({
                    id,
                    speed: Math.max(0, ((current - previous) * 1000) / Math.max(1, now - measuredAt)),
                });
                previous = current;
                measuredAt = now;
            } else if (now - measuredAt > 5000) {
                setMeasured({id, speed: 0});
            }
        }, 1000);
        return () => clearInterval(timer);
    }, [id]);

    async function cancel() {
        setPending(true);
        try {
            if (model) {
                await api.models.cancelModelDownload();
            } else if (download) {
                await api.speech.cancelSpeechAsset({assetId: download.assetId});
                window.dispatchEvent(new Event('speech-assets-updated'));
            }
        } catch (cause) {
            onError(errorMessage(cause));
        } finally {
            setPending(false);
        }
    }

    if (!id) {
        return null;
    }

    return (
        <Box
            as="button"
            type="button"
            className={styles.button}
            onClick={() => void cancel()}
            disabled={pending}
            aria-label={t('cancelDownload')}
            qa="titlebar-download"
        >
            <Box as="span" className={styles.fill} style={{width: `${progress}%`}} aria-hidden="true" />
            <Box
                as="span"
                className={styles.content}
                role="progressbar"
                aria-label={title}
                aria-valuemin={0}
                aria-valuemax={100}
                aria-valuenow={progress}
            >
                <Icon data={ArrowDownToLine} size={14} />
                <Text className={styles.title}>{title}</Text>
                <Text className={styles.percent}>· {progress}%</Text>
                <Text className={styles.speed}>· {formatTransferSpeed(speed, locale)}</Text>
            </Box>
            <Box as="span" className={styles.cancel}>
                <Icon data={Xmark} size={14} />
                <Text>{t(pending ? 'cancellingDownload' : 'cancelDownloadAction')}</Text>
            </Box>
        </Box>
    );
}
