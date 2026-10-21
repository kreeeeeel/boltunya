import {defineConfig} from 'vitest/config';
import {fileURLToPath} from 'node:url';

export default defineConfig({
    resolve: {
        alias: {
            '@aigram/api-client': fileURLToPath(
                new URL(
                    '../backend/contract/client/typescript/build/generated/openapi/src/index.ts',
                    import.meta.url,
                ),
            ),
            '@': fileURLToPath(new URL('./src', import.meta.url)),
            '@tests': fileURLToPath(new URL('./tests', import.meta.url)),
        },
    },
    test: {
        maxWorkers: 4,
        environment: 'jsdom',
        include: ['src/**/*.test.{ts,tsx}'],
        setupFiles: ['./tests/setup.ts'],
        server: {deps: {inline: [/@gravity-ui/, /@bem-react/]}},
    },
});
