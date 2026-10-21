import type {Metadata} from 'next';
import type {ReactNode} from 'react';
import {getRootClassName} from '@gravity-ui/uikit/server';

import '@gravity-ui/uikit/styles/styles.css';
import '@/shared/styles/globals.css';
import '@/shared/styles/typography.css';

import {Preferences} from '@/shared/theme/Preferences';
import {uiFont} from '@/shared/theme/fonts';

export const metadata: Metadata = {
    title: 'Aigram — Telegram',
    icons: {icon: '/assets/brand/aigram.svg'},
    description: 'Локальный ИИ-помощник для личных диалогов Telegram',
};

export default function RootLayout({children}: {children: ReactNode}) {
    return (
        <html lang="ru" className={uiFont.variable}>
            <body className={getRootClassName({theme: 'light'})}>
                <Preferences>{children}</Preferences>
            </body>
        </html>
    );
}
