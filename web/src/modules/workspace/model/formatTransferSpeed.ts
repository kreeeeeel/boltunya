export function formatTransferSpeed(bytesPerSecond: number, locale: string): string {
    const bytes = Math.max(0, Number.isFinite(bytesPerSecond) ? bytesPerSecond : 0);
    const divisor = bytes >= 1024 * 1024 ? 1024 * 1024 : bytes >= 1024 ? 1024 : 1;
    const unit = divisor === 1 ? 'B/s' : divisor === 1024 ? 'KiB/s' : 'MiB/s';
    return new Intl.NumberFormat(locale, {maximumFractionDigits: 1}).format(bytes / divisor) + ' ' + unit;
}
