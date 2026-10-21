import type {NextConfig} from 'next';

const profile = process.env.APP_ENV ?? 'dev';

if (!['dev', 'testing', 'prod'].includes(profile)) {
    throw new Error('APP_ENV must be dev, testing or prod');
}

const config: NextConfig = {
    output: 'export',
    trailingSlash: true,
    images: {unoptimized: true},
    env: {NEXT_PUBLIC_APP_ENV: profile},
    experimental: {extensionAlias: {'.js': ['.ts', '.tsx', '.js']}},
    webpack(webpackConfig, {isServer}) {
        if (!isServer) {
            // Gravity's CSS sanitizer imports Node-only source-map support, unused in the browser.
            webpackConfig.resolve.fallback = {...webpackConfig.resolve.fallback, fs: false};
        }
        return webpackConfig;
    },
};

export default config;
