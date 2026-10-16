export function decodeWaveform(packed: number[]): number[] {
    return Array.from({length: Math.floor((packed.length * 8) / 5)}, (_, index) => {
        const bit = index * 5;
        const offset = Math.floor(bit / 8);
        return ((packed[offset] | ((packed[offset + 1] ?? 0) << 8)) >>> bit % 8) & 31;
    });
}

export function audioTime(seconds: number): string {
    const value = Number.isFinite(seconds) ? Math.max(0, Math.floor(seconds)) : 0;
    return `${Math.floor(value / 60)}:${String(value % 60).padStart(2, '0')}`;
}
