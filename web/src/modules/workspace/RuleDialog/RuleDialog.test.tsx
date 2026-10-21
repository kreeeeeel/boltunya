import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {expect, it, vi} from 'vitest';

import {RuleDialog} from '@/modules/workspace/RuleDialog/RuleDialog';
import {workspaceState} from '@tests/fixtures/workspace';
import ru from '@/shared/i18n/ru.json';

const preset = {id: 'calm', emoji: '🧊', name: 'Спокойный', instructions: 'Коротко, спокойно и на ты.'};
vi.mock('@/modules/settings/model/useModelProfiles', () => ({
    useModelProfiles: () => ({profiles: [], presets: [], error: '', loading: false, refresh: vi.fn()}),
}));

it('offers named presets without the standalone custom style and saves the reference with personal instructions', async () => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    const chat = structuredClone(workspaceState.chats[0]);
    chat.rule.instructions = 'Не обещай встречи.';
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <RuleDialog
                    chat={chat}
                    customStyles={[preset]}
                    engine={workspaceState.engine}
                    defaultModel={workspaceState.settings.defaultModel}
                    onClose={vi.fn()}
                    onCollectContext={vi.fn()}
                    onDeleted={vi.fn()}
                    onSave={onSave}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    fireEvent.click(screen.getByRole('combobox', {name: ru.style}));
    expect(screen.queryByRole('option', {name: ru.custom})).toBeNull();
    fireEvent.click(await screen.findByRole('option', {name: (name) => name.includes('Спокойный')}));
    fireEvent.click(screen.getByRole('button', {name: ru.save}));
    await waitFor(() =>
        expect(onSave).toHaveBeenCalledWith(
            expect.objectContaining({
                style: 'custom',
                styleId: preset.id,
                instructions: 'Не обещай встречи.',
            }),
        ),
    );
    expect(onSave.mock.calls[0][0].instructions).not.toContain(preset.instructions);
});

it('keeps legacy custom instructions while replacing a missing preset with a concrete style', async () => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    const chat = structuredClone(workspaceState.chats[0]);
    chat.rule = {...chat.rule, style: 'custom', styleId: '', instructions: 'Без длинных ответов.'};
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <RuleDialog
                    chat={chat}
                    customStyles={[]}
                    engine={workspaceState.engine}
                    defaultModel={workspaceState.settings.defaultModel}
                    onClose={vi.fn()}
                    onCollectContext={vi.fn()}
                    onDeleted={vi.fn()}
                    onSave={onSave}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    expect(screen.getByRole('combobox', {name: ru.style})).toHaveTextContent(ru.friendly);
    fireEvent.click(screen.getByRole('button', {name: ru.save}));
    await waitFor(() =>
        expect(onSave).toHaveBeenCalledWith(
            expect.objectContaining({style: 'friendly', styleId: '', instructions: 'Без длинных ответов.'}),
        ),
    );
});
