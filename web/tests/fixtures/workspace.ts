import {testChats} from './chats';
import type {AppState} from '@/shared/api/client';

export const workspaceState: AppState = {
    telegram: {state: 'ready', name: '', prompt: '', error: ''},
    engine: {
        online: true,
        downloading: '',
        progress: 0,
        error: '',
        warming: '',
        models: [
            {
                id: 'qwen3:0.6b',
                name: 'Qwen 3 · 0.6B',
                description: '',
                size: '522 МБ',
                installed: true,
                recommended: false,
            },
            {
                id: 'qwen3:1.7b',
                name: 'Qwen 3 · 1.7B',
                description: '',
                size: '1.4 ГБ',
                installed: true,
                recommended: false,
            },
            {
                id: 'gemma3:4b',
                name: 'Gemma 3 · 4B',
                description: '',
                size: '3.4 ГБ',
                installed: false,
                recommended: true,
            },
        ],
    },
    settings: {
        globalPrompt: '',
        paused: true,
        defaultModel: 'qwen3:1.7b',
        rules: {},
        replyDelaySeconds: 3,
        replyIntervalSeconds: 3,
        showTyping: true,
        memoryEnabled: true,
        memoryIntervalHours: 6,
        closeToTray: true,
        launchAtLogin: false,
        resumeOnStartup: false,
    },
    chats: testChats,
    activity: [],
};
