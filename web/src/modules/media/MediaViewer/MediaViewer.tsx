import {Box, Button, Flex, Icon, Link, Text, Tooltip} from '@gravity-ui/uikit';
import {ArrowDownToLine, ArrowLeft, ArrowRight, ArrowsExpand, Minus, Plus, Xmark} from '@gravity-ui/icons';
import {useLocale} from 'next-intl';
import {useEffect, useRef, useState} from 'react';

import type {Message} from '@/shared/api/client';
import {desktopBridge} from '@/shared/desktop/bridge';
import {galleryMessages, mediaSource} from '@/modules/media/model/gallery';
import {useMediaMessages} from '@/modules/media/i18n/messages';
import {MediaCanvas} from '@/modules/media/MediaCanvas/MediaCanvas';
import {RichText} from '@/modules/workspace/RichText/RichText';
import styles from '@/modules/media/MediaViewer/MediaViewer.module.css';

const ZOOM_STEP = 0.5;
const MAX_ZOOM = 5;

export function MediaViewer({
    messages,
    initialId,
    onClose,
}: {
    readonly messages: Message[];
    readonly initialId: string;
    readonly onClose: () => void;
}) {
    const t = useMediaMessages();
    const locale = useLocale();
    const [selected, setSelected] = useState(initialId);
    const [zoom, setZoom] = useState(1);
    const [fullscreenError, setFullscreenError] = useState('');
    const root = useRef<HTMLDivElement>(null);
    const gallery = galleryMessages(messages);
    const index = gallery.findIndex((message) => message.id === selected);
    const message = gallery[index];
    function move(direction: number) {
        const next = gallery[index + direction];
        if (next) {
            setSelected(next.id);
            setZoom(1);
        }
    }

    useEffect(() => {
        const items = galleryMessages(messages);
        const current = items.findIndex((item) => item.id === selected);
        function navigate(direction: number) {
            const next = items[current + direction];
            if (next) {
                setSelected(next.id);
                setZoom(1);
            }
        }
        function keydown(event: KeyboardEvent) {
            if (event.defaultPrevented || event.target instanceof HTMLInputElement) {
                return;
            }
            const actions: Record<string, () => void> = {
                Escape: onClose,
                ArrowLeft: () => navigate(-1),
                ArrowRight: () => navigate(1),
                '+': () => setZoom((value) => Math.min(MAX_ZOOM, value + ZOOM_STEP)),
                '=': () => setZoom((value) => Math.min(MAX_ZOOM, value + ZOOM_STEP)),
                '-': () => setZoom((value) => Math.max(1, value - ZOOM_STEP)),
                '0': () => setZoom(1),
            };
            if (event.key in actions) {
                event.preventDefault();
                actions[event.key]();
            }
        }
        window.addEventListener('keydown', keydown, true);
        return () => window.removeEventListener('keydown', keydown, true);
    }, [messages, selected, onClose]);

    async function fullscreen() {
        try {
            setFullscreenError('');
            const desktop = desktopBridge();
            if (desktop) {
                await desktop.action('fullscreen');
            } else if (document.fullscreenElement) {
                await document.exitFullscreen();
            } else {
                await root.current?.requestFullscreen();
            }
        } catch {
            setFullscreenError(t.unavailable);
        }
    }

    if (!message) {
        return (
            <Flex className={styles.root} alignItems="center" justifyContent="center">
                <Text>{t.unavailable}</Text>
                <Button onClick={onClose}>{t.close}</Button>
            </Flex>
        );
    }
    const video = message.media?.mimeType.startsWith('video/');

    return (
        <Flex
            ref={root}
            direction="column"
            className={styles.root}
            qa="media-viewer"
            role="region"
            aria-label={video ? t.video : t.photo}
        >
            <Flex className={styles.toolbar} alignItems="center" justifyContent="space-between">
                <Flex direction="column" gap={1} className={styles.identity}>
                    <Text variant="subheader-2">
                        {message.outgoing ? t.owner : message.senderName || (video ? t.video : t.photo)}
                    </Text>
                    <Text className={styles.secondary}>
                        {new Date(message.date * 1000).toLocaleString(locale, {
                            day: 'numeric',
                            month: 'long',
                            year: 'numeric',
                            hour: '2-digit',
                            minute: '2-digit',
                        })}
                    </Text>
                </Flex>
                <Flex alignItems="center" gap={1} className={styles.actions}>
                    {!video && (
                        <>
                            <Tooltip content={t.zoomOut}>
                                <Button
                                    view="flat-secondary"
                                    disabled={zoom <= 1}
                                    aria-label={t.zoomOut}
                                    onClick={() => setZoom((value) => Math.max(1, value - ZOOM_STEP))}
                                >
                                    <Icon data={Minus} size={20} />
                                </Button>
                            </Tooltip>
                            <Button
                                view="flat-secondary"
                                onClick={() => setZoom(1)}
                                aria-label={t.reset}
                                className={styles.zoom}
                            >
                                {Math.round(zoom * 100)}%
                            </Button>
                            <Tooltip content={t.zoomIn}>
                                <Button
                                    view="flat-secondary"
                                    disabled={zoom >= MAX_ZOOM}
                                    aria-label={t.zoomIn}
                                    onClick={() => setZoom((value) => Math.min(MAX_ZOOM, value + ZOOM_STEP))}
                                >
                                    <Icon data={Plus} size={20} />
                                </Button>
                            </Tooltip>
                        </>
                    )}
                    <Tooltip content={t.download}>
                        <Link
                            className={styles.download}
                            href={mediaSource(message)}
                            download={message.media?.fileName}
                            aria-label={t.download}
                        >
                            <Icon data={ArrowDownToLine} size={20} />
                        </Link>
                    </Tooltip>
                    <Tooltip content={t.fullscreen}>
                        <Button
                            view="flat-secondary"
                            aria-label={t.fullscreen}
                            onClick={() => void fullscreen()}
                        >
                            <Icon data={ArrowsExpand} size={20} />
                        </Button>
                    </Tooltip>
                    <Button view="flat-secondary" aria-label={t.close} onClick={onClose}>
                        <Icon data={Xmark} size={22} />
                    </Button>
                </Flex>
            </Flex>
            <Box className={styles.stage}>
                <MediaCanvas key={message.id} message={message} zoom={zoom} />
                {index > 0 && (
                    <Button
                        className={`${styles.navigation} ${styles.previous}`}
                        view="flat-secondary"
                        size="xl"
                        aria-label={t.previous}
                        onClick={() => move(-1)}
                    >
                        <Icon data={ArrowLeft} size={24} />
                    </Button>
                )}
                {index < gallery.length - 1 && (
                    <Button
                        className={`${styles.navigation} ${styles.next}`}
                        view="flat-secondary"
                        size="xl"
                        aria-label={t.next}
                        onClick={() => move(1)}
                    >
                        <Icon data={ArrowRight} size={24} />
                    </Button>
                )}
            </Box>
            <Flex className={styles.footer} direction="column" alignItems="center" gap={3}>
                {message.text && (
                    <RichText text={message.text} entities={message.entities} className={styles.caption} />
                )}
                {fullscreenError && <Text role="alert">{fullscreenError}</Text>}
                {gallery.length > 1 && (
                    <Flex
                        className={styles.filmstrip}
                        gap={2}
                        aria-label={`${index + 1} / ${gallery.length}`}
                    >
                        {gallery.map((item, position) => (
                            <Button
                                key={item.id}
                                className={`${styles.thumbnail} ${item.id === message.id ? styles.selected : ''}`}
                                view="flat"
                                aria-label={`${item.media?.mimeType.startsWith('video/') ? t.video : t.photo} ${position + 1}`}
                                aria-pressed={item.id === message.id}
                                onClick={() => {
                                    setSelected(item.id);
                                    setZoom(1);
                                }}
                            >
                                {mediaSource(item, true) ? (
                                    <Box as="img" src={mediaSource(item, true)} alt="" loading="lazy" />
                                ) : (
                                    <Text>{position + 1}</Text>
                                )}
                            </Button>
                        ))}
                    </Flex>
                )}
                <Text className={styles.secondary}>
                    {index + 1} / {gallery.length}
                </Text>
            </Flex>
        </Flex>
    );
}
