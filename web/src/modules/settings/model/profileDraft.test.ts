import {expect, it} from 'vitest';
import {profilePayload} from '@/modules/settings/model/profileDraft';
import type {ModelProfileUpdate} from '@/modules/settings/model/useModelProfiles';
it('never sends an empty or masked key and only clears explicitly', () => {
    const draft = {name: 'My model', clearApiKey: false} as ModelProfileUpdate;
    expect(profilePayload(draft, '')).not.toHaveProperty('apiKey');
    expect(profilePayload(draft, '  secret  ').apiKey).toBe('secret');
    expect(profilePayload({...draft, clearApiKey: true}, '')).toMatchObject({clearApiKey: true});
});
