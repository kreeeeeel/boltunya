import {defineConfig, globalIgnores} from 'eslint/config';
import nextVitals from 'eslint-config-next/core-web-vitals';
import nextTs from 'eslint-config-next/typescript';

const intrinsicElements =
    `a abbr address area article aside audio b base bdi bdo blockquote body br button canvas caption cite code col colgroup data datalist dd del details dfn dialog div dl dt em embed fieldset figcaption figure footer form h1 h2 h3 h4 h5 h6 head header hgroup hr html i iframe img input ins kbd label legend li link main map mark menu meta meter nav noscript object ol optgroup option output p picture pre progress q rp rt ruby s samp script search section select slot small source span strong style sub summary sup table tbody td template textarea tfoot th thead time title tr track u ul var video wbr svg animate animateMotion animateTransform circle clipPath defs desc ellipse feBlend feColorMatrix feComponentTransfer feComposite feConvolveMatrix feDiffuseLighting feDisplacementMap feDistantLight feDropShadow feFlood feFuncA feFuncB feFuncG feFuncR feGaussianBlur feImage feMerge feMergeNode feMorphology feOffset fePointLight feSpecularLighting feSpotLight feTile feTurbulence filter foreignObject g image line linearGradient marker mask metadata mpath path pattern polygon polyline radialGradient rect set stop switch symbol text textPath tspan use view math maction maligngroup malignmark menclose merror mfenced mfrac mglyph mi mlabeledtr mmultiscripts mn mo mover mpadded mphantom mroot mrow ms mscarries mscarry msgroup msline mspace msqrt msrow mstack mstyle msub msubsup msup mtable mtd mtext mtr munder munderover semantics annotation annotation-xml`.split(
        ' ',
    );
const intrinsicSelector = intrinsicElements
    .map((name) => `JSXOpeningElement[name.type="JSXIdentifier"][name.name="${name}"]`)
    .join(',');

export default defineConfig([
    ...nextVitals,
    ...nextTs,
    globalIgnores([
        '.next/**',
        'out/**',
        'build/**',
        'playwright-report/**',
        'test-results/**',
    ]),
    {
        files: ['src/**/*.{ts,tsx}'],
        rules: {'no-restricted-imports': ['error', {patterns: ['./*', '../*']}], curly: ['error', 'all']},
    },
    {
        files: ['src/**/*.tsx'],
        ignores: ['src/app/layout.tsx'],
        rules: {
            'no-restricted-syntax': [
                'error',
                {selector: intrinsicSelector, message: 'Use Gravity UI components.'},
            ],
        },
    },
]);
