import {Box, ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {act, fireEvent, render, screen} from '@testing-library/react';
import {expect, it, vi} from 'vitest';

import {ScrollArea} from '@/shared/ui/ScrollArea/ScrollArea';
import ru from '@/shared/i18n/ru.json';

it('uses live dimensions when pagination expands content before ResizeObserver fires', () => {
    let resize = () => {};
    vi.stubGlobal(
        'ResizeObserver',
        class {
            constructor(callback: () => void) {
                resize = callback;
            }
            observe() {}
            disconnect() {}
        },
    );
    const view = render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ScrollArea qa="viewport">
                    <Box>Content</Box>
                </ScrollArea>
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    const viewport = view.container.querySelector('[data-qa="viewport"]')!;
    Object.defineProperties(viewport, {
        clientHeight: {configurable: true, value: 200},
        scrollHeight: {configurable: true, value: 600},
    });
    act(resize);
    const scrollbar = screen.getByRole('scrollbar');
    fireEvent.keyDown(scrollbar, {key: 'End'});
    expect(viewport.scrollTop).toBe(400);
    Object.defineProperty(viewport, 'scrollHeight', {configurable: true, value: 1000});
    fireEvent.keyDown(scrollbar, {key: 'End'});
    expect(viewport.scrollTop).toBe(800);
    expect(scrollbar).toHaveAttribute('aria-valuenow', '100');
    fireEvent.keyDown(scrollbar, {key: 'PageUp'});
    expect(viewport.scrollTop).toBe(600);
    fireEvent.keyDown(scrollbar, {key: 'Home'});
    expect(viewport.scrollTop).toBe(0);
});
