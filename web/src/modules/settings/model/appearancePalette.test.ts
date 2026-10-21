import {expect, it} from 'vitest';
import {appearancePalette} from '@/modules/settings/model/appearancePalette';

it('keeps valid draft colors visible and falls back per token while an incomplete color is being typed', () => {
    const palette = appearancePalette(
        {mode: 'system', css: '', tokens: {accent: '#a855f7', panel: '#12'}},
        'dark',
    );
    expect(palette.accent).toBe('#a855f7');
    expect(palette.panel).toBe('#242a34');
    expect(palette.text).toBe('#e7edf3');
});

it('previews an explicitly selected light theme even when the system is dark', () => {
    expect(appearancePalette({mode: 'light', css: '', tokens: {}}, 'dark').background).toBe('#f3f5f7');
});
