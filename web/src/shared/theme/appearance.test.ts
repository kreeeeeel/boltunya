import {describe, expect, it} from 'vitest';
import {compileAppearance, defaultAppearance, validThemeColor} from '@/shared/theme/appearance';
describe('custom theme CSS boundary', () => {
    it('scopes every selector and keeps safe local formatting', () => {
        const css = compileAppearance({
            ...defaultAppearance,
            tokens: {accent: '#aabbcc'},
            css: '.message, .contact:hover { border-radius: 20px; color: var(--tiho-ink); }',
        });
        expect(css).toContain('--g-color-base-brand:#aabbcc');
        expect(css).toContain('[data-aigram-css-scope] .message,[data-aigram-css-scope] .contact:hover');
    });
    it.each([
        '@import "https://example.com/x.css";',
        '.x { background:url(https://example.com/x); }',
        '.x { background:image-set("https://example.com/x" 1x); }',
        '.x { background:u\\72l(https://example.com/x); }',
        '@font-face { font-family: test; src: url(https://example.com/a); }',
        '.x { --external: url(https://example.com/x); }',
        '@media (min-width: 10px) { .x { color: red; } }',
    ])('rejects network or global rule %s', (css) => {
        expect(() => compileAppearance({...defaultAppearance, css})).toThrow();
    });
    it('rejects malformed CSS, unknown tokens and token injection', () => {
        expect(() => compileAppearance({...defaultAppearance, css: '.x { color: } invalid'})).toThrow();
        expect(() =>
            compileAppearance({...defaultAppearance, tokens: {accent: 'red;display:none'}}),
        ).toThrow();
        expect(() => compileAppearance({...defaultAppearance, tokens: {constructor: '#fff'}})).toThrow();
        expect(validThemeColor('#4488ff')).toBe(true);
        expect(validThemeColor('#12')).toBe(false);
    });
});
