import {initializeTestTheme} from './settings';
import {speechCompute} from './speechCompute';
import {computeDevices} from './computeDevices';
import type {BrowserContext} from '@playwright/test';
import type {
    Message,
    PreviewJob,
    Reply,
    SpeechState,
    State,
    Text,
    SpeechPreviewRequest,
    SpeechModelImportRequest,
    VoiceSendRequest,
    VoiceDraftRequest,
} from '@aigram/api-client';

export const demoTranscript = 'Привет! Это проверка голосовых сообщений. Сегодня хорошая погода.';

export function createVoiceDemoState(): State {
    return {
        telegram: {state: 'ready', name: 'Демо-профиль', prompt: '', error: '', userId: '100'},
        engine: {
            installed: true,
            online: true,
            downloading: '',
            progress: 0,
            warming: '',
            error: '',
            models: [],
        },
        settings: {
            maxParallelTasks: 1,
            defaultModelProfileId: '',
            deliveryMode: 'reply',
            globalPrompt: '',
            paused: true,
            defaultModel: 'synthetic-model',
            ollamaDevice: 'auto',
            rules: {},
            replyDelaySeconds: 3,
            replyIntervalSeconds: 3,
            showTyping: true,
            memoryEnabled: true,
            memoryIntervalHours: 6,
            closeToTray: true,
            launchAtLogin: false,
            resumeOnStartup: false,
            voiceInputEnabled: false,
            voiceReplyMode: 'text',
            voiceReplyProbability: 50,
            voiceRecognitionModel: 'whisper-tiny',
            voiceSynthesisModel: 'piper-ru-irina',
            voiceSpeechRate: 1,
            voiceConversionDevice: 'cpu',
        },
        chats: [
            {
                id: '41',
                title: 'Голосовое демо',
                preview: 'Все сообщения остаются в демонстрации.',
                date: 1792600000,
                unread: 0,
                kind: 'private',
                joined: true,
                lastMessageId: '1',
                rule: {
                    enabled: false,
                    style: 'friendly',
                    model: 'synthetic-model',
                    modelProfileId: '',
                    deliveryMode: 'inherit',
                    analyzePhotos: false,
                    aiStickers: false,
                    instructions: '',
                    contextMessages: 20,
                    maxTokens: 128,
                    delaySeconds: 0,
                    creativity: 60,
                    emoji: true,
                },
            },
        ],
        activity: [],
    };
}

export function createVoiceDemoSpeech(): SpeechState {
    return {
        assets: [
            {
                id: 'sherpa-runtime',
                title: 'sherpa-onnx 1.13.8 · Windows x64 CPU',
                kind: 'runtime',
                engine: 'sherpa',
                format: 'JVM/JNI',
                languages: [],
                sizeBytes: 8277046,
                license:
                    'sherpa-onnx Apache-2.0; ONNX Runtime MIT; bundled eSpeak NG GPL-3.0-or-later (upstream sources linked in docs)',
                sourceUrl: 'https://github.com/k2-fsa/sherpa-onnx/tree/v1.13.8',
                dependencies: [],
                installed: true,
            },
            {
                id: 'whisper-tiny',
                title: 'Whisper tiny · multilingual int8',
                kind: 'recognition',
                engine: 'sherpa',
                format: 'Whisper ONNX int8',
                languages: ['ru', 'en', 'uk', 'kk'],
                sizeBytes: 116204861,
                license: 'MIT · OpenAI Whisper',
                sourceUrl: 'https://github.com/openai/whisper',
                dependencies: ['sherpa-runtime'],
                installed: true,
            },
            {
                id: 'piper-ru-irina',
                title: 'Piper · Ирина · Russian',
                kind: 'synthesis',
                engine: 'sherpa',
                format: 'Piper/VITS ONNX',
                languages: ['ru'],
                sizeBytes: 21149417,
                license:
                    'Piper repository MIT; voice dataset licence unspecified in upstream model card; eSpeak NG phonemizer/data GPL-3.0-or-later',
                sourceUrl:
                    'https://huggingface.co/rhasspy/piper-voices/blob/main/ru/ru_RU/irina/medium/MODEL_CARD',
                dependencies: ['sherpa-runtime'],
                installed: true,
            },
        ],
        downloads: [],
    };
}

export async function installVoiceMocks(
    surface: Pick<BrowserContext, 'route' | 'addInitScript'>,
    {
        state,
        speech,
        history,
        audioPath,
        dark = false,
        interactive = false,
    }: {
        state: State;
        speech: SpeechState;
        history: Message[];
        audioPath: string;
        dark?: boolean;
        interactive?: boolean;
    },
) {
    await initializeTestTheme(surface, dark);
    const sent: VoiceSendRequest[] = [];
    const requests: string[] = [];
    const jobs = new Map<string, PreviewJob>();
    const previews: SpeechPreviewRequest[] = [];
    const drafts: VoiceDraftRequest[] = [];
    const imports: SpeechModelImportRequest[] = [];
    const uploads: string[] = [];
    const controls = {holdPreview: false, holdManual: false};
    let sequence = 0;
    await surface.route('**/api/**', async (route) => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        if (path === '/api/state') {
            await route.fulfill({json: state});
        } else if (path === '/api/speech/compute') {
            await route.fulfill({
                json: {...speechCompute, selectedDevice: state.settings.voiceConversionDevice ?? 'cpu'},
            });
        } else if (path === '/api/engine/devices') {
            await route.fulfill({json: computeDevices});
        } else if (path === '/api/speech') {
            if (interactive) {
                speech.downloads.forEach((download) => {
                    if (download.phase === 'downloading') {
                        download.downloadedBytes = Math.min(
                            download.totalBytes,
                            download.downloadedBytes + 25,
                        );
                        if (download.downloadedBytes === download.totalBytes) {
                            download.phase = 'ready';
                            const asset = speech.assets.find(
                                (candidate) => candidate.id === download.assetId,
                            );
                            if (asset) {
                                asset.installed = true;
                            }
                        }
                    }
                });
            }
            await route.fulfill({json: speech});
        } else if (path === '/api/storage/locations') {
            await route.fulfill({
                json: {
                    locations: [
                        {
                            id: 'speech',
                            label: 'Speech',
                            path: 'C:/Synthetic/Speech',
                            available: true,
                            openable: true,
                            kind: 'directory',
                            note: '',
                        },
                    ],
                },
            });
        } else if (path === '/api/uploads' && request.method() === 'POST') {
            uploads.push(decodeURIComponent(request.headers()['x-file-name'] ?? ''));
            await route.fulfill({
                json: {
                    id: '00000000-0000-0000-0000-000000000001',
                    fileName: uploads.at(-1),
                    size: request.postDataBuffer()?.length ?? 0,
                    mimeType: 'application/octet-stream',
                },
            });
        } else if (path.startsWith('/api/uploads/') && request.method() === 'DELETE') {
            requests.push('DELETE upload');
            await route.fulfill({json: {ok: true}});
        } else if (path === '/api/speech/models/import') {
            const body: SpeechModelImportRequest = request.postDataJSON();
            imports.push(body);
            speech.assets.push({
                ...speech.assets[2],
                id: 'imported-voice',
                title: body.title,
                installed: true,
                userProvided: true,
                sourceUrl: '',
                license: body.license ?? '',
                languages: body.language ? [body.language] : [],
            });
            await route.fulfill({json: speech});
        } else if (path.startsWith('/api/activity/') && path.endsWith('/cancel')) {
            const id = path.split('/')[3];
            const job = jobs.get(id);
            if (job) {
                job.status = 'cancelled';
            }
            requests.push('CANCEL ' + id);
            await route.fulfill({json: {ok: true}});
        } else if (path.startsWith('/api/speech/assets/') && path.endsWith('/install')) {
            const assetId = path.split('/')[4];
            requests.push(request.method() + ' ' + assetId);
            speech.downloads = [
                {
                    assetId,
                    phase: request.method() === 'DELETE' ? 'cancelled' : 'downloading',
                    downloadedBytes: interactive ? 0 : 25,
                    totalBytes: 100,
                    error: '',
                },
            ];
            await route.fulfill({json: {ok: true}});
        } else if (path === '/api/model-profiles') {
            await route.fulfill({json: {profiles: [], presets: []}});
        } else if (path === '/api/settings') {
            Object.assign(state.settings, request.postDataJSON());
            await route.fulfill({json: state.settings});
        } else if (path === '/api/chats/41/messages') {
            if (request.method() === 'POST') {
                const input: Text = request.postDataJSON();
                history.push({
                    id: String(100 + history.length),
                    chatId: '41',
                    text: input.text,
                    entities: input.entities ?? [],
                    outgoing: true,
                    date: Date.now() / 1000,
                    kind: 'text',
                });
                await route.fulfill({json: {ok: true}});
            } else {
                await route.fulfill({json: history});
            }
        } else if (
            path === '/api/preview/jobs' ||
            path === '/api/speech/drafts' ||
            path === '/api/speech/preview'
        ) {
            const body = request.postDataJSON();
            if (path === '/api/speech/preview') {
                previews.push(body);
            }
            if (path === '/api/speech/drafts') {
                drafts.push(body);
            }
            const id = 'voice-job-' + ++sequence;
            const voice =
                path === '/api/speech/drafts' || path === '/api/speech/preview' || body.output === 'voice';
            const result: Reply = {
                text:
                    path === '/api/speech/drafts' || path === '/api/speech/preview'
                        ? body.text
                        : demoTranscript,
                durationMs: 100,
                tokensPerSecond: 0,
                ...(voice
                    ? {
                          voice: {
                              id,
                              url: '/api/speech/audio/' + id,
                              mimeType: 'audio/ogg',
                              duration: 5,
                              waveform: [],
                          },
                      }
                    : {}),
            };
            jobs.set(id, {
                id,
                status:
                    (path === '/api/speech/preview' && controls.holdPreview) ||
                    (path === '/api/speech/drafts' && controls.holdManual)
                        ? 'generating'
                        : 'ready',
                result,
                error: '',
            });
            await route.fulfill({json: {id, status: 'queued', result: null, error: ''}});
        } else if (path.startsWith('/api/preview/jobs/')) {
            await route.fulfill({json: jobs.get(path.split('/').at(-1) ?? '')});
        } else if (path.startsWith('/api/speech/audio/') || path.startsWith('/api/media/')) {
            await route.fulfill({path: audioPath, contentType: 'audio/ogg'});
        } else if (path === '/api/chats/41/voice') {
            const input: VoiceSendRequest = request.postDataJSON();
            if (!sent.some((value) => value.voiceId === input.voiceId)) {
                sent.push(input);
                if (interactive) {
                    history.push({
                        id: String(100 + history.length),
                        chatId: '41',
                        text: '',
                        outgoing: true,
                        date: Math.floor(Date.now() / 1000),
                        kind: 'voice',
                        automated: true,
                        media: {
                            fileId: 100 + history.length,
                            mimeType: 'audio/ogg',
                            fileName: 'voice.ogg',
                            width: 0,
                            height: 0,
                            thumbnailFileId: 0,
                            size: 18510,
                            duration: 5,
                            waveform: [],
                        },
                    });
                }
            }
            await route.fulfill({json: {ok: true}});
        } else {
            await route.fulfill({
                status: 404,
                json: {message: 'В демонстрации этот раздел не подключён: ' + path},
            });
        }
    });
    return {state, speech, sent, requests, previews, imports, uploads, controls, drafts};
}
