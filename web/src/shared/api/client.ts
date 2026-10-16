import createClient from 'openapi-fetch';

import type {components, paths} from '@/shared/api/schema';
import type {DesktopWindowState} from '@/shared/desktop/bridge';

export const api = createClient<paths>({baseUrl: '', credentials: 'same-origin'});

export type Chat = components['schemas']['Chat'];
export type MessageEntity = components['schemas']['MessageEntity'];
export type Message = components['schemas']['Message'];
export type ChatRule = components['schemas']['ChatRule'];
export type AppState = components['schemas']['State'] & {window?: DesktopWindowState};
export type Reply = components['schemas']['Reply'];
export type Engine = components['schemas']['Engine'];
export type Telegram = components['schemas']['Telegram'];

export function dataOrThrow<T>(result: {data?: T; error?: {message: string}; response: Response}): T {
    if (result.error || result.data === undefined) {
        throw new Error(result.error?.message ?? `HTTP ${result.response.status}`);
    }
    return result.data;
}

export function errorMessage(error: unknown): string {
    return error instanceof Error ? error.message : String(error);
}

export const defaultRule: ChatRule = {
    enabled: false,
    style: 'friendly',
    model: 'qwen3:1.7b',
    instructions: '',
    contextMessages: 20,
    maxTokens: 128,
    delaySeconds: 0,
    creativity: 60,
    emoji: true,
    internetAccess: true,
    sourceLinks: 'requested',
    matchMyStyle: true,
    useMemory: true,
};
