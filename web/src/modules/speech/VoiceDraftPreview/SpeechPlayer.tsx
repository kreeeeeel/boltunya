import {Pause, Play} from '@gravity-ui/icons';
import {Box, Button, Flex, Icon, Slider, Text} from '@gravity-ui/uikit';
import {useEffect, useRef, useState} from 'react';
import {useTranslations} from 'next-intl';
import type {GeneratedVoice} from '@aigram/api-client';
import {audioTime} from '@/modules/workspace/MessageMedia/waveform';
import styles from '@/modules/speech/VoiceDraftPreview/VoiceDraftPreview.module.css';

export function SpeechPlayer({voice}: {readonly voice: GeneratedVoice}) {
    const t = useTranslations('voice');
    const player = useRef<HTMLAudioElement>(null);
    const [playing, setPlaying] = useState(false);
    const [position, setPosition] = useState(0);
    const [error, setError] = useState(false);
    useEffect(() => {
        const audio = player.current;
        return () => audio?.pause();
    }, []);

    async function toggle() {
        const audio = player.current;
        if (!audio) {
            return;
        }
        try {
            if (audio.paused) {
                await audio.play();
            } else {
                audio.pause();
            }
            setError(false);
        } catch {
            setError(true);
        }
    }

    return (
        <Flex direction="column" gap={2} qa="speech-player">
            <Box
                as="audio"
                hidden
                ref={player}
                src={voice.url}
                preload="metadata"
                onPlay={() => setPlaying(true)}
                onPause={() => setPlaying(false)}
                onEnded={() => setPlaying(false)}
                onTimeUpdate={() => setPosition(player.current?.currentTime ?? 0)}
                onError={() => setError(true)}
            />
            <Flex gap={3} alignItems="center">
                <Button
                    view="action"
                    className={styles.play}
                    onClick={() => void toggle()}
                    aria-label={t(playing ? 'pause' : 'play')}
                >
                    <Icon data={playing ? Pause : Play} size={20} />
                </Button>
                <Flex direction="column" gap={1} className={styles.timeline}>
                    <Slider
                        min={0}
                        max={Math.max(1, voice.duration)}
                        step={0.1}
                        marks={[]}
                        value={position}
                        aria-label={t('position')}
                        onUpdate={(seconds) => {
                            if (player.current) {
                                player.current.currentTime = seconds;
                                setPosition(seconds);
                            }
                        }}
                    />
                    <Text color="secondary">
                        {audioTime(position)} / {audioTime(voice.duration)}
                    </Text>
                </Flex>
            </Flex>
            {error && (
                <Text color="danger" role="alert">
                    {t('playFailed')}
                </Text>
            )}
        </Flex>
    );
}
