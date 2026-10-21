import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {MemoryCollectionSettings} from '@/modules/settings/MemoryCollectionSettings/MemoryCollectionSettings';
import {createSettingsDraft} from '@/modules/settings/model/settingsDraft';
import {workspaceState} from '@tests/fixtures/workspace';
import ru from '@/shared/i18n/ru.json';
vi.mock('@/modules/memory/model/useMemoryDocument', () => ({
    useMemoryDocument: () => ({document: undefined, error: '', busy: false}),
}));
it('keeps the existing AI-only collection default and lets the owner opt into other eligible chats', async () => {
    const changed = vi.fn();
    const value = createSettingsDraft(workspaceState.settings);
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <MemoryCollectionSettings value={value} onUpdate={changed} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    expect(screen.getByRole('combobox', {name: ru.memory.collectionScope})).toHaveTextContent(
        ru.memory.collectionScopes['ai-enabled'],
    );
    fireEvent.click(screen.getByRole('combobox', {name: ru.memory.collectionScope}));
    fireEvent.click(await screen.findByRole('option', {name: ru.memory.collectionScopes['all-allowed']}));
    expect(changed).toHaveBeenCalledWith(expect.objectContaining({memoryCollectionScope: 'all-allowed'}));
});
