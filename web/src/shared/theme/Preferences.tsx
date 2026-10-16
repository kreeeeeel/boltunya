'use client';

import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {createContext, useContext, useEffect, useState} from 'react';
import type {ReactNode} from 'react';

import en from '@/shared/i18n/en.json';
import ru from '@/shared/i18n/ru.json';
import kk from '@/shared/i18n/kk.json';
import uk from '@/shared/i18n/uk.json';

const messages = {ru, en, kk, uk};
type Locale = keyof typeof messages;

const PreferencesContext = createContext<{
    toggleTheme: () => void;
    changeLocale: (locale: Locale) => void;
    locale: Locale;
}>({toggleTheme: () => {}, changeLocale: () => {}, locale: 'ru'});

export const usePreferences = () => useContext(PreferencesContext);

export function Preferences({children}: {children: ReactNode}) {
    const [theme, setTheme] = useState<'light' | 'dark'>('light');
    const [locale, setLocale] = useState<Locale>('ru');

    useEffect(() => {
        function restore() {
            setTheme(document.cookie.includes('tiho-theme=dark') ? 'dark' : 'light');
            const saved = document.cookie.match(/(?:^|; )tiho-lang=(ru|en|kk|uk)(?:;|$)/)?.[1] as
                | Locale
                | undefined;
            setLocale(saved ?? 'ru');
            document.documentElement.lang = saved ?? 'ru';
            window.history.replaceState(null, '', window.location.pathname);
        }
        queueMicrotask(restore);
        window.addEventListener('boltun-tray-refresh', restore);
        return () => window.removeEventListener('boltun-tray-refresh', restore);
    }, []);

    function toggleTheme() {
        const next = theme === 'light' ? 'dark' : 'light';
        setTheme(next);
        document.cookie = `tiho-theme=${next}; Path=/; SameSite=Strict; Max-Age=31536000`;
    }

    function changeLocale(next: Locale) {
        setLocale(next);
        document.documentElement.lang = next;
        document.cookie = `tiho-lang=${next}; Path=/; SameSite=Strict; Max-Age=31536000`;
    }

    return (
        <PreferencesContext.Provider value={{toggleTheme, changeLocale, locale}}>
            <NextIntlClientProvider locale={locale} messages={messages[locale]} timeZone="Europe/Moscow">
                <ThemeProvider theme={theme} lang={locale === 'ru' ? 'ru' : 'en'}>
                    {children}
                </ThemeProvider>
            </NextIntlClientProvider>
        </PreferencesContext.Provider>
    );
}
