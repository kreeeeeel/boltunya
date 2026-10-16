import {expect, it} from 'vitest';
import ru from '@/shared/i18n/ru.json';
import en from '@/shared/i18n/en.json';
import kk from '@/shared/i18n/kk.json';
import uk from '@/shared/i18n/uk.json';
import {createTranslator} from 'next-intl';

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

it('all languages contain the same messages and interpolation arguments', () => {
    for (const messages of [en, kk, uk]) {
        const expected = flatten(ru);
        const actual = flatten(messages);
        expect(Object.keys(actual).sort()).toEqual(Object.keys(expected).sort());
        for (const key of Object.keys(expected)) {
            expect(actual[key].match(/\{\w+\}/g)?.sort() ?? []).toEqual(
                expected[key].match(/\{\w+\}/g)?.sort() ?? [],
            );
        }
    }
});

it('renders workflow variables as literal template examples', () => {
    const t = createTranslator({locale: 'ru', messages: ru});
    expect(t('flows.welcome')).toBe('Привет, {{sender}}!');
    expect(t('flows.variables')).toContain('{{data.key}}');
});
