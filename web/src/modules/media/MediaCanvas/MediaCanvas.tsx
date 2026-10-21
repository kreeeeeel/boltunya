import {Box, Button, Flex, Loader, Text} from '@gravity-ui/uikit';
import {useRef, useState} from 'react';

import type {Message} from '@/shared/api/client';
import {mediaSource} from '@/modules/media/model/gallery';
import {useMediaMessages} from '@/modules/media/i18n/messages';
import styles from '@/modules/media/MediaViewer/MediaViewer.module.css';

export function MediaCanvas({message, zoom}: {readonly message: Message; readonly zoom: number}) {
    const t = useMediaMessages();
    const [loaded, setLoaded] = useState(false);
    const [failed, setFailed] = useState(false);
    const [revision, setRevision] = useState(0);
    const [offset, setOffset] = useState({x: 0, y: 0});
    const drag = useRef<{x: number; y: number; left: number; top: number} | null>(null);
    const imageArea = useRef<HTMLDivElement>(null);
    const video = message.media?.mimeType.startsWith('video/');
    const source =
        mediaSource(message) +
        (revision ? `${mediaSource(message).includes('?') ? '&' : '?'}retry=${revision}` : '');

    return (
        <Box className={styles.canvas} qa="media-canvas">
            {!loaded && !failed && (
                <Flex className={styles.loading} role="status" aria-label={t.loading}>
                    <Loader size="m" />
                </Flex>
            )}
            {failed ? (
                <Flex className={styles.failure} direction="column" alignItems="center" gap={4}>
                    <Text variant="subheader-2">{t.unavailable}</Text>
                    <Button
                        onClick={() => {
                            setFailed(false);
                            setLoaded(false);
                            setRevision((value) => value + 1);
                        }}
                    >
                        {t.retry}
                    </Button>
                </Flex>
            ) : video ? (
                <Box
                    as="video"
                    key={revision}
                    src={source}
                    className={styles.video}
                    controls
                    autoPlay
                    playsInline
                    preload="auto"
                    onLoadedData={() => setLoaded(true)}
                    onError={() => setFailed(true)}
                    aria-label={t.video}
                />
            ) : (
                <Box
                    ref={imageArea}
                    className={styles.imageArea}
                    onPointerDown={(event) => {
                        if (zoom <= 1) {
                            return;
                        }
                        imageArea.current?.setPointerCapture(event.pointerId);
                        drag.current = {x: event.clientX, y: event.clientY, left: offset.x, top: offset.y};
                    }}
                    onPointerMove={(event) => {
                        if (drag.current && zoom > 1) {
                            setOffset({
                                x: drag.current.left + event.clientX - drag.current.x,
                                y: drag.current.top + event.clientY - drag.current.y,
                            });
                        }
                    }}
                    onPointerUp={(event) => {
                        imageArea.current?.releasePointerCapture(event.pointerId);
                    }}
                    onLostPointerCapture={() => {
                        drag.current = null;
                    }}
                    style={{cursor: zoom > 1 ? 'grab' : 'default'}}
                >
                    <Box
                        as="img"
                        key={revision}
                        src={source}
                        alt={t.photo}
                        draggable={false}
                        className={styles.image}
                        onLoad={() => setLoaded(true)}
                        onError={() => setFailed(true)}
                        style={{
                            opacity: loaded ? 1 : 0,
                            transform: `translate(${zoom > 1 ? offset.x : 0}px, ${zoom > 1 ? offset.y : 0}px) scale(${zoom})`,
                        }}
                    />
                </Box>
            )}
        </Box>
    );
}
