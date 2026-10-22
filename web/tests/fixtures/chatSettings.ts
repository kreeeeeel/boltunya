import type {Page} from '@playwright/test';
import ru from '../../src/shared/i18n/ru.json';

export async function chooseChatSettingsSection(page: Page, section: keyof typeof ru.chatSettings.sections) {
    const dialog = page.locator('[data-qa="communication-dialog"]');
    const item = dialog.getByRole('menuitem', {name: ru.chatSettings.sections[section], exact: true});
    if (await item.isVisible()) {
        await item.click();
    } else {
        await dialog.getByRole('combobox', {name: ru.chatSettings.navigation, exact: true}).click();
        await page.getByRole('option', {name: ru.chatSettings.sections[section], exact: true}).click();
    }
}
