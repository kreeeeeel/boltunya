import {expect, it} from 'vitest';
import {statusLabelKey, statusTheme} from '@/modules/activity/model/activity';
it('distinguishes completed memory and drafts from Telegram delivery', () => {
    expect(statusLabelKey({status: 'ready', kind: 'memory'})).toBe('memoryReady');
    expect(statusLabelKey({status: 'ready', kind: 'draft'})).toBe('draftReady');
    expect(statusLabelKey({status: 'sent', kind: 'reply'})).toBe('status.sent');
    expect(statusTheme('ready')).toBe('success');
});
