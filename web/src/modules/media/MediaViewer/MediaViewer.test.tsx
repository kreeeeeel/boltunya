import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import type {Message} from '@/shared/api/client';
import {MediaViewer} from '@/modules/media/MediaViewer/MediaViewer';
import ru from '@/shared/i18n/ru.json';
import labels from '@/modules/media/i18n/ru.json';

it('navigates photos, resets zoom, and closes with Escape without sending messages', () => {
    const media = {
        fileId: 7,
        mimeType: 'image/jpeg',
        fileName: 'photo.jpg',
        width: 400,
        height: 300,
        size: 10,
        thumbnailFileId: 0,
        downloaded: false,
    };
    const messages: Message[] = [1, 2].map((id) => ({
        id: String(id),
        chatId: '42',
        text: `Caption ${id}`,
        date: id,
        outgoing: false,
        kind: 'photo',
        media: {...media, fileId: id},
    }));
    const close = vi.fn();
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <MediaViewer messages={messages} initialId="1" onClose={close} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    expect(screen.getByText('Caption 1')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', {name: labels.zoomIn}));
    expect(screen.getByText('150%')).toBeInTheDocument();
    fireEvent.keyDown(window, {key: 'ArrowRight'});
    expect(screen.getByText('Caption 2')).toBeInTheDocument();
    expect(screen.getByText('100%')).toBeInTheDocument();
    expect(screen.queryByRole('button', {name: labels.next})).toBeNull();
    fireEvent.keyDown(window, {key: 'Escape'});
    expect(close).toHaveBeenCalledOnce();
});
