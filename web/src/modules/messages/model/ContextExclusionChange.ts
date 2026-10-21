import type {components} from '@/shared/api/schema';
export type ContextExclusionChange =
    | ({messageId: string} & components['schemas']['ContextExclusionUpdate'])
    | {reset: true};
