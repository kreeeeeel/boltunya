import type * as Contract from '@aigram/api-client';
export type ContextExclusionChange =
    | ({
          messageId: string;
      } & Contract.ContextExclusionUpdate)
    | {
          reset: true;
      };
