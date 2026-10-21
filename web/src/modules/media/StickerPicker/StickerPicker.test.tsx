import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {act, fireEvent, render, screen, waitFor} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {StickerPicker} from '@/modules/media/StickerPicker/StickerPicker';
import ru from '@/shared/i18n/ru.json';

const mocks = vi.hoisted(() => ({stickers: vi.fn(), stickerSet: vi.fn(), media: vi.fn()}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: {media: mocks},
}));

it('ignores a slow prior sticker set after a newer set has been selected', async () => {
    const media = {
        fileId: 1,
        mimeType: 'image/webp',
        fileName: 'sticker.webp',
        width: 128,
        height: 128,
        size: 100,
        thumbnailFileId: 0,
    };
    const sticker = (id: string, emoji: string) => ({id, emoji, setId: id, media, width: 128, height: 128});
    const first = Promise.withResolvers<ReturnType<typeof sticker>[]>();
    mocks.stickers.mockResolvedValue({
        recent: [],
        favorites: [],
        sets: [
            {id: 'a', title: 'Set A', name: 'a', count: 1},
            {id: 'b', title: 'Set B', name: 'b', count: 1},
        ],
    });
    mocks.stickerSet.mockImplementation(({id}: {id: string}) => {
        if (id === 'a') {
            return first.promise;
        }
        return Promise.resolve([sticker('b', 'B sticker')]);
    });
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <StickerPicker anchor={document.body} onClose={vi.fn()} onPick={async () => {}} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    fireEvent.click(await screen.findByRole('tab', {name: 'Set A'}));
    fireEvent.click(screen.getByRole('tab', {name: 'Set B'}));
    expect(screen.getByRole('tab', {name: 'Set B'})).toHaveAttribute('aria-selected', 'true');
    await screen.findByRole('button', {name: 'B sticker'});
    await act(async () => first.resolve([sticker('a', 'A sticker')]));
    await waitFor(() => expect(screen.queryByRole('button', {name: 'A sticker'})).toBeNull());
    expect(screen.getByRole('button', {name: 'B sticker'})).toBeInTheDocument();
});
