import {expect, it} from 'vitest';

import {fallbackWallpaper} from '@/modules/workspace/ChatWallpaper/fallbackWallpaper';

it('keeps a chat background stable and provides distinct palettes and dark variants', () => {
    expect(fallbackWallpaper('41', 'light')).toEqual(fallbackWallpaper('41', 'light'));
    expect(fallbackWallpaper('41', 'light')).not.toEqual(fallbackWallpaper('42', 'light'));
    expect(fallbackWallpaper('41', 'light')).not.toEqual(fallbackWallpaper('41', 'dark'));
    expect(fallbackWallpaper('-100123456789', 'dark')).toHaveLength(2);
});
