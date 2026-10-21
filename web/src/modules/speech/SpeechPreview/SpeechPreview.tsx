import {Check, Xmark} from '@gravity-ui/icons';
import {Button, Flex, Icon, Loader, Text, TextArea} from '@gravity-ui/uikit';
import {useImperativeHandle, useRef, useState} from 'react';
import type {Ref} from 'react';
import type {SpeechAsset} from '@aigram/api-client';
import {useTranslations} from 'next-intl';
import {SpeechPlayer} from '@/modules/speech/VoiceDraftPreview/SpeechPlayer';
import {useSpeechPreview} from '@/modules/speech/model/useSpeechPreview';
import {speechReady} from '@/modules/speech/model/speechReady';
import styles from '@/modules/speech/SpeechPreview/SpeechPreview.module.css';

export interface SpeechPreviewHandle {
    readonly check: (modelId: string) => void;
}

export function SpeechPreview({
    modelId,
    speed,
    ready,
    assets,
    ref,
}: {
    readonly modelId: string;
    readonly speed: number;
    readonly ready: boolean;
    readonly assets?: SpeechAsset[];
    readonly ref?: Ref<SpeechPreviewHandle>;
}) {
    const t = useTranslations('voice');
    const [text, setText] = useState(() => t('previewSample'));
    const preview = useSpeechPreview();
    const input = useRef<HTMLTextAreaElement | null>(null);
    const [submitted, setSubmitted] = useState({text: '', modelId: '', speed: 0});
    const tooLong = text.length > 4096;
    const current = text === submitted.text && modelId === submitted.modelId && speed === submitted.speed;

    async function check(selected = modelId) {
        const selectedReady = selected === modelId ? ready : speechReady(assets ?? [], selected);
        if (preview.busy || !selectedReady || !text.trim() || tooLong) {
            input.current?.focus({preventScroll: true});
            return;
        }
        setSubmitted({text, modelId: selected, speed});
        await preview.run({text, modelId: selected, speed});
    }

    useImperativeHandle(ref, () => ({
        check: (selected) => {
            void check(selected);
        },
    }));

    return (
        <Flex direction="column" gap={3} className={styles.root} qa="speech-preview">
            <Text variant="subheader-2">{t('previewTitle')}</Text>
            <Text color="secondary">{t('previewHint')}</Text>
            <TextArea
                value={text}
                controlRef={input}
                onUpdate={setText}
                disabled={preview.busy}
                minRows={3}
                maxRows={7}
                controlProps={{'aria-label': t('previewText')}}
            />
            {!ready && <Text color="secondary">{t('modelsRequired')}</Text>}
            {tooLong && (
                <Text color="danger" role="alert">
                    {t('transcriptTooLong')}
                </Text>
            )}
            <Flex gap={2} wrap="wrap" alignItems="center">
                <Button
                    size="l"
                    view="flat-secondary"
                    disabled={preview.busy || !ready || !text.trim() || tooLong}
                    onClick={() => void check()}
                >
                    <Icon data={Check} size={20} />
                    {t('previewCheck')}
                </Button>
                {preview.busy && (
                    <>
                        <Flex gap={2} alignItems="center" role="status">
                            <Loader size="s" />
                            <Text>
                                {t(preview.job?.status === 'queued' ? 'previewQueued' : 'previewRunning')}
                            </Text>
                        </Flex>
                        <Button
                            size="l"
                            view="flat-secondary"
                            disabled={!preview.job?.id || preview.cancelling}
                            onClick={() => void preview.cancel()}
                        >
                            <Icon data={Xmark} size={20} />
                            {t('cancel')}
                        </Button>
                    </>
                )}
            </Flex>
            {preview.error && (
                <Text color="danger" role="alert">
                    {preview.error}
                </Text>
            )}
            {preview.job?.status === 'cancelled' && (
                <Text color="secondary" role="status">
                    {t('previewCancelled')}
                </Text>
            )}
            {current && preview.voice && !preview.busy && (
                <SpeechPlayer key={preview.voice.id} voice={preview.voice} />
            )}
        </Flex>
    );
}
