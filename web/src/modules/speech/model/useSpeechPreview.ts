import {useCallback, useEffect, useRef, useState} from 'react';
import {useTranslations} from 'next-intl';
import type {PreviewJob, SpeechPreviewRequest} from '@aigram/api-client';
import {api, errorMessage} from '@/shared/api/client';
import {ApiRequestError} from '@/shared/api/ApiRequestError';

const POLL_INTERVAL_MS = 750;

export function useSpeechPreview() {
    const t = useTranslations('voice');
    const [job, setJob] = useState<PreviewJob>();
    const [busy, setBusy] = useState(false);
    const [cancelling, setCancelling] = useState(false);
    const [error, setError] = useState('');
    const pending = useRef<AbortController | null>(null);
    const poll = useRef<ReturnType<typeof setTimeout> | null>(null);
    const activeJob = useRef<PreviewJob | undefined>(undefined);
    const cancellations = useRef(new Map<string, Promise<void>>());

    const cancelKnownJob = useCallback((id: string) => {
        const existing = cancellations.current.get(id);
        if (existing) {
            return existing;
        }
        const request = api.activity
            .cancelActivity({id})
            .then(() => undefined)
            .catch((cause) => {
                cancellations.current.delete(id);
                throw cause;
            });
        cancellations.current.set(id, request);
        return request;
    }, []);

    useEffect(
        () => () => {
            pending.current?.abort();
            if (poll.current) {
                clearTimeout(poll.current);
            }
            const current = activeJob.current;
            if (current && (current.status === 'queued' || current.status === 'generating')) {
                void cancelKnownJob(current.id).catch((cause) => {
                    console.error('Speech preview cancellation failed.', errorMessage(cause));
                });
            }
        },
        [cancelKnownJob],
    );

    async function run(request: SpeechPreviewRequest) {
        pending.current?.abort();
        if (poll.current) {
            clearTimeout(poll.current);
        }
        const controller = new AbortController();
        pending.current = controller;
        setBusy(true);
        setCancelling(false);
        setError('');

        async function receive(next: PreviewJob) {
            if (controller.signal.aborted) {
                return;
            }
            activeJob.current = next;
            setJob(next);
            if (next.status === 'queued' || next.status === 'generating') {
                poll.current = setTimeout(() => {
                    void api.models
                        .previewJob({id: next.id}, {signal: controller.signal})
                        .then(receive)
                        .catch(failed);
                }, POLL_INTERVAL_MS);
                return;
            }
            setBusy(false);
            if (next.status === 'error' || (next.status === 'ready' && !next.result?.voice)) {
                setError(next.error || t('previewFailed'));
            }
        }

        function failed(cause: unknown) {
            if (!controller.signal.aborted) {
                setBusy(false);
                setError(cause instanceof ApiRequestError ? t('previewFailed') : errorMessage(cause));
            }
        }

        try {
            const previous = activeJob.current;
            if (previous && (previous.status === 'queued' || previous.status === 'generating')) {
                setCancelling(true);
                try {
                    await cancelKnownJob(previous.id);
                } catch (cause) {
                    if (!controller.signal.aborted) {
                        setBusy(false);
                        setCancelling(false);
                        setError(errorMessage(cause));
                    }
                    return;
                }
            }
            controller.signal.throwIfAborted();
            activeJob.current = undefined;
            setJob(undefined);
            setCancelling(false);
            const next = await api.speech.previewSpeech(
                {speechPreviewRequest: request},
                {signal: controller.signal},
            );
            await receive(next);
        } catch (cause) {
            failed(cause);
        }
    }

    async function cancel() {
        if (!job?.id || cancelling) {
            return;
        }
        setCancelling(true);
        try {
            await cancelKnownJob(job.id);
        } catch (cause) {
            if (!pending.current?.signal.aborted) {
                setError(errorMessage(cause));
            }
        } finally {
            if (!pending.current?.signal.aborted) {
                setCancelling(false);
            }
        }
    }

    return {
        job,
        busy,
        cancelling,
        error,
        voice: job?.status === 'ready' ? job.result?.voice : undefined,
        run,
        cancel,
    };
}
