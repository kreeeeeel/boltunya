import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {beforeEach, expect, it, vi} from 'vitest';
import {MessageMedia} from '@/modules/workspace/MessageMedia/MessageMedia';
import type {Message} from '@/shared/api/client';
import ru from '@/shared/i18n/ru.json';
const mocks = vi.hoisted(() => ({downloadMedia: vi.fn()}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: {
        media: {
            downloadMedia: mocks.downloadMedia,
        },
    },
}));
const message: Message = {
    id: '1',
    chatId: '42',
    text: '',
    outgoing: false,
    date: 1,
    kind: 'photo',
    media: {
        fileId: 7,
        mimeType: 'image/jpeg',
        fileName: 'photo.jpg',
        width: 320,
        height: 240,
        size: 10,
        thumbnailFileId: 0,
        autoDownload: false,
        downloaded: false,
        cacheVersion: 2,
    },
};
function mount(value = message) {
    return render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <MessageMedia message={value} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
}
beforeEach(() => {
    mocks.downloadMedia.mockReset().mockResolvedValue({ok: true});
});
it('does not create an image request when autoload is off and downloads only after a click', async () => {
    mount();
    expect(screen.queryByRole('img')).toBeNull();
    expect(mocks.downloadMedia).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', {name: ru.storage.download}));
    await waitFor(() =>
        expect(mocks.downloadMedia).toHaveBeenCalledWith({
            fileId: 7,
        }),
    );
    expect(await screen.findByRole('img')).toHaveAttribute('src', '/api/media/7?v=2');
});
it('shows an already cached photo even with autoload disabled', () => {
    mount({...message, media: {...message.media!, downloaded: true}});
    expect(screen.getByRole('img')).toBeInTheDocument();
    expect(mocks.downloadMedia).not.toHaveBeenCalled();
});

it('keeps images inline without a photo viewer action or overlay', () => {
    mount({...message, media: {...message.media!, downloaded: true}});
    fireEvent.load(screen.getByRole('img'));
    fireEvent.click(screen.getByRole('img'));
    expect(screen.queryByRole('button', {name: ru.openPhoto})).not.toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByRole('img')).toHaveAttribute('src', '/api/media/7?v=2');
});
