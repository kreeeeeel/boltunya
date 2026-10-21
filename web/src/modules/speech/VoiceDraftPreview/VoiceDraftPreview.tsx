import {ArrowRotateRight, PaperPlane, Xmark} from '@gravity-ui/icons';
import {Box, Button, Flex, Icon, Text, TextArea} from '@gravity-ui/uikit';
import {useEffect, useRef, useState} from 'react';
import {useTranslations} from 'next-intl';
import type {GeneratedVoice, Reply} from '@aigram/api-client';
import {errorMessage} from '@/shared/api/client';
import {SpeechPlayer} from '@/modules/speech/VoiceDraftPreview/SpeechPlayer';
import styles from '@/modules/speech/VoiceDraftPreview/VoiceDraftPreview.module.css';

export function VoiceDraftPreview({
    draft,
    onSynthesize,
    onSend,
    onDiscard,
}: {
    readonly draft: Reply & {voice: GeneratedVoice};
    readonly onSynthesize: (text: string) => Promise<Reply>;
    readonly onSend: (voiceId: string) => Promise<void>;
    readonly onDiscard: () => void;
}) {
    const t = useTranslations('voice');
    const [voice, setVoice] = useState(draft.voice);
    const [spokenText, setSpokenText] = useState(draft.text);
    const [text, setText] = useState(draft.text);
    const [busy, setBusy] = useState<'synthesis' | 'send' | ''>('');
    const [error, setError] = useState('');
    const mounted = useRef(true);
    useEffect(() => {
        mounted.current = true;
        return () => {
            mounted.current = false;
        };
    }, []);
    const changed = text !== spokenText;
    const tooLong = text.length > 4096;

    async function synthesize() {
        if (busy || !text.trim() || tooLong) {
            return;
        }
        setBusy('synthesis');
        setError('');
        try {
            const result = await onSynthesize(text);
            if (!result.voice) {
                throw new Error(t('generationFailed'));
            }
            if (mounted.current) {
                setVoice(result.voice);
                setSpokenText(result.text);
                setText(result.text);
            }
        } catch (cause) {
            if (mounted.current) {
                setError(errorMessage(cause));
            }
        } finally {
            if (mounted.current) {
                setBusy('');
            }
        }
    }

    async function send() {
        if (busy || changed || !text.trim() || tooLong) {
            return;
        }
        setBusy('send');
        setError('');
        try {
            await onSend(voice.id);
            if (mounted.current) {
                onDiscard();
            }
        } catch (cause) {
            if (mounted.current) {
                setError(errorMessage(cause));
            }
        } finally {
            if (mounted.current) {
                setBusy('');
            }
        }
    }

    return (
        <Flex direction="column" gap={3} className={styles.root} qa="voice-draft">
            <Flex alignItems="center" justifyContent="space-between" gap={3}>
                <Text variant="subheader-2">{t('draftTitle')}</Text>
                <Button
                    size="s"
                    view="flat-secondary"
                    disabled={!!busy}
                    onClick={onDiscard}
                    aria-label={t('discardDraft')}
                >
                    <Icon data={Xmark} size={16} />
                </Button>
            </Flex>
            {!changed && <SpeechPlayer key={voice.id} voice={voice} />}
            {changed && (
                <Text role="status" color="secondary">
                    {t('transcriptChanged')}
                </Text>
            )}
            <Box>
                <Text color="secondary">{t('transcript')}</Text>
                <TextArea
                    value={text}
                    onUpdate={setText}
                    disabled={!!busy}
                    minRows={2}
                    maxRows={5}
                    controlProps={{'aria-label': t('transcript')}}
                />
            </Box>
            {tooLong && (
                <Text color="danger" role="alert">
                    {t('transcriptTooLong')}
                </Text>
            )}
            {error && (
                <Text color="danger" role="alert">
                    {error}
                </Text>
            )}
            <Flex gap={2} wrap="wrap" justifyContent="flex-end">
                <Button
                    size="m"
                    view="flat-info"
                    disabled={!!busy || !text.trim() || tooLong}
                    loading={busy === 'synthesis'}
                    onClick={() => void synthesize()}
                >
                    <Icon data={ArrowRotateRight} size={16} />
                    {t(busy === 'synthesis' ? 'synthesizing' : 'regenerate')}
                </Button>
                <Button
                    size="m"
                    view="action"
                    disabled={!!busy || changed || !text.trim() || tooLong}
                    loading={busy === 'send'}
                    onClick={() => void send()}
                >
                    <Icon data={PaperPlane} size={16} />
                    {t('sendDraft')}
                </Button>
            </Flex>
        </Flex>
    );
}
