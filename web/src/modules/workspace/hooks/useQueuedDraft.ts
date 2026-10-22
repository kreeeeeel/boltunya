import {useCallback, useEffect, useRef} from 'react';
import {useTranslations} from 'next-intl';
import {api} from '@/shared/api/client';
import type {ChatRule} from '@/shared/api/client';
import type {PreviewJob, PreviewJobRequest} from '@aigram/api-client';
import {DraftPreparationError} from '@/modules/workspace/model/DraftPreparationError';
const POLL_INTERVAL_MS = 750;
function nextPoll(signal: AbortSignal) {
    return new Promise<void>((resolve, reject) => {
        if (signal.aborted) {
            reject(new DOMException('Aborted', 'AbortError'));
            return;
        }
        const abort = () => {
            window.clearTimeout(timer);
            reject(new DOMException('Aborted', 'AbortError'));
        };
        const timer = window.setTimeout(() => {
            signal.removeEventListener('abort', abort);
            resolve();
        }, POLL_INTERVAL_MS);
        signal.addEventListener('abort', abort, {once: true});
    });
}
export function useQueuedDraft(chatId: string | undefined, onRefresh: () => Promise<void>) {
    const t = useTranslations('draftQueue');
    const pending = useRef<AbortController | null>(null);
    useEffect(() => () => pending.current?.abort(), [chatId]);
    const execute = useCallback(
        async (enqueue: (signal: AbortSignal) => Promise<PreviewJob>) => {
            if (!chatId) {
                throw new DOMException('Aborted', 'AbortError');
            }
            pending.current?.abort();
            const controller = new AbortController();
            pending.current = controller;
            const signal = controller.signal;
            try {
                let job = await enqueue(signal);
                await onRefresh();
                while (job.status === 'queued' || job.status === 'generating') {
                    await nextPoll(signal);
                    job = await api.models.previewJob(
                        {
                            id: job.id,
                        },
                        {
                            signal,
                        },
                    );
                }
                await onRefresh();
                signal.throwIfAborted();
                if (job.status === 'cancelled') {
                    throw new DraftPreparationError(t('cancelled'), 'cancelled');
                }
                if (job.status === 'error' || !job.result?.text) {
                    throw new DraftPreparationError(job.error || t('failed'), 'error');
                }
                return job.result;
            } finally {
                if (pending.current === controller) {
                    pending.current = null;
                }
            }
        },
        [chatId, onRefresh, t],
    );
    const generate = useCallback(
        (rule: ChatRule, messageId?: string, output: PreviewJobRequest['output'] = 'text') =>
            execute((signal) =>
                api.models.enqueuePreviewJob(
                    {
                        previewJobRequest: {
                            chatId: chatId ?? '',
                            rule,
                            ...(messageId ? {messageId} : {}),
                            ...(output === 'voice' ? {output} : {}),
                        },
                    },
                    {signal},
                ),
            ),
        [chatId, execute],
    );
    const synthesize = useCallback(
        (text: string) =>
            execute((signal) =>
                api.speech.enqueueVoiceDraft({voiceDraftRequest: {chatId: chatId ?? '', text}}, {signal}),
            ),
        [chatId, execute],
    );
    return {generate, synthesize};
}
