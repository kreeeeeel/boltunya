import {expect, it} from 'vitest';
import {
    mergeActivityEntries,
    orderActivityEntries,
    statusLabelKey,
    statusTheme,
} from '@/modules/activity/model/activity';
import {testActivity} from '@tests/fixtures/activity';
it('distinguishes completed memory and drafts from Telegram delivery', () => {
    expect(statusLabelKey({status: 'ready', kind: 'memory'})).toBe('memoryReady');
    expect(statusLabelKey({status: 'ready', kind: 'draft'})).toBe('draftReady');
    expect(statusLabelKey({status: 'sent', kind: 'reply'})).toBe('status.sent');
    expect(statusTheme('ready')).toBe('success');
});

it('places new same-second events before existing entries while older pages remain after them', () => {
    const old = {...testActivity[2], id: 'old'};
    const newest = {...old, id: 'new'};
    const previous = mergeActivityEntries([old], [newest, old]);
    expect(orderActivityEntries(previous).map((entry) => entry.id)).toEqual(['new', 'old']);
    const older = {...old, id: 'older'};
    expect(
        orderActivityEntries(mergeActivityEntries(previous, [older], 'older')).map((entry) => entry.id),
    ).toEqual(['new', 'old', 'older']);
});

it('never reverts completed history to an outdated active snapshot', () => {
    const completed = {...testActivity[2], id: 'job', finishedAt: 2000000};
    const outdated = {...completed, status: 'generating', finishedAt: 0};
    expect(mergeActivityEntries([completed], [outdated])).toEqual([completed]);
});

it('uses completion timestamps and actual queue positions without changing the source arrays', () => {
    const recent = {...testActivity[2], id: 'finished', date: 1, finishedAt: 2000000000000};
    const unknown = {...testActivity[1], id: 'unknown', queuePosition: 0};
    const source = [unknown, testActivity[2], recent, testActivity[1], testActivity[0]];
    expect(orderActivityEntries(source).map((entry) => entry.id)).toEqual([
        'running',
        'queued',
        'unknown',
        'finished',
        'sent',
    ]);
    expect(source[0]).toBe(unknown);
});
