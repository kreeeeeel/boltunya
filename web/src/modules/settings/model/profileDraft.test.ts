import {expect, it} from 'vitest';
import {profileDraft, profilePayload} from '@/modules/settings/model/profileDraft';
import type {ModelProfileUpdate} from '@/modules/settings/model/useModelProfiles';
import {modelProfiles, providerPreset} from '@tests/fixtures/modelProfiles';

it('never sends an empty or masked key and only clears explicitly', () => {
    const draft = {name: 'My model', clearApiKey: false} as ModelProfileUpdate;
    expect(profilePayload(draft, '')).toEqual(draft);
    expect(profilePayload(draft, '  secret  ').apiKey).toBe('secret');
    expect(profilePayload({...draft, clearApiKey: true}, '')).toMatchObject({clearApiKey: true});
});

it('initializes complete editable options from a preset without changing its defaults', () => {
    const preset = structuredClone(providerPreset);
    const draft = profileDraft(preset);

    expect(draft.options).toEqual(preset.defaultsOptions);
    expect(draft.capabilities).toEqual(preset.defaultCapabilities);
    expect(draft.preset).toBe(preset.id);
    expect(draft.enabled).toBe(true);
    expect(draft.clearApiKey).toBe(false);
    expect(draft.options).not.toBe(preset.defaultsOptions);
    expect(draft.capabilities).not.toBe(preset.defaultCapabilities);
});

it('keeps saved connection options in the draft without copying response-only fields', () => {
    const profile = structuredClone(modelProfiles[0]);
    const draft = profileDraft(profile);

    expect(draft.options).toEqual(profile.options);
    expect(draft.capabilities).toEqual(profile.capabilities);
    expect(draft.options).not.toBe(profile.options);
    expect(draft.capabilities).not.toBe(profile.capabilities);
    expect(draft).not.toHaveProperty('id');
    expect(draft).not.toHaveProperty('apiKeyConfigured');
});
