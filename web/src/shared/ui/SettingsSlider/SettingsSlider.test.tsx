import {ThemeProvider} from '@gravity-ui/uikit';
import {fireEvent, render, screen} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {SettingsSlider} from '@/shared/ui/SettingsSlider';

it('labels the numeric control and preserves bounds, keyboard updates and its displayed unit', () => {
    const update = vi.fn();
    render(
        <ThemeProvider>
            <SettingsSlider
                label="Speech speed"
                value={1}
                displayValue="1.0×"
                min={0.5}
                max={2}
                step={0.1}
                onUpdate={update}
                hint="Changes playback speed."
            />
        </ThemeProvider>,
    );
    const slider = screen.getByRole('slider', {name: 'Speech speed'});
    expect(slider).toHaveAttribute('aria-valuemin', '0.5');
    expect(slider).toHaveAttribute('aria-valuemax', '2');
    expect(slider).toHaveAttribute('aria-valuenow', '1');
    expect(screen.getByText('1.0×')).toBeVisible();
    expect(screen.getByText('Changes playback speed.')).toBeVisible();
    expect(slider.closest('.g-slider')?.querySelector('.g-slider__top')).toHaveClass(
        'g-slider__top_tooltip-display_off',
    );
    fireEvent.keyDown(slider, {key: 'End', code: 'End', keyCode: 35, which: 35});
    expect(update).toHaveBeenCalledWith(2);
});

it('preserves a disabled field without accepting keyboard changes', () => {
    const update = vi.fn();
    render(
        <ThemeProvider>
            <SettingsSlider
                label="Reply probability"
                value={50}
                displayValue="50%"
                min={0}
                max={100}
                disabled
                onUpdate={update}
            />
        </ThemeProvider>,
    );
    const slider = screen.getByRole('slider', {name: 'Reply probability'});
    expect(slider).toHaveAttribute('aria-disabled', 'true');
    fireEvent.keyDown(slider, {key: 'End', code: 'End', keyCode: 35, which: 35});
    expect(update).not.toHaveBeenCalled();
});
