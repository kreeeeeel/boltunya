import {expect, it} from 'vitest';
import {speechReady} from '@/modules/speech/model/speechReady';
import {speechState} from '@tests/fixtures/speech';
import {createVoiceDemoSpeech} from '@tests/fixtures/voiceDemo';
import {readFileSync} from 'node:fs';
import {join} from 'node:path';
import type {SpeechAsset} from '@aigram/api-client';

it('requires the selected synthesis model and its runtime without relying on unrelated models', () => {
    expect(speechReady([], 'piper-ru-irina')).toBe(false);
    expect(speechReady(speechState.assets, 'whisper-tiny')).toBe(false);
    expect(
        speechReady(
            speechState.assets.map((asset) => ({...asset, installed: asset.id !== 'sherpa-runtime'})),
            'piper-ru-irina',
        ),
    ).toBe(false);
    expect(
        speechReady(
            speechState.assets.filter((asset) => asset.kind !== 'recognition'),
            'piper-ru-irina',
        ),
    ).toBe(true);
});

it('keeps all displayed fixture sizes, sources and licences equal to the shipping catalog', () => {
    const catalog = JSON.parse(
        readFileSync(
            join(process.cwd(), '..', 'backend', 'src', 'main', 'resources', 'speech', 'catalog.json'),
            'utf8',
        ),
    ) as {asset: Omit<SpeechAsset, 'installed'>}[];
    [speechState.assets, createVoiceDemoSpeech().assets].forEach((assets) =>
        assets.forEach((asset) => {
            const original = catalog.find((entry) => entry.asset.id === asset.id);
            expect(original).toBeDefined();
            expect(asset).toMatchObject(original?.asset ?? {});
        }),
    );
});
