import type {ChatRule} from '@/shared/api/client';
import {communicationStyles} from '@/modules/workspace/model/communicationStyles';

export function normalizeCommunicationRule(rule: ChatRule, presetIds: readonly string[]): ChatRule {
    if (rule.styleId && presetIds.includes(rule.styleId)) {
        return rule.style === 'custom' ? rule : {...rule, style: 'custom'};
    }
    if (rule.style === 'custom' || rule.styleId) {
        const style = communicationStyles.find((item) => item.value === rule.style)?.value ?? 'friendly';
        return {...rule, style, styleId: ''};
    }
    return rule;
}
