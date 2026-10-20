import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {useState} from 'react';

import {StylesDialog} from '@/modules/workspace/StylesDialog/StylesDialog';
import ru from '@/shared/i18n/ru.json';

const mocks = vi.hoisted(() => ({PATCH: vi.fn()}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: mocks,
}));

it('keeps success feedback visible after refreshed custom styles arrive from the backend', async () => {
    const original = [{id: 'style-1', emoji: '🧊', name: 'Спокойный', instructions: 'Коротко и спокойно.'}];
    const onClose = vi.fn();
    mocks.PATCH.mockResolvedValue({data: {ok: true}, response: new Response()});
    function Example() {
        const [styles, setStyles] = useState(original);
        return (
            <NextIntlClientProvider locale="ru" messages={ru}>
                <ThemeProvider>
                    <StylesDialog
                        styles={styles}
                        onClose={onClose}
                        onRefresh={async () => {
                            setStyles(structuredClone(original));
                        }}
                    />
                </ThemeProvider>
            </NextIntlClientProvider>
        );
    }
    render(<Example />);
    fireEvent.click(screen.getByRole('button', {name: ru.save}));
    await waitFor(() => expect(mocks.PATCH).toHaveBeenCalled());
    expect(await screen.findByRole('button', {name: ru.saved})).toBeVisible();
    expect(onClose).not.toHaveBeenCalled();
    fireEvent.change(screen.getByRole('textbox', {name: ru.styleName}), {target: {value: 'Сдержанный'}});
    expect(screen.queryByRole('button', {name: ru.saved})).toBeNull();
});
