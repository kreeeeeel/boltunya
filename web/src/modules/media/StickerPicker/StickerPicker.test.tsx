import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {act, fireEvent, render, screen, waitFor} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {StickerPicker} from '@/modules/media/StickerPicker/StickerPicker';
import ru from '@/shared/i18n/ru.json';

const mocks = vi.hoisted(() => ({GET: vi.fn()}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: mocks,
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
    const first = Promise.withResolvers<{data: ReturnType<typeof sticker>[]; response: Response}>();
    mocks.GET.mockImplementation((path: string, options?: {params?: {path?: {id: string}}}) => {
        if (path === '/api/stickers') {
            return Promise.resolve({
                data: {
                    recent: [],
                    favorites: [],
                    sets: [
                        {id: 'a', title: 'Set A', name: 'a', count: 1},
                        {id: 'b', title: 'Set B', name: 'b', count: 1},
                    ],
                },
                response: new Response(),
            });
        }
        if (options?.params?.path?.id === 'a') {
            return first.promise;
        }
        return Promise.resolve({data: [sticker('b', 'B sticker')], response: new Response()});
    });
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <StickerPicker anchor={document.body} onClose={vi.fn()} onPick={async () => {}} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    fireEvent.click(await screen.findByRole('button', {name: 'Set A'}));
    fireEvent.click(screen.getByRole('button', {name: 'Set B'}));
    await screen.findByRole('button', {name: 'B sticker'});
    await act(async () => first.resolve({data: [sticker('a', 'A sticker')], response: new Response()}));
    await waitFor(() => expect(screen.queryByRole('button', {name: 'A sticker'})).toBeNull());
    expect(screen.getByRole('button', {name: 'B sticker'})).toBeInTheDocument();
});
