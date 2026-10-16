import {Box, Skeleton, Text} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import {useEffect, useRef, useState} from 'react';
import type {AnimationItem} from 'lottie-web';

import {api, dataOrThrow} from '@/shared/api/client';
import styles from '@/modules/workspace/MessageMedia/MessageMedia.module.css';

export function AnimatedSticker({fileId}: {readonly fileId: number}) {
    const t = useTranslations();
    const container = useRef<HTMLDivElement>(null);
    const [ready, setReady] = useState(false);
    const [error, setError] = useState(false);
    useEffect(() => {
        const controller = new AbortController();
        let animation: AnimationItem | undefined;
        async function load() {
            try {
                const [source, lottie] = await Promise.all([
                    api.GET('/api/media/{fileId}', {
                        params: {path: {fileId}},
                        parseAs: 'text',
                        signal: controller.signal,
                    }),
                    import('lottie-web'),
                ]);
                if (controller.signal.aborted || !container.current) {
                    return;
                }
                const data = JSON.parse(dataOrThrow(source));
                animation = lottie.default.loadAnimation({
                    container: container.current,
                    renderer: 'svg',
                    loop: true,
                    autoplay: !window.matchMedia('(prefers-reduced-motion: reduce)').matches,
                    animationData: data,
                });
                animation.addEventListener('DOMLoaded', () => setReady(true));
                animation.addEventListener('data_failed', () => setError(true));
            } catch {
                if (!controller.signal.aborted) {
                    setError(true);
                }
            }
        }
        void load();
        return () => {
            controller.abort();
            animation?.destroy();
        };
    }, [fileId]);
    return (
        <Box className={styles.sticker} role="img" aria-label={t('sticker')}>
            {!ready && !error && <Skeleton width="100%" height="100%" />}
            <Box ref={container} className={styles.animation} />
            {error && <Text color="secondary">{t('mediaError')}</Text>}
        </Box>
    );
}
