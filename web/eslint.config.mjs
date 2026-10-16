import {defineConfig,globalIgnores} from 'eslint/config';
import nextVitals from 'eslint-config-next/core-web-vitals';
import nextTs from 'eslint-config-next/typescript';

export default defineConfig([
    ...nextVitals,...nextTs,
    globalIgnores(['.next/**','out/**','src/shared/api/schema.d.ts','playwright-report/**','test-results/**']),
    {files:['src/**/*.{ts,tsx}'],rules:{'no-restricted-imports':['error',{patterns:['./*','../*']}],'curly':['error','all']}},
    {files:['src/**/*.tsx'],ignores:['src/app/layout.tsx'],rules:{'no-restricted-syntax':['error',{selector:'JSXOpeningElement[name.type="JSXIdentifier"][name.name=/^[a-z]/]',message:'Use Gravity UI components.'}]}},
]);
