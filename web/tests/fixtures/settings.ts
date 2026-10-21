import {expect} from '@playwright/test';
import type {Page} from '@playwright/test';
import ru from '../../src/shared/i18n/ru.json';
export async function chooseSettingsSection(page: Page, section: keyof typeof ru.settingsCenter.sections) {
    await page
        .getByRole('dialog', {name: ru.settings, exact: true})
        .getByRole('menuitem', {name: ru.settingsCenter.sections[section], exact: true})
        .click();
}
export async function toggleTheme(page: Page) {
    const dark = !(await page.locator('body').getAttribute('class'))?.includes('g-root_theme_dark');
    await page.getByRole('button', {name: 'Сменить тему', exact: true}).click();
    await page.getByRole('combobox', {name: ru.appearance.mode, exact: true}).click();
    await page
        .getByRole('option', {name: dark ? ru.appearance.modes.dark : ru.appearance.modes.light, exact: true})
        .click();
    await page.getByRole('button', {name: ru.save, exact: true}).click();
    await expect(page.getByRole('button', {name: ru.saved, exact: true})).toBeVisible();
    await page
        .getByRole('dialog', {name: ru.settings, exact: true})
        .getByRole('button', {name: ru.close, exact: true})
        .click();
}
export async function persistTestTheme(page: Page, dark: boolean) {
    await page.evaluate(
        async (mode) => {
            const response = await fetch('/api/appearance', {
                method: 'PUT',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({mode, tokens: {}, css: ''}),
            });
            if (!response.ok) {
                throw new Error('Theme save failed');
            }
        },
        dark ? 'dark' : 'light',
    );
}
