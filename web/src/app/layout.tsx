import type {Metadata} from 'next';
import type {ReactNode} from 'react';
import {getRootClassName} from '@gravity-ui/uikit/server';

import '@gravity-ui/uikit/styles/styles.css';
import '@xyflow/react/dist/style.css';
import '@fontsource/manrope/400.css';
import '@fontsource/manrope/500.css';
import '@fontsource/manrope/600.css';
import '@fontsource/manrope/700.css';
import '@/shared/styles/globals.css';

import {Preferences} from '@/shared/theme/Preferences';

export const metadata: Metadata = {
    title: 'Болтунья — Telegram',
    icons: {icon: '/assets/brand/boltunya.svg'},
    description: 'Локальный ИИ-помощник для личных диалогов Telegram',
};

export default function RootLayout({children}: {children: ReactNode}) {
    return (
        <html lang="ru">
            <body className={getRootClassName({theme: 'light'})}>
                <Preferences>{children}</Preferences>
            </body>
        </html>
    );
}
