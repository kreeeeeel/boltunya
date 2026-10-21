import type {ChatRule} from '@/shared/api/client';
import {communicationStyles} from '@/modules/workspace/model/communicationStyles';

export function normalizeCommunicationRule(rule: ChatRule, presetIds: readonly string[]): ChatRule {
    if (
        rule.proactiveMode === undefined ||
        rule.proactiveIntervalMinutes === undefined ||
        rule.proactiveMessageMode === undefined ||
        rule.proactiveText === undefined ||
        rule.replyInterruptionMode === undefined
    ) {
        rule = {
            ...rule,
            proactiveMode: rule.proactiveMode ?? 'inherit',
            proactiveIntervalMinutes: rule.proactiveIntervalMinutes ?? 0,
            proactiveMessageMode: rule.proactiveMessageMode ?? 'ai',
            proactiveText: rule.proactiveText ?? '',
            replyInterruptionMode: rule.replyInterruptionMode ?? 'inherit',
        };
    }
    if (rule.styleId && presetIds.includes(rule.styleId)) {
        return rule.style === 'custom' ? rule : {...rule, style: 'custom'};
    }
    if (rule.style === 'custom' || rule.styleId) {
        const style = communicationStyles.find((item) => item.value === rule.style)?.value ?? 'friendly';
        return {...rule, style, styleId: ''};
    }
    return rule;
}
