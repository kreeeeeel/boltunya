import {expect, it} from 'vitest';
import {audioTime, decodeWaveform} from '@/modules/workspace/MessageMedia/waveform';

it('decodes Telegram five-bit voice waveforms without reading beyond the data', () => {
    expect(decodeWaveform([])).toEqual([]);
    expect(decodeWaveform([255])).toEqual([31]);
    expect(decodeWaveform([255, 255, 255, 255, 255])).toEqual(Array(8).fill(31));
    expect(decodeWaveform([32, 0])).toEqual([0, 1, 0]);
});

it('formats audio positions and handles unavailable metadata', () => {
    expect(audioTime(72.9)).toBe('1:12');
    expect(audioTime(Infinity)).toBe('0:00');
});
