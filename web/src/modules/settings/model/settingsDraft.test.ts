import {expect, it} from 'vitest';
import {createSettingsDraft} from '@/modules/settings/model/settingsDraft';
import {workspaceState} from '@tests/fixtures/workspace';
it('preserves explicit collection and proactive preferences when saving unrelated settings', () => {
    const draft = createSettingsDraft({
        ...workspaceState.settings,
        memoryCollectionScope: 'all-allowed',
        proactiveEnabled: true,
        proactiveIntervalMinutes: 45,
    });
    expect(draft).toMatchObject({
        memoryCollectionScope: 'all-allowed',
        proactiveEnabled: true,
        proactiveIntervalMinutes: 45,
    });
});
