export function speechLanguageName(language: string, locale: string): string {
    try {
        return new Intl.DisplayNames([locale], {type: 'language'}).of(language) ?? language;
    } catch {
        return language;
    }
}
