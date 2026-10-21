import {generate, ident, parse, walk} from 'css-tree';
import type {components} from '@/shared/api/schema';

export type Appearance = components['schemas']['AppearanceSettings'];
export const defaultAppearance: Appearance = {mode: 'system', tokens: {}, css: ''};
export const themeTokens = {
    accent: ['--g-color-base-brand', '--g-color-text-brand', '--g-color-text-link'],
    background: ['--tiho-bg', '--tiho-chat'],
    panel: ['--tiho-panel', '--tiho-rail'],
    text: ['--tiho-ink'],
    muted: ['--tiho-muted'],
    incoming: ['--tiho-bubble'],
    outgoing: ['--tiho-outgoing'],
    border: ['--tiho-line'],
} as const;
export function validThemeColor(value: string) {
    return (
        [4, 5, 7, 9].includes(value.length) &&
        value.startsWith('#') &&
        Array.from(value.slice(1)).every((character) => '0123456789abcdef'.includes(character.toLowerCase()))
    );
}
export function compileAppearance(value: Appearance): string {
    if (value.css.length > 20_000 || !['light', 'dark', 'system'].includes(value.mode)) {
        throw new Error('invalidTheme');
    }
    const tokens = Object.entries(value.tokens)
        .map(([key, color]) => {
            if (!Object.hasOwn(themeTokens, key) || !validThemeColor(color)) {
                throw new Error('invalidColor');
            }
            return themeTokens[key as keyof typeof themeTokens]
                .map((variable) => variable + ':' + color)
                .join(';');
        })
        .join(';');
    const ast = parse(value.css, {
        parseCustomProperty: true,
        onParseError: () => {
            throw new Error('invalidCss');
        },
    });
    walk(ast, (node) => {
        if (node.type === 'Atrule' || node.type === 'Raw' || node.type === 'Url') {
            throw new Error('unsafeCss');
        }
        if (
            node.type === 'Function' &&
            ['url', 'src', 'image', 'image-set', '-webkit-image-set', 'expression', 'paint'].includes(
                ident.decode(node.name).toLowerCase(),
            )
        ) {
            throw new Error('unsafeCss');
        }
        if (
            node.type === 'Declaration' &&
            ['behavior', '-moz-binding'].includes(ident.decode(node.property).toLowerCase())
        ) {
            throw new Error('unsafeCss');
        }
        if (node.type === 'Rule') {
            if (node.prelude.type !== 'SelectorList') {
                throw new Error('unsafeCss');
            }
            node.prelude.children.forEach((selector) => {
                if (selector.type !== 'Selector') {
                    throw new Error('unsafeCss');
                }
                const scoped = parse('[data-aigram-css-scope] ' + generate(selector), {context: 'selector'});
                if (scoped.type !== 'Selector') {
                    throw new Error('invalidCss');
                }
                selector.children = scoped.children;
            });
        }
    });
    return (tokens ? '.g-root.g-root{' + tokens + '}' : '') + generate(ast);
}
