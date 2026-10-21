import {useEffect, useState} from 'react';
import {api, dataOrThrow, errorMessage} from '@/shared/api/client';
import type {components} from '@/shared/api/schema';

export type PromptBudgetRequest = components['schemas']['PromptBudgetRequest'];
export type PromptBudgetReport = components['schemas']['PromptBudgetReport'];
const BUDGET_DEBOUNCE_MS = 350;

export function usePromptBudget(request: PromptBudgetRequest) {
    const key = JSON.stringify(request);
    const [result, setResult] = useState<{key: string; report?: PromptBudgetReport; error: string}>();
    useEffect(() => {
        const controller = new AbortController();
        const timer = setTimeout(() => {
            void api
                .POST('/api/prompts/budget', {
                    body: JSON.parse(key) as PromptBudgetRequest,
                    signal: controller.signal,
                })
                .then(dataOrThrow)
                .then((report) => {
                    if (!controller.signal.aborted) {
                        setResult({key, report, error: ''});
                    }
                })
                .catch((cause) => {
                    if (!controller.signal.aborted) {
                        setResult({key, error: errorMessage(cause)});
                    }
                });
        }, BUDGET_DEBOUNCE_MS);
        return () => {
            clearTimeout(timer);
            controller.abort();
        };
    }, [key]);
    return {
        report: result?.key === key ? result.report : undefined,
        error: result?.key === key ? result.error : '',
        loading: result?.key !== key,
    };
}
