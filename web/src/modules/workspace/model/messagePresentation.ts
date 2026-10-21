export function isEmojiMessage(text: string): boolean {
    const graphemes = [...new Intl.Segmenter(undefined, {granularity: 'grapheme'}).segment(text.trim())];
    return (
        graphemes.length > 0 &&
        graphemes.length <= 3 &&
        graphemes.every(({segment}) => {
            const base = Array.from(segment)
                .filter((character) => {
                    const point = character.codePointAt(0) ?? 0;
                    return point < 0x1f3fb || point > 0x1f3ff;
                })
                .join('');
            return Object.hasOwn(emoji, base) || Object.hasOwn(emoji, base + '\uFE0F');
        })
    );
}

export function messageDay(timestamp: number): string {
    return new Date(timestamp * 1000).toLocaleDateString('en-CA', {timeZone: 'Europe/Moscow'});
}
import emoji from 'unicode-emoji-json';
