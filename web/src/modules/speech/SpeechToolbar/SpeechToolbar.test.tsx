import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {SpeechToolbar} from '@/modules/speech/SpeechToolbar/SpeechToolbar';
import ru from '@/shared/i18n/ru.json';

it('uses the same compact button view for explicit refresh, explorer and import actions', () => {
    const refresh = vi.fn();
    const browse = vi.fn().mockResolvedValue(undefined);
    const importModel = vi.fn();
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <SpeechToolbar onRefresh={refresh} onBrowse={browse} onImport={importModel} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    const buttons = [ru.voice.refreshList, ru.voice.openFolder, ru.voice.importModel].map((name) =>
        screen.getByRole('button', {name}),
    );
    buttons.forEach((button) => {
        expect(button).toHaveClass('g-button_view_flat-secondary', 'g-button_size_l');
        fireEvent.click(button);
    });
    expect(refresh).toHaveBeenCalledOnce();
    expect(browse).toHaveBeenCalledOnce();
    expect(importModel).toHaveBeenCalledOnce();
});
