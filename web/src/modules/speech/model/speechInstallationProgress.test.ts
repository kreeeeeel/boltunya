import {expect, it} from 'vitest';
import type {SpeechState} from '@aigram/api-client';
import {speechState} from '@tests/fixtures/speech';
import {speechInstallationProgress} from '@/modules/speech/model/speechInstallationProgress';

it('shows prerequisite download progress on the requested voice rather than a motionless queued button', () => {
    const engine = {...speechState.assets[0], installed: false, sizeBytes: 100};
    const voice = {...speechState.assets[2], installed: false, sizeBytes: 20, dependencies: [engine.id]};
    const state: SpeechState = {
        assets: [engine, voice],
        downloads: [
            {assetId: voice.id, phase: 'queued', downloadedBytes: 0, totalBytes: 20, error: ''},
            {assetId: engine.id, phase: 'downloading', downloadedBytes: 50, totalBytes: 100, error: ''},
        ],
    };
    expect(speechInstallationProgress(voice, state)).toMatchObject({
        assetId: voice.id,
        phase: 'downloading',
        downloadedBytes: 50,
        totalBytes: 120,
    });
    state.assets[0].installed = true;
    state.downloads[0] = {...state.downloads[0], phase: 'downloading', downloadedBytes: 10};
    expect(speechInstallationProgress(voice, state)).toMatchObject({downloadedBytes: 110, totalBytes: 120});
});

it('preserves cancellation and failure states without borrowing progress from an unrelated download', () => {
    const asset = speechState.assets[2];
    const error = {
        assetId: asset.id,
        phase: 'error' as const,
        downloadedBytes: 0,
        totalBytes: 0,
        error: 'Network failure',
    };
    expect(speechInstallationProgress(asset, {assets: speechState.assets, downloads: [error]})).toBe(error);
    expect(speechInstallationProgress(asset, {assets: speechState.assets, downloads: []})).toBeUndefined();
});

it('keeps individual transfer progress when the engine was already installed before this request', () => {
    const model = {...speechState.assets[1], installed: false};
    const download = {
        assetId: model.id,
        phase: 'downloading' as const,
        downloadedBytes: 25,
        totalBytes: 100,
        error: '',
    };
    expect(speechInstallationProgress(model, {assets: speechState.assets, downloads: [download]})).toBe(
        download,
    );
});
