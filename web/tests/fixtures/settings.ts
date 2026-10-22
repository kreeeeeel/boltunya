import {expect} from '@playwright/test';
import type {BrowserContext, Locator, Page} from '@playwright/test';
import ru from '../../src/shared/i18n/ru.json';

export async function initializeTestTheme(surface: Pick<BrowserContext, 'addInitScript'>, dark: boolean) {
    await surface.addInitScript(
        (mode) => {
            const saved = document.cookie
                .split(';')
                .some((cookie) => cookie.trim().startsWith('tiho-theme='));
            if (!saved) {
                document.cookie = `tiho-theme=${mode}; Path=/; SameSite=Strict; Max-Age=31536000`;
            }
        },
        dark ? 'dark' : 'light',
    );
}

export async function chooseSettingsSection(page: Page, section: keyof typeof ru.settingsCenter.sections) {
    await page
        .getByRole('dialog', {name: ru.settings, exact: true})
        .getByRole('menuitem', {name: ru.settingsCenter.sections[section], exact: true})
        .click();
}
export async function toggleTheme(page: Page) {
    const dark = !(await page.locator('body').getAttribute('class'))?.includes('g-root_theme_dark');
    await page.getByRole('button', {name: ru.theme, exact: true}).click();
    await expect(page.locator('body')).toContainClass('g-root_theme_' + (dark ? 'dark' : 'light'));
}
export async function persistTestTheme(page: Page, dark: boolean) {
    await page.evaluate(
        (mode) => {
            document.cookie = `tiho-theme=${mode}; Path=/; SameSite=Strict; Max-Age=31536000`;
            window.dispatchEvent(new Event('aigram-tray-refresh'));
        },
        dark ? 'dark' : 'light',
    );
}

export async function expectSettingsSlider(slider: Locator) {
    await expect(slider).toBeVisible();
    const layout = await slider.evaluate((element) => {
        const style = getComputedStyle(element);
        const heading = element.children[0].getBoundingClientRect();
        const control = element.children[1].getBoundingClientRect();
        const label = element.children[0].children[0].getBoundingClientRect();
        const value = element.children[0].children[1].getBoundingClientRect();
        return {
            gap: Number.parseFloat(style.rowGap),
            radius: Number.parseFloat(style.borderRadius),
            background: style.backgroundColor,
            fullWidth: Math.abs(control.width - heading.width) < 1,
            labelLeft: Math.abs(label.left - heading.left) < 1,
            valueRight: Math.abs(value.right - heading.right) < 1,
            controlBelowHeading: Math.abs(control.top - heading.bottom - 3) < 1,
            compactTrack: !element.querySelector('.g-slider__top:not(.g-slider__top_tooltip-display_off)'),
            noOverflow: element.scrollWidth <= element.clientWidth,
        };
    });
    expect(layout).toMatchObject({
        gap: 3,
        radius: 12,
        fullWidth: true,
        labelLeft: true,
        valueRight: true,
        controlBelowHeading: true,
        compactTrack: true,
        noOverflow: true,
    });
    expect(layout.background).not.toBe('rgba(0, 0, 0, 0)');
}
