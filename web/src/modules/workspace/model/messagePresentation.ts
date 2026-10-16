export function isEmojiMessage(text: string): boolean {
    const graphemes = [...new Intl.Segmenter(undefined, {granularity: 'grapheme'}).segment(text.trim())];
    return (
        graphemes.length > 0 &&
        graphemes.length <= 3 &&
        graphemes.every(
            ({segment}) =>
                /^[\p{Extended_Pictographic}\p{Regional_Indicator}\p{Emoji_Modifier}\uFE0F\u200D\u20E3\d#*]+$/u.test(
                    segment,
                ) && /[\p{Extended_Pictographic}\p{Regional_Indicator}\u20E3]/u.test(segment),
        )
    );
}

export function messageDay(timestamp: number): string {
    return new Date(timestamp * 1000).toLocaleDateString('en-CA', {timeZone: 'Europe/Moscow'});
}
