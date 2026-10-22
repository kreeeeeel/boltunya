import {Box, Button, Text} from '@gravity-ui/uikit';
import {act, fireEvent, render, screen} from '@testing-library/react';
import {afterEach, beforeEach, expect, it, vi} from 'vitest';
import {Preferences, usePreferences} from '@/shared/theme/Preferences';

function Example() {
    const preferences = usePreferences();
    return (
        <Box>
            <Text data-testid="theme">{preferences.theme}</Text>
            <Text data-testid="locale">{preferences.locale}</Text>
            <Button onClick={preferences.toggleTheme}>Toggle theme</Button>
            <Button onClick={() => preferences.changeLocale('en')}>English</Button>
        </Box>
    );
}

beforeEach(() => {
    document.cookie = 'tiho-theme=; Path=/; Max-Age=0';
    document.cookie = 'tiho-lang=; Path=/; Max-Age=0';
});

afterEach(() => {
    document.cookie = 'tiho-theme=; Path=/; Max-Age=0';
    document.cookie = 'tiho-lang=; Path=/; Max-Age=0';
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
});

it('restores the built-in theme and locale without requesting or injecting a custom theme', () => {
    document.cookie = 'tiho-theme=dark; Path=/';
    document.cookie = 'tiho-lang=en; Path=/';
    const fetch = vi.fn();
    vi.stubGlobal('fetch', fetch);
    render(
        <Preferences>
            <Example />
        </Preferences>,
    );
    expect(screen.getByTestId('theme')).toHaveTextContent('dark');
    expect(screen.getByTestId('locale')).toHaveTextContent('en');
    expect(document.documentElement.lang).toBe('en');
    expect(document.querySelector('style[data-aigram-theme]')).toBeNull();
    expect(fetch).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', {name: 'Toggle theme'}));
    expect(screen.getByTestId('theme')).toHaveTextContent('light');
    expect(document.cookie).toContain('tiho-theme=light');
    expect(fetch).not.toHaveBeenCalled();
});

it('uses the system preference for an unknown cookie and synchronizes tray windows from saved preferences', () => {
    document.cookie = 'tiho-theme=removed-custom-theme; Path=/';
    const listener = vi.fn();
    const media = {
        matches: true,
        addEventListener: listener,
        removeEventListener: vi.fn(),
    } as unknown as MediaQueryList;
    const matchMedia = vi.spyOn(window, 'matchMedia').mockReturnValue(media);
    const view = render(
        <Preferences>
            <Example />
        </Preferences>,
    );
    expect(screen.getByTestId('theme')).toHaveTextContent('dark');
    expect(listener).toHaveBeenCalledWith('change', expect.any(Function));
    document.cookie = 'tiho-theme=light; Path=/';
    act(() => window.dispatchEvent(new Event('aigram-tray-refresh')));
    expect(screen.getByTestId('theme')).toHaveTextContent('light');
    fireEvent.click(screen.getByRole('button', {name: 'English'}));
    expect(screen.getByTestId('locale')).toHaveTextContent('en');
    expect(document.cookie).toContain('tiho-lang=en');
    view.unmount();
    expect(media.removeEventListener).toHaveBeenCalledWith('change', expect.any(Function));
    matchMedia.mockRestore();
});
