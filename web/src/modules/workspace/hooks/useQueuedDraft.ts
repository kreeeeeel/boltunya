import {useEffect, useRef} from 'react';
import {useTranslations} from 'next-intl';
import {api, dataOrThrow} from '@/shared/api/client';
import type {ChatRule} from '@/shared/api/client';

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
            let job = dataOrThrow(
                await api.POST('/api/preview/jobs', {
                    body: {chatId, rule, ...(messageId ? {messageId} : {})},
                    signal,
                }),
            );
            await onRefresh();
            while (job.status === 'queued' || job.status === 'generating') {
                await nextPoll(signal);
                job = dataOrThrow(
                    await api.GET('/api/preview/jobs/{id}', {params: {path: {id: job.id}}, signal}),
                );
            }
            await onRefresh();
            signal.throwIfAborted();
            if (job.status === 'cancelled') {
                throw new Error(t('cancelled'));
            }
            if (job.status === 'error' || !job.result?.text) {
                throw new Error(job.error || t('failed'));
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
