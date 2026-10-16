import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen} from '@testing-library/react';
import {describe, expect, it} from 'vitest';

import {RichText} from '@/modules/workspace/RichText/RichText';
import ru from '@/shared/i18n/ru.json';

describe('Rich message content', () => {
    it('renders clickable links, escaped text and a revealable spoiler', () => {
        const text = '<script> secret https://example.com';
        render(
            <NextIntlClientProvider locale="ru" messages={ru}>
                <ThemeProvider>
                    <RichText text={text} entities={[{offset: 9, length: 6, type: 'spoiler'}]} />
                </ThemeProvider>
            </NextIntlClientProvider>,
        );
        expect(screen.getByRole('link', {name: 'https://example.com'})).toHaveAttribute(
            'href',
            'https://example.com/',
        );
        expect(screen.getByText('<script>', {exact: false})).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', {name: 'Показать спойлер'}));
        expect(screen.queryByRole('button', {name: 'Показать спойлер'})).not.toBeInTheDocument();
        expect(screen.getByText('secret')).toBeInTheDocument();
    });
});
