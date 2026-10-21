import type {GeneratedVoice, SpeechState} from '@aigram/api-client';

export const speechState: SpeechState = {
    assets: [
        {
            id: 'sherpa-runtime',
            title: 'sherpa-onnx 1.13.8 · Windows x64 CPU',
            kind: 'runtime',
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

export const generatedVoice: GeneratedVoice = {
    id: 'synthetic-voice-1',
    url: '/api/speech/audio/synthetic-voice-1',
    mimeType: 'audio/ogg',
    duration: 5,
    waveform: [],
};
