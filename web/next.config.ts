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
};

export default config;
