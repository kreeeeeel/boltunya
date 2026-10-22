import {
    ActivityApi,
    ChatsApi,
    MediaApi,
    MemoryApi,
    ModelsApi,
    SettingsApi,
    SpeechApi,
    SystemApi,
    TelegramApi,
} from '@aigram/api-client';
import type * as Contract from '@aigram/api-client';
import {apiConfiguration} from '@/shared/api/configuration';
import type {DesktopWindowState} from '@/shared/desktop/bridge';

export const api = {
    activity: new ActivityApi(apiConfiguration),
    chats: new ChatsApi(apiConfiguration),
    media: new MediaApi(apiConfiguration),
    memory: new MemoryApi(apiConfiguration),
    models: new ModelsApi(apiConfiguration),
    settings: new SettingsApi(apiConfiguration),
    speech: new SpeechApi(apiConfiguration),
    system: new SystemApi(apiConfiguration),
    telegram: new TelegramApi(apiConfiguration),
};

export type Chat = Contract.Chat;
export type MessageEntity = Contract.MessageEntity;
export type Message = Contract.Message;
export type ChatRule = Contract.ChatRule;
export type AppState = Contract.State & {window?: DesktopWindowState};
export type Reply = Contract.Reply;
export type Engine = Contract.Engine;
export type Telegram = Contract.Telegram;
export type Activity = Contract.Activity;
export type ActivityDetails = Contract.ActivityDetails;
export type ActivityAttempt = Contract.ActivityAttempt;
export type ReplyQueue = Contract.ReplyQueue;

export function errorMessage(error: unknown): string {
    return error instanceof Error ? error.message : String(error);
}

export const defaultRule: ChatRule = {
    enabled: false,
    style: 'friendly',
    model: 'qwen3:1.7b',
    modelProfileId: '',
    deliveryMode: 'inherit',
    analyzePhotos: false,
    aiStickers: false,
    voiceSettingsMode: 'inherit',
    voiceInputEnabled: false,
    voiceReplyMode: 'text',
    voiceReplyProbability: 50,
    voiceRecognitionModel: 'whisper-tiny',
    voiceSynthesisModel: 'piper-ru-irina',
    voiceSpeechRate: 1,
    useDefaultModel: true,
    instructions: '',
    contextMessages: 20,
    maxTokens: 128,
    delaySeconds: 0,
    creativity: 60,
    emoji: true,
    matchMyStyle: true,
    useMemory: true,
    proactiveMode: 'inherit',
    proactiveIntervalMinutes: 0,
    proactiveMessageMode: 'ai',
    proactiveText: '',
    replyInterruptionMode: 'inherit',
};
