import {expect, it} from 'vitest';
import ru from '@/shared/i18n/ru.json';
import en from '@/shared/i18n/en.json';
import kk from '@/shared/i18n/kk.json';
import uk from '@/shared/i18n/uk.json';

function flatten(messages: Record<string, unknown>, prefix = ''): Record<string, string> {
    return Object.fromEntries(
        Object.entries(messages).flatMap(([key, value]) => {
            const name = prefix + key;
            return typeof value === 'string'
                ? [[name, value]]
                : Object.entries(flatten(value as Record<string, unknown>, name + '.'));
        }),
    );
}

function argumentsIn(value: string) {
    return value
        .split('{')
        .slice(1)
        .flatMap((part) => {
            const name = part.slice(0, part.indexOf('}'));
            return name &&
                Array.from(name).every((character) =>
                    'abcdefghijklmnopqrstuvwxyz0123456789_'.includes(character.toLowerCase()),
                )
                ? [name]
                : [];
        })
        .sort();
}

it('all languages contain the same messages and interpolation arguments', () => {
    for (const messages of [en, kk, uk]) {
        const expected = flatten(ru);
        const actual = flatten(messages);
        expect(Object.keys(actual).sort()).toEqual(Object.keys(expected).sort());
        for (const key of Object.keys(expected)) {
            expect(argumentsIn(actual[key])).toEqual(argumentsIn(expected[key]));
        }
    }
});
