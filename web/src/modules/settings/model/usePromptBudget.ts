import {useEffect, useState} from 'react';
import {api, errorMessage} from '@/shared/api/client';
import type * as Contract from '@aigram/api-client';
export type PromptBudgetRequest = Contract.PromptBudgetRequest;
export type PromptBudgetReport = Contract.PromptBudgetReport;
const BUDGET_DEBOUNCE_MS = 350;
export function usePromptBudget(request: PromptBudgetRequest) {
    const key = JSON.stringify(request);
    const [result, setResult] = useState<{
        key: string;
        report?: PromptBudgetReport;
        error: string;
    }>();
    useEffect(() => {
        const controller = new AbortController();
        const timer = setTimeout(() => {
            void api.models
                .estimatePromptBudget(
                    {
                        promptBudgetRequest: JSON.parse(key) as PromptBudgetRequest,
                    },
                    {
                        signal: controller.signal,
                    },
                )
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
