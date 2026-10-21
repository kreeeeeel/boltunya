'use client';
import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {createContext, useContext, useEffect, useState} from 'react';
import type {ReactNode} from 'react';
import {api, errorMessage} from '@/shared/api/client';
import {compileAppearance, defaultAppearance} from '@/shared/theme/appearance';
import type {Appearance} from '@/shared/theme/appearance';
import en from '@/shared/i18n/en.json';
import ru from '@/shared/i18n/ru.json';
import kk from '@/shared/i18n/kk.json';
import uk from '@/shared/i18n/uk.json';
const messages = {ru, en, kk, uk};
type Locale = keyof typeof messages;
interface PreferencesValue {
    toggleTheme: () => void;
    changeLocale: (locale: Locale) => void;
    locale: Locale;
    theme: 'light' | 'dark';
    appearance: Appearance;
    appearanceError: string;
    previewAppearance: (appearance: Appearance) => void;
    saveAppearance: (appearance: Appearance) => Promise<void>;
}
const PreferencesContext = createContext<PreferencesValue>({
    toggleTheme: () => {},
    changeLocale: () => {},
    locale: 'ru' as Locale,
    theme: 'light' as 'light' | 'dark',
    appearance: defaultAppearance,
    appearanceError: '',
    previewAppearance: () => {},
    saveAppearance: async (): Promise<void> => {},
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
    const [appearance, setAppearance] = useState<Appearance>(defaultAppearance);
    const [preview, setPreview] = useState<Appearance>(defaultAppearance);
    const [appearanceError, setAppearanceError] = useState('');
    const [systemDark, setSystemDark] = useState(false);
    const [locale, setLocale] = useState<Locale>('ru');
    const theme = preview.mode === 'system' ? (systemDark ? 'dark' : 'light') : preview.mode;
    useEffect(() => {
        const media = window.matchMedia('(prefers-color-scheme: dark)');
        const update = () => setSystemDark(media.matches);
        update();
        media.addEventListener('change', update);
        function restoreLocale() {
            const saved = cookie('tiho-lang');
            const language = saved && Object.hasOwn(messages, saved) ? (saved as Locale) : 'ru';
            setLocale(language);
            document.documentElement.lang = language;
        }
        restoreLocale();
        void api.settings
            .appearance()
            .then((result) => {
                const saved = result;
                compileAppearance(saved);
                setAppearance(saved);
                setPreview(saved);
            })
            .catch((cause) => setAppearanceError(errorMessage(cause)));
        window.addEventListener('boltun-tray-refresh', restoreLocale);
        window.addEventListener('aigram-tray-refresh', restoreLocale);
        return () => {
            media.removeEventListener('change', update);
            window.removeEventListener('boltun-tray-refresh', restoreLocale);
            window.removeEventListener('aigram-tray-refresh', restoreLocale);
        };
    }, []);
    useEffect(() => {
        const element = document.createElement('style');
        element.dataset.aigramTheme = 'true';
        try {
            element.textContent = compileAppearance(preview);
        } catch (cause) {
            queueMicrotask(() => setAppearanceError(errorMessage(cause)));
        }
        document.head.appendChild(element);
        return () => element.remove();
    }, [preview]);
    function previewAppearance(next: Appearance) {
        compileAppearance(next);
        setPreview(next);
    }
    async function saveAppearance(next: Appearance) {
        compileAppearance(next);
        const saved = await api.settings.saveAppearance({
            appearanceSettings: next,
        });
        compileAppearance(saved);
        setAppearance(saved);
        setPreview(saved);
        setAppearanceError('');
        document.cookie = `tiho-theme=${saved.mode}; Path=/; SameSite=Strict; Max-Age=31536000`;
    }
    function toggleTheme() {
        const next = {...appearance, mode: theme === 'light' ? ('dark' as const) : ('light' as const)};
        void saveAppearance(next).catch((cause) => setAppearanceError(errorMessage(cause)));
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
                appearance,
                appearanceError,
                previewAppearance,
                saveAppearance,
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
