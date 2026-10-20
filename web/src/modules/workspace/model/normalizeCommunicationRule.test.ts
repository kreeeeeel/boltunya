import {expect, it} from 'vitest';

import {communicationStyles} from '@/modules/workspace/model/communicationStyles';
import {normalizeCommunicationRule} from '@/modules/workspace/model/normalizeCommunicationRule';
import {defaultRule} from '@/shared/api/client';

it('offers concrete built-in styles and reserves custom for named presets', () => {
    expect(communicationStyles.map((style) => style.value)).toEqual([
        'friendly',
        'business',
        'concise',
        'warm',
        'aggressive',
        'rough',
    ]);
});

it('keeps a named preset reference and personal instructions separate', () => {
    const rule = {
        ...defaultRule,
        style: 'custom' as const,
        styleId: 'calm',
        instructions: 'Не обещай встреч.',
    };
    expect(normalizeCommunicationRule(rule, ['calm'])).toBe(rule);
});

it('migrates an unnamed or removed custom selection without losing personal instructions', () => {
    const rule = {
        ...defaultRule,
        style: 'custom' as const,
        styleId: 'removed',
        instructions: 'Коротко и спокойно.',
    };
    expect(normalizeCommunicationRule(rule, [])).toEqual({...rule, style: 'friendly', styleId: ''});
});
