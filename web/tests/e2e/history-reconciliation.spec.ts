import {expect, test} from '@playwright/test';

import type {Message} from '@/shared/api/client';
import {workspaceState} from '../fixtures/workspace';

test('polling replaces a temporary Telegram message without hiding real repeated messages', async ({
    page,
}) => {
    const message = (id: string, text: string, outgoing: boolean): Message => ({
        id,
        chatId: '41',
        text,
        outgoing,
        date: 1792152000,
        kind: 'text',
    });
    const incoming = message('200', 'Привет', false);
    let history = [incoming, message('90071992547409920', 'Ответ', true)];
    await page.route('**/api/**', async (route) => {
        const path = new URL(route.request().url()).pathname;
        if (path === '/api/state') {
            await route.fulfill({json: workspaceState});
        } else if (path === '/api/chats/41/messages') {
            await route.fulfill({json: history});
        } else {
            await route.fulfill({status: 400, json: {error: 'Unexpected test request'}});
        }
    });
    await page.goto(process.env.TIHO_TEST_URL ?? 'http://127.0.0.1:3099');
    await page.getByRole('button', {name: 'Аня Смирнова', exact: true}).click();
    await expect(page.locator('[data-qa="message-outgoing"]')).toHaveCount(1);
    history = [incoming, message('300', 'Ответ', true)];
    await page.waitForResponse((response) => new URL(response.url()).pathname === '/api/chats/41/messages');
    await expect(page.locator('[data-qa="message-outgoing"]')).toHaveCount(1);
    history = [...history, message('400', 'Ответ', true)];
    await expect(page.locator('[data-qa="message-outgoing"]')).toHaveCount(2);
});
