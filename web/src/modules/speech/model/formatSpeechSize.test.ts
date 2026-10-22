import {expect, it} from 'vitest';
import {formatSpeechSize} from '@/modules/speech/model/formatSpeechSize';

it('uses binary units and a readable size for the optional multi-gigabyte engine', () => {
    expect(formatSpeechSize(6775972339, 'en')).toBe('6.31 GiB');
    expect(formatSpeechSize(21149417, 'ru')).toBe('20,2 MiB');
});
