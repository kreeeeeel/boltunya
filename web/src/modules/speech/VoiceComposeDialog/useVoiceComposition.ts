import {useEffect, useRef, useState} from 'react';
import {useTranslations} from 'next-intl';
import type {GeneratedVoice} from '@aigram/api-client';
import {api} from '@/shared/api/client';
import {useSpeechState} from '@/modules/speech/model/useSpeechState';
import {useSpeechPreview} from '@/modules/speech/model/useSpeechPreview';
import {speechReady} from '@/modules/speech/model/speechReady';
import type {VoiceComposeDialogProps} from '@/modules/speech/VoiceComposeDialog/VoiceComposeDialog.types';

export function useVoiceComposition({
    chatId,
    defaultModelId,
    defaultSpeed = 1,
    onClose,
    onSent,
    onSettings,
}: VoiceComposeDialogProps) {
    const t = useTranslations('voice');
    const speech = useSpeechState();
    const preview = useSpeechPreview(chatId);
    const [initialModelId] = useState(defaultModelId);
    const [selection, setSelection] = useState('');
    const [speed, setSpeed] = useState(defaultSpeed);
    const [text, setText] = useState('');
    const [submitted, setSubmitted] = useState({text: '', modelId: '', speed: 0});
    const [sending, setSending] = useState(false);
    const [autoSending, setAutoSending] = useState(false);
    const [refreshing, setRefreshing] = useState(false);
    const [error, setError] = useState('');
    const [warning, setWarning] = useState('');
    const [delivery, setDelivery] = useState<Record<string, 'sending' | 'sent' | 'unconfirmed'>>({});
    const attempted = useRef(new Set<string>());
    const sendingRef = useRef(false);
    const generationRef = useRef(false);
    const intention = useRef<object | null>(null);
    const closed = useRef(false);
    const mounted = useRef(false);
    const models =
        speech.state?.assets.filter((asset) => asset.kind === 'synthesis' && asset.installed) ?? [];
    const modelId =
        selection || models.find((asset) => asset.id === initialModelId)?.id || models[0]?.id || '';
    const ready = speechReady(speech.state?.assets ?? [], modelId);
    const tooLong = text.length > 4096;
    const current = text === submitted.text && modelId === submitted.modelId && speed === submitted.speed;
    const voice = current ? preview.voice : undefined;
    const delivered = !!voice && delivery[voice.id] === 'sent';
    const blocked = !!voice && !!delivery[voice.id];

    useEffect(() => {
        mounted.current = true;
        closed.current = false;
        return () => {
            mounted.current = false;
            closed.current = true;
            intention.current = null;
            generationRef.current = false;
        };
    }, []);

    function close() {
        if (!sendingRef.current) {
            closed.current = true;
            intention.current = null;
            onClose();
        }
    }

    function clearIntention() {
        intention.current = null;
        setAutoSending(false);
    }

    async function synthesize(sendAfter = false) {
        if (
            closed.current ||
            sendingRef.current ||
            generationRef.current ||
            preview.busy ||
            !ready ||
            !text.trim() ||
            tooLong
        ) {
            return;
        }
        generationRef.current = true;
        setSelection(modelId);
        setError('');
        setWarning('');
        setSubmitted({text, modelId, speed});
        const intent = sendAfter ? {} : null;
        intention.current = intent;
        setAutoSending(sendAfter);
        await preview.run(
            {text, modelId, speed},
            {
                onReady: (readyVoice) => {
                    if (intent && intention.current === intent && mounted.current && !closed.current) {
                        intention.current = null;
                        void deliver(readyVoice);
                    }
                },
                onSettled: () => {
                    generationRef.current = false;
                    if (mounted.current && !closed.current) {
                        intention.current = null;
                        setAutoSending(false);
                    }
                },
            },
        );
    }

    async function deliver(voice: GeneratedVoice) {
        if (attempted.current.has(voice.id) || sendingRef.current || closed.current || !mounted.current) {
            return;
        }
        attempted.current.add(voice.id);
        setDelivery((previous) => ({...previous, [voice.id]: 'sending'}));
        sendingRef.current = true;
        setSending(true);
        setError('');
        setWarning('');
        try {
            await api.speech.sendVoiceDraft({chatId, voiceSendRequest: {voiceId: voice.id}});
            if (mounted.current) {
                setDelivery((previous) => ({...previous, [voice.id]: 'sent'}));
            }
            try {
                await onSent();
            } catch {
                if (mounted.current) {
                    setWarning(t('manualRefreshFailed'));
                }
            }
        } catch {
            if (mounted.current) {
                setDelivery((previous) => ({...previous, [voice.id]: 'unconfirmed'}));
                setError(t('manualSendUnconfirmed'));
            }
        } finally {
            sendingRef.current = false;
            if (mounted.current) {
                setSending(false);
            }
        }
    }

    async function send() {
        if (
            closed.current ||
            sendingRef.current ||
            generationRef.current ||
            preview.busy ||
            !ready ||
            !text.trim() ||
            tooLong
        ) {
            return;
        }
        if (voice && !blocked) {
            await deliver(voice);
        } else {
            await synthesize(true);
        }
    }

    async function refresh() {
        setRefreshing(true);
        try {
            await onSent();
            if (mounted.current) {
                setWarning('');
            }
        } catch {
            if (mounted.current) {
                setWarning(t('manualRefreshFailed'));
            }
        } finally {
            if (mounted.current) {
                setRefreshing(false);
            }
        }
    }

    function updateText(next: string) {
        clearIntention();
        setText(next);
    }

    function selectModel(id: string) {
        if (id) {
            clearIntention();
            setSelection(id);
        }
    }

    function updateSpeed(next: number) {
        clearIntention();
        setSpeed(next);
    }

    async function cancel() {
        clearIntention();
        await preview.cancel();
    }

    function openSettings() {
        if (!sendingRef.current) {
            close();
            onSettings?.();
        }
    }

    return {
        models,
        modelId,
        text,
        speed,
        ready,
        tooLong,
        loadingModels: !speech.state && !speech.error,
        loadedModels: !!speech.state,
        modelError: speech.error,
        reloadModels: speech.reload,
        sending,
        autoSending,
        refreshing,
        generating: preview.busy,
        cancelling: preview.cancelling,
        hasJob: !!preview.job?.id,
        status: preview.job?.status,
        voice,
        changedAudio: !current && !!preview.voice,
        delivered,
        error: error || preview.error,
        warning,
        canGenerate: !sending && !preview.busy && ready && !!text.trim() && !tooLong,
        canSend: !blocked && !sending && !preview.busy && ready && !!text.trim() && !tooLong,
        updateText,
        selectModel,
        updateSpeed,
        close,
        cancel,
        refresh,
        send,
        generate: () => synthesize(),
        openSettings: onSettings ? openSettings : undefined,
    };
}
