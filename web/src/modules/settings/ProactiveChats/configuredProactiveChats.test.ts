import {expect, it} from 'vitest';
import {configuredProactiveChats} from '@/modules/settings/ProactiveChats/configuredProactiveChats';
import type {Chat} from '@/shared/api/client';
import {defaultRule} from '@/shared/api/client';
import {workspaceState} from '@tests/fixtures/workspace';

function chat(id: string, rule: Partial<Chat['rule']> = {}, properties: Partial<Chat> = {}): Chat {
    return {
        ...workspaceState.chats[0],
        id,
        kind: 'private',
        lastMessageId: '99',
        ...properties,
        rule: {...defaultRule, ...rule},
    };
}

it('includes explicit initiative independently of automatic replies and preserves transiently busy chats', () => {
    const explicit = chat(
        '1',
        {enabled: false, proactiveMode: 'enabled'},
        {unread: 3, typingUsers: [{id: 'user:2', name: 'User', action: 'typing'}]},
    );
    const inherited = chat('2', {enabled: true, proactiveMode: 'inherit'});
    const disabled = chat('3', {enabled: true, proactiveMode: 'disabled'});
    const settings = {...workspaceState.settings, proactiveEnabled: false};
    expect(configuredProactiveChats([explicit, inherited, disabled], settings)).toEqual([explicit]);
    expect(
        configuredProactiveChats([explicit, inherited, disabled], {...settings, proactiveEnabled: true}),
    ).toEqual([explicit, inherited]);
});

it('excludes self, unavailable, empty and channel chats from the configured list', () => {
    const rule = {proactiveMode: 'enabled' as const};
    const valid = chat('valid', rule);
    expect(
        configuredProactiveChats(
            [
                chat('self', rule),
                chat('left', rule, {joined: false}),
                chat('channel', rule, {kind: 'channel'}),
                chat('empty', rule, {lastMessageId: ''}),
                valid,
            ],
            workspaceState.settings,
            'self',
        ),
    ).toEqual([valid]);
});

it('applies effective group permissions and allows mentions-only initiative only when explicitly enabled', () => {
    const explicit = chat(
        '1',
        {enabled: false, proactiveMode: 'enabled', groupReplies: 'inherit'},
        {kind: 'group'},
    );
    const inherited = chat(
        '2',
        {enabled: true, proactiveMode: 'inherit', groupReplies: 'inherit'},
        {kind: 'group'},
    );
    const disabled = chat('3', {proactiveMode: 'enabled', groupReplies: 'disabled'}, {kind: 'group'});
    const all = chat('4', {proactiveMode: 'enabled', groupReplies: 'all'}, {kind: 'supergroup'});
    const settings = {...workspaceState.settings, proactiveEnabled: true, groupReplies: 'mentions' as const};
    expect(configuredProactiveChats([explicit, inherited, disabled, all], settings)).toEqual([explicit, all]);
    expect(
        configuredProactiveChats([explicit, inherited, disabled, all], {...settings, groupReplies: 'all'}),
    ).toEqual([explicit, inherited, all]);
});
