import {expect, it} from 'vitest';
import {createSettingsDraft} from '@/modules/settings/model/settingsDraft';
import {workspaceState} from '@tests/fixtures/workspace';
it('preserves explicit collection and proactive preferences when saving unrelated settings', () => {
    const draft = createSettingsDraft({
        ...workspaceState.settings,
        memoryCollectionScope: 'all-allowed',
        proactiveEnabled: true,
        proactiveIntervalMinutes: 45,
        formatReplies: true,
        replyProcessingMode: 'finish',
    });
    expect(draft).toMatchObject({
        memoryCollectionScope: 'all-allowed',
        proactiveEnabled: true,
        proactiveIntervalMinutes: 45,
        formatReplies: true,
        replyProcessingMode: 'finish',
    });
});

it('keeps AI formatting opt-in for older saved settings', () => {
    const settings = {...workspaceState.settings};
    delete settings.formatReplies;
    delete settings.replyProcessingMode;
    expect(createSettingsDraft(settings).formatReplies).toBe(false);
    expect(createSettingsDraft(settings).replyProcessingMode).toBe('replace');
});
