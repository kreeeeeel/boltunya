import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {act, fireEvent, render, screen, waitFor, within} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import '@/modules/messages/MessageEditEditor/MessageEditEditor';
import {Conversation} from '@/modules/workspace/Conversation/Conversation';
import {DraftPreparationError} from '@/modules/workspace/model/DraftPreparationError';
import {workspaceState} from '@tests/fixtures/workspace';
import ru from '@/shared/i18n/ru.json';
import type {Message, Reply} from '@/shared/api/client';

vi.mock('@/modules/media/MediaViewer/MediaViewerProvider', () => ({
    MediaViewerProvider: ({children}: {children: import('react').ReactNode}) => children,
    useMediaViewer: () => undefined,
}));

function renderConversation(onDraft: () => Promise<Reply>, onSend = vi.fn(), messages: Message[] = []) {
    return render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <Conversation
                    chat={workspaceState.chats[0]}
                    messages={messages}
                    error=""
                    onDraft={onDraft}
                    onRefresh={vi.fn()}
                    onConfigure={vi.fn()}
                    onInspector={vi.fn()}
                    onMemory={vi.fn()}
                    onSend={onSend}
                    onBack={vi.fn()}
                    onEarlier={vi.fn()}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
}

async function requestDraft() {
    fireEvent.click(screen.getByRole('button', {name: ru.draft}));
    fireEvent.click(await screen.findByRole('menuitem', {name: ru.voice.generateText}));
    await act(async () => {
        await vi.dynamicImportSettled();
    });
}

it('shows cancelled preparation as a neutral block and clears it on a successful retry', async () => {
    const draft = vi
        .fn()
        .mockRejectedValueOnce(new DraftPreparationError(ru.draftQueue.cancelled, 'cancelled'))
        .mockResolvedValueOnce({text: 'Готовый ответ', durationMs: 100, tokensPerSecond: 10});
    renderConversation(draft);
    await requestDraft();
    const notice = await screen.findByRole('status', {name: ru.draftQueue.cancelled});
    expect(within(notice).getByText(ru.draftQueue.cancelledDescription)).toBeVisible();
    expect(notice.querySelector('svg')).not.toBeNull();
    expect(screen.queryByRole('alert')).toBeNull();
    await requestDraft();
    await waitFor(() =>
        expect(screen.getByRole('textbox', {name: ru.messagePlaceholder})).toHaveValue('Готовый ответ'),
    );
    expect(screen.queryByRole('status', {name: ru.draftQueue.cancelled})).toBeNull();
});

it('renders a generated draft as real formatting and sends its Telegram entity ranges', async () => {
    const entities = [
        {offset: 3, length: 6, type: 'bold'},
        {offset: 11, length: 4, type: 'spoiler'},
    ];
    const send = vi.fn().mockResolvedValue(undefined);
    renderConversation(
        vi.fn().mockResolvedValue({text: '🙂 Привет, друг', entities, durationMs: 100, tokensPerSecond: 10}),
        send,
    );
    await requestDraft();
    await waitFor(() =>
        expect(screen.getByRole('textbox', {name: ru.messagePlaceholder})).toHaveAttribute(
            'contenteditable',
            'true',
        ),
    );
    const editor = screen.getByRole('textbox', {name: ru.messagePlaceholder});
    await waitFor(() => expect(editor).toHaveTextContent('🙂 Привет, друг'));
    expect(editor).toHaveAttribute('contenteditable', 'true');
    await waitFor(() => expect(screen.getByRole('button', {name: ru.send})).toBeEnabled());
    fireEvent.click(screen.getByRole('button', {name: ru.send}));
    await waitFor(() => expect(send).toHaveBeenCalledWith('🙂 Привет, друг', entities));
    await waitFor(() => expect(screen.getByRole('textbox', {name: ru.messagePlaceholder})).toHaveValue(''));
});

it('clears old formatting when the next generated draft is plain text', async () => {
    const send = vi.fn().mockResolvedValue(undefined);
    renderConversation(
        vi
            .fn()
            .mockResolvedValueOnce({
                text: 'Привет',
                entities: [{offset: 0, length: 6, type: 'bold'}],
                durationMs: 1,
                tokensPerSecond: 1,
            })
            .mockResolvedValueOnce({text: 'Другой ответ', entities: [], durationMs: 1, tokensPerSecond: 1}),
        send,
    );
    await requestDraft();
    await waitFor(() =>
        expect(screen.getByRole('textbox', {name: ru.messagePlaceholder})).toHaveTextContent('Привет'),
    );
    await requestDraft();
    await waitFor(() =>
        expect(screen.getByRole('textbox', {name: ru.messagePlaceholder})).toHaveValue('Другой ответ'),
    );
    fireEvent.click(screen.getByRole('button', {name: ru.send}));
    await waitFor(() => expect(send).toHaveBeenCalledWith('Другой ответ'));
});

it('blocks sending when a rich draft cannot preserve its entities', async () => {
    const send = vi.fn();
    renderConversation(
        vi.fn().mockResolvedValue({
            text: 'Hi',
            entities: [{offset: 0, length: 100, type: 'bold'}],
            durationMs: 1,
            tokensPerSecond: 1,
        }),
        send,
    );
    await requestDraft();
    expect(await screen.findByRole('alert')).toHaveTextContent(ru.formattedDraftError);
    expect(screen.getByRole('button', {name: ru.send})).toBeDisabled();
    expect(send).not.toHaveBeenCalled();
});

it('keeps a typed reply and displays a generation failure with its reason', async () => {
    renderConversation(vi.fn().mockRejectedValue(new Error('Модель не ответила.')));
    const input = screen.getByRole('textbox', {name: ru.messagePlaceholder});
    fireEvent.change(input, {target: {value: 'Мой черновик'}});
    await requestDraft();
    const notice = await screen.findByRole('alert', {name: ru.draftQueue.failed});
    expect(notice).toHaveTextContent('Модель не ответила.');
    expect(notice.querySelector('svg')).not.toBeNull();
    expect(input).toHaveValue('Мой черновик');
});

it('keeps the bottom on a resize without moving older history or an open message menu', async () => {
    const callbacks = new Map<Element, () => void>();
    vi.stubGlobal(
        'ResizeObserver',
        class {
            private readonly callback: () => void;
            constructor(callback: () => void) {
                this.callback = callback;
            }
            observe(element: Element) {
                callbacks.set(element, this.callback);
            }
            unobserve(element: Element) {
                callbacks.delete(element);
            }
            disconnect() {}
        },
    );
    const view = renderConversation(vi.fn(), vi.fn(), [
        {
            id: '1',
            chatId: workspaceState.chats[0].id,
            text: 'Message',
            outgoing: false,
            date: 1,
            kind: 'text',
        },
    ]);
    const viewport = view.container.querySelector<HTMLElement>('[data-qa="conversation-messages"]')!;
    Object.defineProperties(viewport, {
        clientHeight: {configurable: true, value: 200},
        scrollHeight: {configurable: true, value: 1000},
    });
    act(() => callbacks.get(viewport)?.());
    expect(viewport.scrollTop).toBe(800);
    Object.defineProperty(viewport, 'clientHeight', {configurable: true, value: 100});
    act(() => callbacks.get(viewport)?.());
    expect(viewport.scrollTop).toBe(900);
    viewport.scrollTop = 300;
    fireEvent.scroll(viewport);
    Object.defineProperty(viewport, 'scrollHeight', {configurable: true, value: 1200});
    act(() => callbacks.get(viewport)?.());
    expect(viewport.scrollTop).toBe(300);
    viewport.scrollTop = 1100;
    fireEvent.scroll(viewport);
    const row = view.container.querySelector('[data-message-id="1"]')!;
    fireEvent.contextMenu(row, {clientX: 10, clientY: 10});
    await screen.findByRole('menuitem', {name: ru.messageMenu.copyText});
    viewport.scrollTop = 1050;
    Object.defineProperty(viewport, 'scrollHeight', {configurable: true, value: 1300});
    act(() => callbacks.get(viewport)?.());
    expect(viewport.scrollTop).toBe(1050);
});
