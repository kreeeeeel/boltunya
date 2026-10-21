import {expect, it} from 'vitest';
import {filterSpeechAssets} from '@/modules/speech/model/filterSpeechAssets';
import {speechLanguageName} from '@/modules/speech/model/speechLanguageName';
import {speechState} from '@tests/fixtures/speech';

it('combines type, case-insensitive title or model ID, and language without changing the catalog', () => {
    const assets = [
        ...speechState.assets,
        {...speechState.assets[2], id: 'voice-en', title: 'English Voice', languages: ['en']},
    ];
    expect(filterSpeechAssets(assets, 'synthesis', ' PIPER ', 'ru', 'ru').map((asset) => asset.id)).toEqual([
        'piper-ru-irina',
    ]);
    expect(filterSpeechAssets(assets, 'synthesis', 'VOICE', 'en', 'ru').map((asset) => asset.id)).toEqual([
        'voice-en',
    ]);
    expect(filterSpeechAssets(assets, 'recognition', '', 'kk', 'ru').map((asset) => asset.id)).toEqual([
        'whisper-tiny',
    ]);
    expect(filterSpeechAssets(assets, 'synthesis', 'english', 'ru', 'ru')).toEqual([]);
    expect(assets).toHaveLength(4);
});

it('localizes known languages and keeps user-provided unknown language labels readable', () => {
    expect(speechLanguageName('en', 'ru')).toBe('английский');
    expect(speechLanguageName('русский', 'ru')).toBe('русский');
});
