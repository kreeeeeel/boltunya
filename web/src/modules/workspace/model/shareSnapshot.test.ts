import {expect, it} from 'vitest';
import {shareSnapshot} from '@/modules/workspace/model/shareSnapshot';

it('retains an unchanged history and untouched chats while only generation progress changes', () => {
    const previous = {
        chats: [{id: '1', rule: {enabled: true}, typingUsers: []}],
        activity: [{id: 'job', thinking: 'First'}],
    };
    const next = shareSnapshot(previous, {
        chats: [{id: '1', rule: {enabled: true}, typingUsers: []}],
        activity: [{id: 'job', thinking: 'Second'}],
    });
    expect(next).not.toBe(previous);
    expect(next.chats).toBe(previous.chats);
    expect(next.activity).not.toBe(previous.activity);
    expect(shareSnapshot(next, JSON.parse(JSON.stringify(next)))).toBe(next);
});

it('keeps entity references across reordering and correctly applies edits additions and deletions', () => {
    const previous = [
        {id: '1', text: 'First'},
        {id: '2', text: 'Second'},
    ];
    const reordered = shareSnapshot(previous, [
        {id: '2', text: 'Second'},
        {id: '1', text: 'First'},
    ]);
    expect(reordered[0]).toBe(previous[1]);
    expect(reordered[1]).toBe(previous[0]);
    const edited = shareSnapshot(previous, [
        {id: '1', text: 'Edited'},
        {id: '3', text: 'Third'},
    ]);
    expect(edited).toEqual([
        {id: '1', text: 'Edited'},
        {id: '3', text: 'Third'},
    ]);
    expect(shareSnapshot({a: 1, b: 2}, {a: 1})).toEqual({a: 1});
});
