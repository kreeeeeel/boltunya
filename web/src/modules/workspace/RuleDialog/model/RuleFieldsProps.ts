import type {ChatRule} from '@/shared/api/client';

export interface RuleFieldsProps {
    readonly value: ChatRule;
    readonly onUpdate: (rule: ChatRule) => void;
}
