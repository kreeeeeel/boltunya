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

it.each(['error', 'interrupted', 'cancelled', 'ready'])(
    'a newer terminal %s draft hides an older automatic failure without duplicating its local notice',
    (status) => {
        const automatic = {...testActivity[0], id: 'automatic', status: 'error', date: 10};
        const draft = {...automatic, id: 'manual', kind: 'draft' as const, status, date: 11};

        expect(visibleChatActivity([automatic, draft], '41')).toBeUndefined();
        expect(visibleChatActivity([draft, automatic], '41')).toBeUndefined();
    },
);

it.each(['queued', 'generating'])('an active %s draft retains its process block', (status) => {
    const automatic = {...testActivity[0], status: 'error', date: 10};
    const draft = {...automatic, id: 'manual', kind: 'draft' as const, status, date: 11};

    expect(visibleChatActivity([automatic, draft], '41')).toEqual(draft);
});

it('a newer failed automatic reply remains visible after an older terminal draft', () => {
    const draft = {...testActivity[0], id: 'manual', kind: 'draft' as const, status: 'error', date: 10};
    const automatic = {...testActivity[0], id: 'automatic', status: 'error', date: 11};

    expect(visibleChatActivity([draft, automatic], '41')).toEqual(automatic);
});
