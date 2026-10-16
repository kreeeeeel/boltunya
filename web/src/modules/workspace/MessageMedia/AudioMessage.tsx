import {Box, Button, Flex, Icon, Text} from '@gravity-ui/uikit';
import {Pause, Play} from '@gravity-ui/icons';
import {useTranslations} from 'next-intl';
import {useEffect, useRef, useState} from 'react';
import type {Message} from '@/shared/api/client';
import {audioTime, decodeWaveform} from '@/modules/workspace/MessageMedia/waveform';
import styles from '@/modules/workspace/MessageMedia/AudioMessage.module.css';

export function AudioMessage({message}: {readonly message: Message}) {
    const t = useTranslations();
    const root = useRef<HTMLDivElement>(null);
    const [playing, setPlaying] = useState(false);
    const [position, setPosition] = useState(0);
    const [duration, setDuration] = useState(message.media?.duration ?? 0);
    const [error, setError] = useState(false);
    useEffect(() => {
        const player = root.current?.querySelector('audio');
        return () => player?.pause();
    }, []);
    const samples = decodeWaveform(message.media?.waveform ?? []);
    const bars = Array.from({length: 32}, (_, index) =>
        samples.length
            ? Math.max(
                  2,
                  ...samples.slice(
                      Math.floor((index * samples.length) / 32),
                      Math.max(
                          Math.floor(((index + 1) * samples.length) / 32),
                          Math.floor((index * samples.length) / 32) + 1,
                      ),
                  ),
              )
            : 3,
    );
    function audio() {
        return root.current?.querySelector('audio');
    }
    function seek(fraction: number) {
        const element = audio();
        if (element && element.readyState > 0 && duration > 0) {
            element.currentTime = Math.max(0, Math.min(1, fraction)) * duration;
        }
    }
    async function toggle() {
        const element = audio();
        if (!element) {
            return;
        }
        try {
            if (element.paused) {
                await element.play();
            } else {
                element.pause();
            }
            setError(false);
        } catch {
            setError(true);
        }
    }
    return (
        <Box ref={root} className={styles.root}>
            <Box
                as="audio"
                src={`/api/media/${message.media?.fileId}`}
                preload="metadata"
                className={styles.audio}
                onPlay={() => setPlaying(true)}
                onPause={() => setPlaying(false)}
                onEnded={() => setPlaying(false)}
                onTimeUpdate={() => setPosition(audio()?.currentTime ?? 0)}
                onLoadedMetadata={() => {
                    const value = audio()?.duration;
                    if (value !== undefined && Number.isFinite(value)) {
                        setDuration(value);
                    }
                }}
                onError={() => setError(true)}
            />
            <Flex gap={3} alignItems="center">
                <Button
                    view="action"
                    className={styles.play}
                    aria-label={t(playing ? 'pauseAudio' : 'playAudio')}
                    onClick={() => void toggle()}
                >
                    <Icon data={playing ? Pause : Play} size={20} />
                </Button>
                <Flex direction="column" gap={1} className={styles.content}>
                    {message.kind === 'audio' && (
                        <Text className={styles.name}>{message.media?.fileName}</Text>
                    )}
                    <Box
                        role="slider"
                        tabIndex={0}
                        aria-label={t('audioPosition')}
                        aria-valuemin={0}
                        aria-valuemax={Math.round(duration)}
                        aria-valuenow={Math.round(position)}
                        className={styles.waveform}
                        onClick={(event) => {
                            const bounds = root.current
                                ?.querySelector('[role="slider"]')
                                ?.getBoundingClientRect();
                            if (bounds) {
                                seek((event.clientX - bounds.left) / bounds.width);
                            }
                        }}
                        onKeyDown={(event) => {
                            if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
                                event.preventDefault();
                                seek((position + (event.key === 'ArrowLeft' ? -5 : 5)) / (duration || 1));
                            }
                        }}
                    >
                        {bars.map((height, index) => (
                            <Box
                                key={index}
                                className={styles.bar}
                                style={{
                                    height: 3 + height * 0.7,
                                    opacity: index / 32 < position / (duration || 1) ? 1 : 0.4,
                                }}
                            />
                        ))}
                    </Box>
                    <Text className={styles.time}>
                        {audioTime(playing || position > 0 ? position : duration)}
                    </Text>
                </Flex>
            </Flex>
            {error && <Text color="danger">{t('mediaError')}</Text>}
        </Box>
    );
}
