import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor, within} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {Conversation} from '@/modules/workspace/Conversation/Conversation';
import {DraftPreparationError} from '@/modules/workspace/model/DraftPreparationError';
import {workspaceState} from '@tests/fixtures/workspace';
import ru from '@/shared/i18n/ru.json';

vi.mock('@/modules/media/MediaViewer/MediaViewerProvider', () => ({
    MediaViewerProvider: ({children}: {children: import('react').ReactNode}) => children,
}));

function renderConversation(onDraft: () => Promise<string>) {
    return render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <Conversation
                    chat={workspaceState.chats[0]}
                    messages={[]}
                    error=""
                    onDraft={onDraft}
                    onRefresh={vi.fn()}
                    onConfigure={vi.fn()}
                    onInspector={vi.fn()}
                    onMemory={vi.fn()}
                    onSend={vi.fn()}
                    onBack={vi.fn()}
                    onEarlier={vi.fn()}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
}

it('shows cancelled preparation as a neutral block and clears it on a successful retry', async () => {
    const draft = vi
        .fn()
        .mockRejectedValueOnce(new DraftPreparationError(ru.draftQueue.cancelled, 'cancelled'))
        .mockResolvedValueOnce('Готовый ответ');
    renderConversation(draft);
    fireEvent.click(screen.getByRole('button', {name: ru.draft}));
    const notice = await screen.findByRole('status', {name: ru.draftQueue.cancelled});
    expect(within(notice).getByText(ru.draftQueue.cancelledDescription)).toBeVisible();
    expect(notice.querySelector('svg')).not.toBeNull();
    expect(screen.queryByRole('alert')).toBeNull();
    fireEvent.click(screen.getByRole('button', {name: ru.draft}));
    await waitFor(() =>
        expect(screen.getByRole('textbox', {name: ru.messagePlaceholder})).toHaveValue('Готовый ответ'),
    );
    expect(screen.queryByRole('status', {name: ru.draftQueue.cancelled})).toBeNull();
});

it('keeps a typed reply and displays a generation failure with its reason', async () => {
    renderConversation(vi.fn().mockRejectedValue(new Error('Модель не ответила.')));
    const input = screen.getByRole('textbox', {name: ru.messagePlaceholder});
    fireEvent.change(input, {target: {value: 'Мой черновик'}});
    fireEvent.click(screen.getByRole('button', {name: ru.draft}));
    const notice = await screen.findByRole('alert', {name: ru.draftQueue.failed});
    expect(notice).toHaveTextContent('Модель не ответила.');
    expect(notice.querySelector('svg')).not.toBeNull();
    expect(input).toHaveValue('Мой черновик');
});
