import palettes from '@/modules/workspace/ChatWallpaper/fallback-palettes.json';

export function fallbackWallpaper(chatId: string, theme: 'light' | 'dark') {
    const seed = Array.from(chatId).reduce(
        (hash, character) => (hash * 31 + character.charCodeAt(0)) >>> 0,
        0,
    );
    return palettes[seed % palettes.length][theme];
}
