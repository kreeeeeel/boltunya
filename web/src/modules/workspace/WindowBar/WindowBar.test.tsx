import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {act, fireEvent, render, screen} from '@testing-library/react';
import {expect, it, vi} from 'vitest';

import {WindowBar} from '@/modules/workspace/WindowBar/WindowBar';
import {workspaceState} from '@tests/fixtures/workspace';
import ru from '@/shared/i18n/ru.json';

it('shows an immediate pending state and real model warmup while keeping pause available', async () => {
    const pending = Promise.withResolvers<void>();
    const onPause = vi.fn(() => pending.promise);
    const state = {...workspaceState, settings: {...workspaceState.settings, paused: true}};
    const content = (value: typeof state) => (
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <WindowBar state={value} onPause={onPause} onError={vi.fn()} />
            </ThemeProvider>
        </NextIntlClientProvider>
    );
    const view = render(content(state));
    fireEvent.click(screen.getByRole('button', {name: ru.resume}));
    expect(screen.getByRole('button', {name: ru.resume})).toBeDisabled();
    expect(screen.getByRole('status')).toHaveTextContent(ru.aiControl.updating);
    await act(async () => pending.resolve());
    view.rerender(
        content({
            ...state,
            settings: {...state.settings, paused: false},
            engine: {...state.engine, warming: 'qwen3:1.7b'},
        }),
    );
    expect(screen.getByRole('status')).toHaveTextContent(ru.aiControl.warming);
    expect(screen.getByRole('button', {name: ru.pause})).toBeEnabled();
    expect(screen.getByRole('button', {name: ru.pause})).toHaveAttribute('aria-busy', 'true');
});
