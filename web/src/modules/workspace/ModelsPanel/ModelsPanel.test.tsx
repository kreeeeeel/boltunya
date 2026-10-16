import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen} from '@testing-library/react';
import {expect, it, vi} from 'vitest';

import {ModelsPanel} from '@/modules/workspace/ModelsPanel/ModelsPanel';
import {workspaceState} from '@tests/fixtures/workspace';
import ru from '@/shared/i18n/ru.json';

it('searches model names and identifiers and restores the catalog after an empty result', () => {
    const view = render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ModelsPanel engine={workspaceState.engine} defaultModel="qwen3:1.7b" onRefresh={vi.fn()} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    const rows = () => view.container.querySelectorAll('[data-qa="model-card"]');
    expect(rows()).toHaveLength(3);
    const search = screen.getByRole('textbox', {name: ru.modelSearch.label});
    fireEvent.change(search, {target: {value: 'gemma3:4b'}});
    expect(rows()).toHaveLength(1);
    expect(screen.getByText('Gemma 3 · 4B')).toBeInTheDocument();
    fireEvent.change(search, {target: {value: 'QWEN'}});
    expect(rows()).toHaveLength(2);
    fireEvent.change(search, {target: {value: 'nothing-matches'}});
    expect(screen.getByText(ru.modelSearch.empty)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', {name: ru.modelSearch.clear}));
    expect(rows()).toHaveLength(3);
    expect(search).toHaveValue('');
});
