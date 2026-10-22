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
type ThemeMode = 'light' | 'dark' | 'system';
interface PreferencesValue {
    toggleTheme: () => void;
    changeLocale: (locale: Locale) => void;
    locale: Locale;
    theme: 'light' | 'dark';
}
const PreferencesContext = createContext<PreferencesValue>({
    toggleTheme: () => {},
    changeLocale: () => {},
    locale: 'ru' as Locale,
    theme: 'light' as 'light' | 'dark',
});
export const usePreferences = () => useContext(PreferencesContext);
function cookie(name: string) {
    return document.cookie
        .split(';')
        .map((part) => part.trim())
        .find((part) => part.startsWith(name + '='))
        ?.slice(name.length + 1);
}
export function Preferences({children}: {children: ReactNode}) {
    const [mode, setMode] = useState<ThemeMode>('system');
    const [systemDark, setSystemDark] = useState(false);
    const [locale, setLocale] = useState<Locale>('ru');
    const theme = mode === 'system' ? (systemDark ? 'dark' : 'light') : mode;
    useEffect(() => {
        const media = window.matchMedia('(prefers-color-scheme: dark)');
        const update = () => setSystemDark(media.matches);
        update();
        media.addEventListener('change', update);
        function restorePreferences() {
            const saved = cookie('tiho-lang');
            const language = saved && Object.hasOwn(messages, saved) ? (saved as Locale) : 'ru';
            setLocale(language);
            document.documentElement.lang = language;
            const savedTheme = cookie('tiho-theme');
            setMode(savedTheme === 'light' || savedTheme === 'dark' ? savedTheme : 'system');
        }
        restorePreferences();
        window.addEventListener('boltun-tray-refresh', restorePreferences);
        window.addEventListener('aigram-tray-refresh', restorePreferences);
        return () => {
            media.removeEventListener('change', update);
            window.removeEventListener('boltun-tray-refresh', restorePreferences);
            window.removeEventListener('aigram-tray-refresh', restorePreferences);
        };
    }, []);
    function toggleTheme() {
        const next = theme === 'light' ? 'dark' : 'light';
        setMode(next);
        document.cookie = `tiho-theme=${next}; Path=/; SameSite=Strict; Max-Age=31536000`;
    }
    function changeLocale(next: Locale) {
        setLocale(next);
        document.documentElement.lang = next;
        document.cookie = `tiho-lang=${next}; Path=/; SameSite=Strict; Max-Age=31536000`;
    }
    return (
        <PreferencesContext.Provider
            value={{
                toggleTheme,
                changeLocale,
                locale,
                theme,
            }}
        >
            <NextIntlClientProvider locale={locale} messages={messages[locale]} timeZone="Europe/Moscow">
                <ThemeProvider theme={theme} lang={locale === 'ru' ? 'ru' : 'en'}>
                    {children}
                </ThemeProvider>
            </NextIntlClientProvider>
        </PreferencesContext.Provider>
    );
}
