import {useEffect, useRef} from 'react';
import {useTranslations} from 'next-intl';
import {api} from '@/shared/api/client';
import type {ChatRule} from '@/shared/api/client';
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
    async function run(rule: ChatRule, messageId?: string) {
        if (!chatId) {
            return '';
        }
        pending.current?.abort();
        const controller = new AbortController();
        pending.current = controller;
        const signal = controller.signal;
        try {
            let job = await api.models.enqueuePreviewJob(
                {
                    previewJobRequest: {chatId, rule, ...(messageId ? {messageId} : {})},
                },
                {
                    signal,
                },
            );
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
            return job.result.text;
        } finally {
            if (pending.current === controller) {
                pending.current = null;
            }
        }
    }
    return run;
}
