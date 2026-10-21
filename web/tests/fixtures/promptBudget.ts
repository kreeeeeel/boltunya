import type * as Contract from '@aigram/api-client';
export function syntheticPromptBudget(
    request: Contract.PromptBudgetRequest = {},
): Contract.PromptBudgetReport {
    const model = request.rule?.model ?? 'qwen3:1.7b';
    const profileId = request.rule?.modelProfileId ?? '';
    const preferences = request.preferences ?? {
        model,
        profileId,
        instructions: '',
        contextWindow: null,
        tokenEncoding: 'auto' as const,
    };
    const contextWindow = preferences.contextWindow ?? 8192;
    const systemTokens =
        200 + Math.ceil(((request.globalPrompt?.length ?? 0) + (preferences.instructions?.length ?? 0)) / 4);
    const reservedOutputTokens = 512 + (request.rule?.maxTokens ?? 128);
    const remainingTokens = contextWindow - systemTokens - reservedOutputTokens;
    return {
        model,
        profileId,
        contextWindow,
        maxContextTokens: 32768,
        systemTokens,
        reservedOutputTokens,
        remainingTokens,
        fits: remainingTokens >= 0,
        estimated: true,
        includesHistory: false,
        preferences,
        tokenEncoding: preferences.tokenEncoding ?? 'auto',
    };
}
