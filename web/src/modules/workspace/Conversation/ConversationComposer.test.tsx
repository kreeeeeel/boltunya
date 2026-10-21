import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen} from '@testing-library/react';
import {useState} from 'react';
import {expect, it, vi} from 'vitest';
import {ConversationComposer} from '@/modules/workspace/Conversation/ConversationComposer';
import ru from '@/shared/i18n/ru.json';

const chatUi = {photos: 'Фотографии', videos: 'Видео', messageActions: 'Действия с сообщением'};

function Example() {
    const [text, setText] = useState('Before selected after');
    return (
        <NextIntlClientProvider locale="ru" messages={{...ru, chatUi}}>
            <ThemeProvider>
                <ConversationComposer
                    chatId="42"
                    text={text}
                    onTextChange={setText}
                    busy=""
                    onSend={vi.fn()}
                    onDraft={vi.fn()}
                    onMediaSent={vi.fn()}
                />
            </ThemeProvider>
        </NextIntlClientProvider>
    );
}

it('keeps the input visible during queued generation and preserves a typed draft', () => {
    const update = vi.fn();
    render(
        <NextIntlClientProvider locale="ru" messages={{...ru, chatUi}}>
            <ThemeProvider>
                <ConversationComposer
                    chatId="42"
                    text="My draft"
                    onTextChange={update}
                    busy="draft"
                    onSend={vi.fn()}
                    onDraft={vi.fn()}
                    onMediaSent={vi.fn()}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    expect(screen.getByRole('textbox', {name: ru.messagePlaceholder})).toHaveValue('My draft');
    fireEvent.change(screen.getByRole('textbox', {name: ru.messagePlaceholder}), {
        target: {value: 'Edited draft'},
    });
    expect(update).toHaveBeenCalledWith('Edited draft');
    expect(screen.getByRole('button', {name: ru.draft})).toBeDisabled();
    expect(document.querySelector('.g-skeleton')).toBeNull();
});

it('removes emoji and sticker controls while exposing manual voice without changing the plain draft', () => {
    render(<Example />);
    expect(screen.getByRole('textbox', {name: ru.messagePlaceholder})).toHaveValue('Before selected after');
    expect(screen.queryByRole('button', {name: ru.chooseEmoji})).not.toBeInTheDocument();
    expect(screen.queryByRole('button', {name: 'Стикеры'})).not.toBeInTheDocument();
    expect(screen.getByRole('button', {name: ru.voice.manualTitle})).toBeEnabled();
    expect(document.querySelector('[data-qa="sticker-picker"]')).toBeNull();
});

it('offers separate photo video and document actions with icons and matching file filters', async () => {
    render(<Example />);
    fireEvent.click(screen.getByRole('button', {name: 'Прикрепить файл'}));
    const photos = await screen.findByRole('menuitem', {name: chatUi.photos});
    expect(photos.querySelector('.g-menu__item-icon svg')).not.toBeNull();
    const videos = screen.getByRole('menuitem', {name: chatUi.videos});
    expect(videos.querySelector('.g-menu__item-icon svg')).not.toBeNull();
    expect(
        screen.getByRole('menuitem', {name: 'Документы'}).querySelector('.g-menu__item-icon svg'),
    ).not.toBeNull();
    fireEvent.click(videos);
    expect(document.querySelector('input[type="file"]')).toHaveAttribute('accept', 'video/mp4,video/webm');
    fireEvent.click(screen.getByRole('button', {name: 'Прикрепить файл'}));
    fireEvent.click(await screen.findByRole('menuitem', {name: chatUi.photos}));
    expect(document.querySelector('input[type="file"]')).toHaveAttribute(
        'accept',
        'image/jpeg,image/png,image/webp',
    );
});
