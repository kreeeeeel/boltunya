import {defineConfig} from 'vitest/config';
import {fileURLToPath} from 'node:url';

export default defineConfig({
    resolve: {
        alias: {
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
