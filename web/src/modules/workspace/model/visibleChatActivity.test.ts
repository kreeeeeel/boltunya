import {expect, it} from 'vitest';
import {visibleChatActivity} from '@/modules/workspace/model/visibleChatActivity';
import {testActivity} from '@tests/fixtures/activity';

it('keeps a failed reply visible until a newer job replaces it', () => {
    const failed = {...testActivity[0], status: 'error', date: 10};
    expect(visibleChatActivity([failed], '41')).toEqual(failed);
    expect(
        visibleChatActivity([failed, {...failed, id: 'new', status: 'sent', date: 11}], '41'),
    ).toBeUndefined();
    expect(
        visibleChatActivity([failed, {...failed, id: 'active', status: 'queued', date: 12}], '41')?.id,
    ).toBe('active');
    expect(visibleChatActivity([{...failed, kind: 'memory'}], '41')).toBeUndefined();
    expect(visibleChatActivity([failed], '42')).toBeUndefined();
});
