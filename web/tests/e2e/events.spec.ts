import {expect, test} from '@playwright/test';
import {readFileSync} from 'node:fs';
import {join} from 'node:path';

import {workspaceState} from '../fixtures/workspace';
import {activityDetails, testActivity} from '../fixtures/activity';

test.beforeEach(async ({page}) => {
    const state = structuredClone(workspaceState);
    state.activity = structuredClone(testActivity);
    state.replyQueue = {paused: false, queued: 1, running: 1};
    state.settings.paused = false;
    await page.route('**/api/**', async (route) => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        const match = path.match(/^\/api\/activity\/([^/]+)(?:\/(cancel|priority))?$/);
        if (path === '/api/state') {
            await route.fulfill({json: state});
        } else if (path === '/api/activity/queue') {
            state.replyQueue!.paused = request.postDataJSON().paused;
            await route.fulfill({json: {ok: true}});
        } else if (path === '/api/activity') {
            state.activity = state.activity.filter((entry) =>
                ['queued', 'generating'].includes(entry.status),
            );
            await route.fulfill({json: {ok: true}});
        } else if (match) {
            const entry = state.activity.find((item) => item.id === match[1])!;
            if (match[2] === 'cancel') {
                entry.status = 'cancelled';
                entry.phase = 'cancelled';
                state.replyQueue!.queued = state.activity.filter((item) => item.status === 'queued').length;
                state.replyQueue!.running = state.activity.filter(
                    (item) => item.status === 'generating',
                ).length;
            } else if (match[2] === 'priority') {
                entry.priority = true;
                entry.queuePosition = 1;
            }
            await route.fulfill({json: match[2] ? {ok: true} : activityDetails(entry)});
        } else {
            await route.fulfill({json: {ok: true}});
        }
    });
    const launchFile = join(process.env.LOCALAPPDATA ?? '', 'Tiho', 'launch.url');
    await page.goto(process.env.TIHO_TEST_URL ?? readFileSync(launchFile, 'utf8'));
    await page.locator('[data-qa="nav-activity"]').click();
});

test('cards display a real queue, statuses and streamed thinking', async ({page}) => {
    await expect(page.getByRole('button', {name: 'История: Аня Смирнова'})).toHaveCSS('position', 'absolute');
    await expect(page.locator('[data-qa="empty-activity"]')).toHaveCount(0);
    await expect(page.locator('[data-qa="events-toolbar"]').getByText('В очереди: 1')).toBeVisible();
    await expect(page.locator('[data-qa="event-running"]').getByText('Аня Смирнова')).toBeVisible();
    await expect(page.getByText('Собеседник спрашивает о встрече.', {exact: false})).toBeVisible();
    await expect(page.getByRole('progressbar', {name: 'Генерация ответа'})).toBeVisible();
    await expect(page.locator('[data-qa="event-sent"]').getByText('Отправлено', {exact: true})).toBeVisible();
    const positions = await page
        .locator('[data-qa="events-grid"] article')
        .evaluateAll((cards) =>
            cards.map((card) => ({x: card.getBoundingClientRect().x, y: card.getBoundingClientRect().y})),
        );
    expect(positions[0].y).toBe(positions[1].y);
    expect(positions[1].y).toBe(positions[2].y);
    expect(positions[1].x).toBeGreaterThan(positions[0].x);
    expect(positions[3].y).toBeGreaterThan(positions[0].y);
    await page.screenshot({path: '../docs/resources/activity-queue.png', animations: 'disabled'});
});

test('event grid adapts to two columns and then a single column without horizontal overflow', async ({
    page,
}) => {
    for (const [width, columns] of [
        [1000, 2],
        [640, 1],
    ]) {
        await page.setViewportSize({width, height: 900});
        const cards = page.locator('[data-qa="events-grid"] article');
        await expect
            .poll(async () =>
                cards.evaluateAll(
                    (items) => new Set(items.map((item) => item.getBoundingClientRect().x)).size,
                ),
            )
            .toBe(columns);
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
            true,
        );
    }
});

test('queue pause, priority, cancellation and clear use separate API actions', async ({page}) => {
    await page.getByRole('button', {name: 'Пауза', exact: true}).click();
    await expect(page.getByText('Очередь на паузе')).toBeVisible();
    await page.getByRole('button', {name: 'Повысить приоритет: Максим Волков'}).click();
    await expect(
        page
            .locator('[data-qa="event-queued"]')
            .getByRole('button', {name: 'Повысить приоритет: Максим Волков'}),
    ).toBeDisabled();
    await page.getByRole('button', {name: 'Очистить завершённые события'}).click();
    await expect(page.locator('article')).toHaveCount(2);
    await page.getByRole('button', {name: 'Остановить: Аня Смирнова'}).click();
    await expect(
        page.locator('[data-qa="event-running"]').getByText('Остановлено', {exact: true}),
    ).toBeVisible();
    await page.getByRole('button', {name: 'Остановить: Максим Волков'}).click();
    await page.getByRole('button', {name: 'Очистить завершённые события'}).click();
    await expect(page.locator('[data-qa="empty-activity"]')).toBeVisible();
    await expect(page.locator('[data-qa="events-toolbar"]')).toHaveCount(0);
});

test('history modal displays exact prompt, model output, thinking and usage', async ({page}) => {
    await page.getByRole('button', {name: 'История: Лиза'}).click();
    const dialog = page.getByRole('dialog', {name: 'История ответа'});
    await expect(dialog.getByText('412', {exact: true})).toBeVisible();
    await expect(dialog.getByText('28', {exact: true})).toBeVisible();
    const requestBlock = dialog.locator('[data-qa="event-request-1"]');
    const thinkingBlock = dialog.locator('[data-qa="event-thinking-1"]');
    const responseBlock = dialog.locator('[data-qa="event-response-1"]');
    await expect(requestBlock).toHaveCSS('border-radius', '14px');
    await expect(thinkingBlock).toHaveCSS('border-radius', '14px');
    await expect(responseBlock).toHaveCSS('border-radius', '14px');
    await expect
        .poll(() =>
            requestBlock.evaluate((element) => {
                const next = element.nextElementSibling!;
                return next.getBoundingClientRect().top - element.getBoundingClientRect().bottom;
            }),
        )
        .toBeGreaterThanOrEqual(15);
    await expect(requestBlock.locator('[data-qa="event-thinking-1"]')).toHaveCount(0);
    await dialog.getByRole('button', {name: 'Контекст · сообщений: 2'}).click();
    await dialog.locator('pre').first().scrollIntoViewIfNeeded();
    await expect(
        dialog.getByText('Отвечай коротко, учитывай историю разговора.', {exact: true}),
    ).toBeVisible();
    await expect(
        dialog.getByText('Уточню время встречи и спрошу, удобно ли собеседнику.', {exact: true}),
    ).toBeVisible();
    await dialog.getByRole('button', {name: 'Контекст · сообщений: 2'}).click();
    await expect(
        dialog.getByText('Отвечай коротко, учитывай историю разговора.', {exact: true}),
    ).toBeHidden();
    await responseBlock.scrollIntoViewIfNeeded();
    const colors = await dialog
        .locator('[data-qa="event-request-1"], [data-qa="event-thinking-1"], [data-qa="event-response-1"]')
        .evaluateAll((blocks) => blocks.map((block) => getComputedStyle(block).backgroundColor));
    expect(new Set(colors).size).toBe(3);
    expect(colors.every((color) => color !== 'rgba(0, 0, 0, 0)')).toBe(true);
    await page.screenshot({path: '../docs/resources/activity-details.png', animations: 'disabled'});
    await dialog.getByRole('button', {name: 'Закрыть', exact: true}).click();
    await expect(dialog).toHaveCount(0);
});

test('failed detail request shows an error and modal remains dismissible', async ({page}) => {
    await page.route('**/api/activity/sent', (route) =>
        route.fulfill({status: 404, json: {message: 'История уже очищена'}}),
    );
    await page.getByRole('button', {name: 'История: Лиза'}).click();
    await expect(page.getByRole('dialog').getByRole('alert')).toHaveText('История уже очищена');
    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog')).toHaveCount(0);
});

test('dark events use the app palette and readable card sizes', async ({page}) => {
    await page.context().addCookies([{name: 'tiho-theme', value: 'dark', url: page.url()}]);
    await page.reload();
    await page.locator('[data-qa="nav-activity"]').click();
    await expect(page.locator('[data-qa="event-running"]')).toHaveCSS('background-color', 'rgb(36, 42, 52)');
    await page.screenshot({path: '../docs/resources/activity-dark.png', animations: 'disabled'});
});
