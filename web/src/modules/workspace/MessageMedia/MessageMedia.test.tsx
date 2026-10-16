import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {beforeEach, expect, it, vi} from 'vitest';
import {MessageMedia} from '@/modules/workspace/MessageMedia/MessageMedia';
import type {Message} from '@/shared/api/client';
import ru from '@/shared/i18n/ru.json';
const mocks = vi.hoisted(() => ({POST: vi.fn()}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: mocks,
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
    mocks.POST.mockReset().mockResolvedValue({data: {ok: true}, response: new Response()});
});
it('does not create an image request when autoload is off and downloads only after a click', async () => {
    mount();
    expect(screen.queryByRole('img')).toBeNull();
    expect(mocks.POST).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', {name: ru.storage.download}));
    await waitFor(() =>
        expect(mocks.POST).toHaveBeenCalledWith('/api/media/{fileId}/download', {
            params: {path: {fileId: 7}},
        }),
    );
    expect(await screen.findByRole('img')).toHaveAttribute('src', '/api/media/7?v=2');
});
it('shows an already cached photo even with autoload disabled', () => {
    mount({...message, media: {...message.media!, downloaded: true}});
    expect(screen.getByRole('img')).toBeInTheDocument();
    expect(mocks.POST).not.toHaveBeenCalled();
});
