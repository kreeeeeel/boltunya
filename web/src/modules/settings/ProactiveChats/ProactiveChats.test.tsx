import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {ProactiveChats} from '@/modules/settings/ProactiveChats/ProactiveChats';
import {workspaceState} from '@tests/fixtures/workspace';
import ru from '@/shared/i18n/ru.json';

const chats = Array.from({length: 65}, (_, index) => ({
    ...workspaceState.chats[0],
    id: String(index),
    title: 'Чат ' + index,
    kind: 'private' as const,
    lastMessageId: '99',
    rule: {...workspaceState.chats[0].rule, enabled: false, proactiveMode: 'enabled' as const},
}));

it('pages the complete configured list and opens the exact chat without enabling automatic replies', async () => {
    const configure = vi.fn();
    const {container} = render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ProactiveChats chats={chats} settings={workspaceState.settings} onConfigure={configure} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    expect(container.querySelectorAll('[data-qa="proactive-chat"]')).toHaveLength(30);
    expect(screen.getByText('65')).toBeVisible();
    fireEvent.click(screen.getByRole('button', {name: ru.proactive.loadMore}));
    await waitFor(() => expect(container.querySelectorAll('[data-qa="proactive-chat"]')).toHaveLength(60));
    fireEvent.click(screen.getByRole('button', {name: ru.proactive.loadMore}));
    await waitFor(() => expect(container.querySelectorAll('[data-qa="proactive-chat"]')).toHaveLength(65));
    expect(screen.queryByRole('button', {name: ru.proactive.loadMore})).toBeNull();
    fireEvent.click(screen.getByRole('button', {name: 'Настроить Чат 64'}));
    expect(configure).toHaveBeenCalledExactlyOnceWith(chats[64]);
    expect(chats[64].rule.enabled).toBe(false);
});

it('preserves unsaved global changes by disabling chat navigation until they are saved', () => {
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ProactiveChats
                    chats={[chats[0]]}
                    settings={workspaceState.settings}
                    disabled
                    onConfigure={vi.fn()}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    expect(screen.getByText(ru.proactive.saveBeforeChat)).toBeVisible();
    expect(screen.getByRole('button', {name: 'Настроить Чат 0'})).toBeDisabled();
});
