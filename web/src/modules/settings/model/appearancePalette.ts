import {validThemeColor} from '@/shared/theme/appearance';
import type {Appearance} from '@/shared/theme/appearance';

const palettes = {
    light: {
        accent: '#2878c9',
        background: '#f3f5f7',
        panel: '#ffffff',
        text: '#273441',
        muted: '#647383',
        incoming: '#ffffff',
        outgoing: '#dff1dc',
        border: '#e8ecf0',
    },
    dark: {
        accent: '#65abef',
        background: '#171b22',
        panel: '#242a34',
        text: '#e7edf3',
        muted: '#9caab8',
        incoming: '#2c3541',
        outgoing: '#315577',
        border: '#343e4b',
    },
};

export function appearancePalette(value: Appearance, systemTheme: 'light' | 'dark') {
    const theme = value.mode === 'system' ? systemTheme : value.mode;
    return Object.fromEntries(
        Object.entries(palettes[theme]).map(([token, fallback]) => [
            token,
            validThemeColor(value.tokens[token] ?? '') ? value.tokens[token] : fallback,
        ]),
    ) as typeof palettes.light;
}
