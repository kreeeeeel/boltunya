export function formatSpeechSize(bytes: number, locale: string): string {
    const gigabytes = bytes >= 1024 ** 3;
    const number = new Intl.NumberFormat(locale, {
        maximumFractionDigits: gigabytes ? 2 : 1,
    }).format(bytes / 1024 ** (gigabytes ? 3 : 2));
    return `${number} ${gigabytes ? 'GiB' : 'MiB'}`;
}
