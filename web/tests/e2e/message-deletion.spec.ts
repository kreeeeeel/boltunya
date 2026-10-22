import {initializeTestTheme} from '../fixtures/settings';
import {expect, test} from '@playwright/test';
import {readFileSync} from 'node:fs';
import {join} from 'node:path';
import {workspaceState} from '../fixtures/workspace';
import type {Message} from '../../src/shared/api/client';
import ru from '../../src/shared/i18n/ru.json';

for (const dark of [false, true]) {
    test(`deletion confirms an explicit scope and retains compact badges and scroll position in ${dark ? 'dark' : 'light'} theme`, async ({
        page,
    }) => {
        await initializeTestTheme(page, dark);
        const state = structuredClone(workspaceState);
        state.chats = [state.chats[0]];
        const history: Message[] = Array.from({length: 40}, (_, index) => ({
            id: String(index + 1),
            chatId: '41',
            text: 'Synthetic retained message ' + (index + 1),
            kind: 'text',
            outgoing: true,
            date: 1792064000 + index,
            automated: index === 19,
            contextExcluded: index === 19,
        }));
        const mutations: unknown[] = [];
        let historyRequests = 0;
        await page.route('**/api/**', async (route) => {
            const request = route.request();
            const path = new URL(request.url()).pathname;
            if (path === '/api/state') {
                await route.fulfill({json: state});
            } else if (path === '/api/model-profiles') {
                await route.fulfill({json: {profiles: [], presets: []}});
            } else if (path === '/api/chats/41/messages') {
                historyRequests++;
                // Deliberately stale after mutation, reproducing an in-flight history response.
                await route.fulfill({json: history});
            } else if (path === '/api/chats/41/messages/20/deletion') {
                await route.fulfill({json: {canDeleteForSelf: true, canDeleteForEveryone: true}});
            } else if (path === '/api/chats/41/messages/20' && request.method() === 'DELETE') {
                mutations.push(request.postDataJSON());
                await route.fulfill({json: {chatId: '41', messageId: '20', deletedAt: 1792065000}});
            } else {
                await route.fulfill({status: 404, json: {message: 'Unexpected synthetic request'}});
            }
        });
        const launch =
            process.env.TIHO_TEST_URL ??
            readFileSync(
                process.env.TIHO_LAUNCH_FILE ?? join(process.env.LOCALAPPDATA ?? '', 'Tiho', 'launch.url'),
                'utf8',
            );
        await page.goto(launch);
        await page.getByRole('button', {name: state.chats[0].title, exact: true}).click();
        const row = page.locator('[data-message-row-id="20"]');
        await row.scrollIntoViewIfNeeded();
        const excluded = row.locator('[data-qa="message-context-excluded"]');
        const ai = row.locator('[data-qa="message-ai"]');
        const excludedBounds = (await excluded.boundingBox())!;
        const aiBounds = (await ai.boundingBox())!;
        expect(excludedBounds.height).toBe(aiBounds.height);
        expect(Math.abs(excludedBounds.y - aiBounds.y)).toBeLessThan(1);
        expect(excludedBounds.x + excludedBounds.width).toBeLessThanOrEqual(aiBounds.x);
        expect(aiBounds.x - excludedBounds.x - excludedBounds.width).toBeLessThanOrEqual(8);
        const viewport = page.locator('[data-qa="conversation-messages"]');
        const scrollTop = await viewport.evaluate((element) => element.scrollTop);
        await row.click({button: 'right'});
        await page.getByRole('menuitem', {name: ru.messageMenu.delete, exact: true}).click();
        const dialog = page.getByRole('dialog', {name: ru.messageDelete.title, exact: true});
        await expect(dialog.getByRole('radio', {name: ru.messageDelete.self, exact: true})).toBeChecked();
        expect(mutations).toHaveLength(0);
        await dialog.getByRole('button', {name: ru.cancel, exact: true}).click();
        await expect(dialog).not.toBeVisible();
        expect(mutations).toHaveLength(0);
        await row.click({button: 'right'});
        await page.getByRole('menuitem', {name: ru.messageMenu.delete, exact: true}).click();
        await dialog.getByRole('radio', {name: ru.messageDelete.everyone, exact: true}).check();
        await page.screenshot({
            path: `../docs/resources/message-deletion-${dark ? 'dark' : 'light'}.png`,
            animations: 'disabled',
        });
        await dialog.getByRole('button', {name: ru.messageDelete.confirmEveryone, exact: true}).click();
        await expect(dialog).not.toBeVisible();
        expect(mutations).toEqual([{scope: 'everyone'}]);
        await expect(row.locator('[data-message-id="20"]')).toHaveAttribute('data-message-deleted', 'true');
        await expect(row.getByText(history[19].text, {exact: true})).toBeVisible();
        expect(Math.abs((await viewport.evaluate((element) => element.scrollTop)) - scrollTop)).toBeLessThan(
            1,
        );
        const previousRequests = historyRequests;
        await expect.poll(() => historyRequests).toBeGreaterThan(previousRequests);
        await expect(row.locator('[data-message-id="20"]')).toHaveAttribute('data-message-deleted', 'true');
    });
}
