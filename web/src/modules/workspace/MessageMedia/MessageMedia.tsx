import {Box, Button, Flex, Icon, Link, Skeleton, Text} from '@gravity-ui/uikit';
import {ArrowDownToLine, File, ArrowsExpand} from '@gravity-ui/icons';
import {useTranslations} from 'next-intl';
import {useState} from 'react';

import type {Message} from '@/shared/api/client';
import {AudioMessage} from '@/modules/workspace/MessageMedia/AudioMessage';
import {AnimatedSticker} from '@/modules/workspace/MessageMedia/AnimatedSticker';
import styles from '@/modules/workspace/MessageMedia/MessageMedia.module.css';
import {DeferredMedia} from '@/modules/workspace/MessageMedia/DeferredMedia';
import {useMediaViewer} from '@/modules/media/MediaViewer/MediaViewerProvider';
import {MediaViewerOverlay} from '@/modules/media/MediaViewer/MediaViewerOverlay';

export function MessageMedia({message}: {readonly message: Message}) {
    const t = useTranslations();
    const [loaded, setLoaded] = useState(false);
    const [failed, setFailed] = useState(false);
    const [expanded, setExpanded] = useState(false);
    const [requested, setRequested] = useState(false);
    const viewer = useMediaViewer();
    const open = () => (viewer ? viewer(message) : setExpanded(true));
    const media = message.media;
    if (!media) {
        return message.kind === 'text' ? null : <Text color="secondary">{t('unsupportedMedia')}</Text>;
    }
    if (media.autoDownload === false && !media.downloaded && !requested) {
        return <DeferredMedia media={media} onDownloaded={() => setRequested(true)} />;
    }
    const source = `/api/media/${media.fileId}${media.cacheVersion ? '?v=' + media.cacheVersion : ''}`;
    const sticker = message.kind === 'sticker';
    if (media.mimeType === 'application/x-tgsticker') {
        return <AnimatedSticker fileId={media.fileId} />;
    }
    const image = media.mimeType.startsWith('image/');
    const video = media.mimeType.startsWith('video/');
    const audio = media.mimeType.startsWith('audio/');
    const scale = Math.min(1, 360 / (media.width || 360), 360 / (media.height || 360));
    const width = sticker ? 180 : (media.width || 360) * scale;
    const height = sticker ? 180 : (media.height || 360) * scale;
    return (
        <Box className={styles.root}>
            {image && (
                <>
                    <Box
                        className={styles.picture}
                        style={{
                            width: sticker ? width : '100%',
                            maxWidth: '100%',
                            aspectRatio: `${width} / ${height}`,
                        }}
                    >
                        {!loaded && !failed && (
                            <Skeleton className={styles.imageSkeleton} width="100%" height="100%" />
                        )}
                        <Box
                            as="img"
                            src={source}
                            alt={t(sticker ? 'sticker' : 'photo')}
                            loading="lazy"
                            className={styles.image}
                            onLoad={() => setLoaded(true)}
                            onError={() => setFailed(true)}
                            style={{opacity: loaded ? 1 : 0}}
                        />
                        {!sticker && (
                            <Button
                                view="flat"
                                className={styles.photoButton}
                                onClick={open}
                                aria-label={t(sticker ? 'sticker' : 'openPhoto')}
                            />
                        )}
                    </Box>
                </>
            )}
            {video && (
                <Box className={styles.videoContainer}>
                    <Box
                        as="video"
                        src={source}
                        controls={!sticker && message.kind !== 'animation'}
                        autoPlay={sticker || message.kind === 'animation'}
                        muted={sticker || message.kind === 'animation'}
                        loop={sticker || message.kind === 'animation'}
                        playsInline
                        preload="metadata"
                        poster={media.thumbnailFileId ? `/api/media/${media.thumbnailFileId}` : undefined}
                        className={
                            sticker
                                ? styles.sticker
                                : message.kind === 'videoNote'
                                  ? styles.videoNote
                                  : styles.video
                        }
                        onError={() => setFailed(true)}
                    />
                    {!sticker && (
                        <Button
                            view="flat-secondary"
                            className={styles.expandVideo}
                            aria-label={t('openPhoto')}
                            onClick={open}
                        >
                            <Icon data={ArrowsExpand} size={18} />
                        </Button>
                    )}
                </Box>
            )}
            {audio && <AudioMessage message={message} />}
            {!image && !video && !audio && (
                <Flex gap={3} alignItems="center" className={styles.file}>
                    <Icon data={File} size={28} />
                    <Flex direction="column" gap={1}>
                        <Text>{media.fileName}</Text>
                        <Link href={source} download={media.fileName}>
                            <Icon data={ArrowDownToLine} size={14} /> {t('downloadFile')}
                        </Link>
                    </Flex>
                </Flex>
            )}
            {failed && <Text color="danger">{t('mediaError')}</Text>}
            {expanded && (
                <MediaViewerOverlay
                    messages={[message]}
                    initialId={message.id}
                    onClose={() => setExpanded(false)}
                />
            )}
        </Box>
    );
}
