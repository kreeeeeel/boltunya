import {expect, it} from 'vitest';
import {formatTransferSpeed} from '@/modules/workspace/model/formatTransferSpeed';

it('keeps small transfer rates visible and formats binary units without negative speeds', () => {
    expect(formatTransferSpeed(512, 'en')).toBe('512 B/s');
    expect(formatTransferSpeed(1536, 'en')).toBe('1.5 KiB/s');
    expect(formatTransferSpeed(1.5 * 1024 * 1024, 'ru')).toBe('1,5 MiB/s');
    expect(formatTransferSpeed(-1, 'en')).toBe('0 B/s');
});
