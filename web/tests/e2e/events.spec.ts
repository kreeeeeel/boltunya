import {initializeTestTheme, persistTestTheme} from '../fixtures/settings';
import {expect, test} from '@playwright/test';
import {readFileSync} from 'node:fs';
import {join} from 'node:path';
import {workspaceState} from '../fixtures/workspace';
import {activityDetails, testActivity} from '../fixtures/activity';
import type * as Contract from '@aigram/api-client';
import {historyPage, historyStorage} from '../fixtures/activityHistory';
import ru from '../../src/shared/i18n/ru.json';
test.beforeEach(async ({page}) => {
    await initializeTestTheme(page, false);
    const state = structuredClone(workspaceState);
    state.activity = structuredClone(testActivity);
    state.replyQueue = {paused: false, queued: 1, running: 1};
    state.settings.paused = false;
    const feedbacks = new Map<string, Contract.ActivityFeedback>();
    await page.route('**/api/**', async (route) => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        const parts = path.split('/');
        const item = parts[1] === 'api' && parts[2] === 'activity' && Boolean(parts[3]);
        const match =
            item && (parts.length === 4 || (parts.length === 5 && ['cancel', 'priority'].includes(parts[4])))
                ? [path, parts[3], parts[4]]
                : null;
        const feedbackMatch = item && parts.length === 5 && parts[4] === 'feedback' ? [path, parts[3]] : null;
        if (path === '/api/activity/history') {
            await route.fulfill({json: historyPage(state.activity)});
        } else if (path === '/api/activity/history/settings') {
            await route.fulfill({
                json: {...historyStorage, ...(request.method() === 'PUT' ? request.postDataJSON() : {})},
            });
        } else if (path === '/api/model-profiles') {
            await route.fulfill({json: {profiles: [], presets: []}});
        } else if (path === '/api/speech') {
            await route.fulfill({json: {assets: [], downloads: []}});
        } else if (path === '/api/settings') {
            Object.assign(state.settings, request.postDataJSON());
            if (state.replyQueue) {
                state.replyQueue.maxParallelTasks = state.settings.maxParallelTasks;
            }
            await route.fulfill({json: state.settings});
        } else if (path === '/api/state') {
            await route.fulfill({json: state});
        } else if (path === '/api/activity/queue') {
            state.replyQueue!.paused = request.postDataJSON().paused;
            await route.fulfill({json: {ok: true}});
        } else if (path === '/api/activity') {
            state.activity = state.activity.filter((entry) =>
                ['queued', 'generating'].includes(entry.status),
            );
            await route.fulfill({json: {ok: true}});
        } else if (feedbackMatch) {
            const id = feedbackMatch[1];
            const entry = state.activity.find((item) => item.id === id)!;
            if (request.method() === 'DELETE') {
                feedbacks.delete(id);
                entry.feedbackRating = null;
                await route.fulfill({json: {ok: true}});
            } else {
                const body = request.postDataJSON();
                const feedback = {
                    rating: body.rating,
                    comment: body.comment ?? feedbacks.get(id)?.comment ?? '',
                    updatedAt: Date.now(),
                };
                feedbacks.set(id, feedback);
                entry.feedbackRating = body.rating;
                await route.fulfill({json: feedback});
            }
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
            await route.fulfill({
                json: match[2]
                    ? {ok: true}
                    : {...activityDetails(entry), feedback: feedbacks.get(entry.id) ?? null},
            });
        } else {
            await route.fulfill({json: {ok: true}});
        }
    });
    const launchFile = join(process.env.LOCALAPPDATA ?? '', 'Tiho', 'launch.url');
    await page.goto(process.env.TIHO_TEST_URL ?? readFileSync(launchFile, 'utf8'));
    await page.locator('[data-qa="nav-activity"]').click();
});
test('finished reply ratings support comments, edits and removal across card and details', async ({page}) => {
    const card = page.locator('[data-qa="event-sent"]');
    await card.getByRole('button', {name: 'Хорошо', exact: true}).click();
    await expect(card.getByRole('button', {name: 'Хорошо', exact: true})).toHaveAttribute(
        'aria-pressed',
        'true',
    );
    await card.getByRole('button', {name: 'Оценить с комментарием', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'История ответа'});
    const editor = dialog.locator('[data-qa="activity-feedback-editor"]');
    await editor.getByRole('button', {name: 'Плохо', exact: true}).click();
    await editor.getByRole('textbox', {name: 'Комментарий к ответу'}).fill('Слишком формально, лучше проще.');
    const save = page.waitForRequest(
        (request) => request.method() === 'PUT' && request.url().endsWith('/api/activity/sent/feedback'),
    );
    await editor.getByRole('button', {name: 'Сохранить оценку', exact: true}).click();
    expect((await save).postDataJSON()).toEqual({rating: 'bad', comment: 'Слишком формально, лучше проще.'});
    await expect(editor.getByRole('button', {name: 'Оценка сохранена', exact: true})).toBeVisible();
    await page.screenshot({path: '../docs/resources/reply-feedback.png', animations: 'disabled'});
    await page.keyboard.press('Escape');
    await expect(card.getByRole('button', {name: 'Плохо', exact: true})).toHaveAttribute(
        'aria-pressed',
        'true',
    );
    await card.getByRole('button', {name: 'Оценить с комментарием', exact: true}).click();
    await expect(editor.getByRole('textbox', {name: 'Комментарий к ответу'})).toHaveValue(
        'Слишком формально, лучше проще.',
    );
    await editor.getByRole('button', {name: 'Снять оценку', exact: true}).click();
    await expect(editor.getByRole('button', {name: 'Снять оценку', exact: true})).toHaveCount(0);
    await page.keyboard.press('Escape');
    await expect(card.getByRole('button', {name: 'Плохо', exact: true})).toHaveAttribute(
        'aria-pressed',
        'false',
    );
    await expect(page.locator('[data-qa="event-running"] [data-qa="activity-rating"]')).toHaveCount(0);
    await expect(page.locator('[data-qa="event-error"] [data-qa="activity-rating"]')).toHaveCount(0);
});
test('failed feedback saves keep the comment draft and can be retried', async ({page}) => {
    let fail = true;
    await page.route('**/api/activity/sent/feedback', async (route) => {
        if (fail) {
            await route.fulfill({status: 503, json: {message: 'Не удалось сохранить оценку.'}});
        } else {
            await route.fallback();
        }
    });
    await page
        .locator('[data-qa="event-sent"]')
        .getByRole('button', {name: 'Оценить с комментарием', exact: true})
        .click();
    const editor = page.locator('[data-qa="activity-feedback-editor"]');
    await editor.getByRole('button', {name: 'Плохо', exact: true}).click();
    const comment = editor.getByRole('textbox', {name: 'Комментарий к ответу'});
    await comment.fill('Не учтена договорённость.');
    await editor.getByRole('button', {name: 'Сохранить оценку', exact: true}).click();
    await expect(editor.getByRole('alert')).toHaveText('Не удалось сохранить оценку.');
    await expect(comment).toHaveValue('Не учтена договорённость.');
    fail = false;
    await editor.getByRole('button', {name: 'Сохранить оценку', exact: true}).click();
    await expect(editor.getByRole('button', {name: 'Оценка сохранена', exact: true})).toBeVisible();
    await expect(editor.getByRole('alert')).toHaveCount(0);
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
test('a generated reply with failed delivery is not labeled as sent to Telegram', async ({page}) => {
    const details = activityDetails(testActivity[3]);
    details.response = 'Готовый ответ, который не был отправлен.';
    await page.route('**/api/activity/error', (route) => route.fulfill({json: details}));
    await page.getByRole('button', {name: 'История: Команда проекта'}).click();
    const dialog = page.getByRole('dialog', {name: 'История ответа'});
    await expect(dialog.getByText('Готовый ответ', {exact: true})).toBeVisible();
    await expect(dialog.getByText('Отправлено в Telegram', {exact: true})).toHaveCount(0);
    await expect(dialog.getByText(details.response, {exact: true})).toBeVisible();
});
test('dark events use the app palette and readable card sizes', async ({page}) => {
    await persistTestTheme(page, true);
    await page.reload();
    await page.locator('[data-qa="nav-activity"]').click();
    await expect(page.locator('[data-qa="event-running"]')).toHaveCSS('background-color', 'rgb(36, 42, 52)');
    await page.screenshot({path: '../docs/resources/activity-dark.png', animations: 'disabled'});
});
test('parallel queue settings persist without fabricated resource forecasts', async ({page}) => {
    let metricRequests = 0;
    page.on('request', (request) => {
        if (new URL(request.url()).pathname === '/api/metrics') {
            metricRequests += 1;
        }
    });
    await page.getByRole('button', {name: 'Параллельные задачи', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Параллельные задачи', exact: true});
    await dialog.getByRole('radio', {name: '4', exact: true}).check();
    await expect(dialog.getByText('Очень высокая', {exact: true})).toBeVisible();
    await expect(dialog.getByText(ru.events.parallel.estimateHint, {exact: true})).toBeVisible();
    await expect(dialog.getByRole('progressbar')).toHaveCount(0);
    expect(metricRequests).toBe(0);
    const saved = page.waitForRequest(
        (request) => request.method() === 'PATCH' && request.url().endsWith('/api/settings'),
    );
    await dialog.getByRole('button', {name: 'Сохранить настройки', exact: true}).click();
    expect((await saved).postDataJSON()).toEqual({maxParallelTasks: 4});
    await expect(dialog.getByRole('button', {name: 'Сохранено', exact: true})).toBeVisible();
    await page.screenshot({path: '../docs/resources/parallel-tasks.png', animations: 'disabled'});
    await dialog.getByRole('button', {name: 'Закрыть', exact: true}).click();
    await page.getByRole('button', {name: 'Параллельные задачи', exact: true}).click();
    await expect(page.getByRole('radio', {name: '4', exact: true})).toBeChecked();
});
for (const dark of [false, true]) {
    test(`completed memory and initiative tags stay readable in ${dark ? 'dark' : 'light'} theme`, async ({
        page,
    }) => {
        const state = structuredClone(workspaceState);
        const finishedAt = Date.UTC(2026, 9, 21, 10, 24);
        const memory = {
            ...testActivity[2],
            id: 'memory-ready',
            kind: 'memory' as const,
            status: 'ready' as const,
            phase: 'ready',
            title: 'Общая память',
            finishedAt,
            feedbackAvailable: false,
            thinking: '',
            preview: '',
        };
        state.activity = [memory, {...testActivity[2], id: 'initiative', kind: 'initiative'}];
        state.settings.paused = false;
        state.engine.warming = state.settings.defaultModel;
        await page.route('**/api/state', (route) => route.fulfill({json: state}));
        await page.route('**/api/activity/history?**', (route) =>
            route.fulfill({json: historyPage(state.activity)}),
        );
        if (dark) {
            await persistTestTheme(page, true);
        }
        await page.reload();
        await page.locator('[data-qa="nav-activity"]').click();
        const card = page.locator('[data-qa="event-memory-ready"]');
        const result = card.locator('[data-qa="reply-process"]');
        const identity = card.locator('[data-qa="event-identity"]');
        const tags = card.locator('[data-qa="event-tags"]');
        expect((await identity.boundingBox())!.y + (await identity.boundingBox())!.height).toBeLessThan(
            (await tags.boundingBox())!.y,
        );
        expect(
            await identity
                .getByText('Общая память', {exact: true})
                .evaluate((title) => title.scrollWidth <= title.clientWidth),
        ).toBe(true);
        await expect(result).toContainText(ru.events.memoryReady);
        const completedTime = await page.evaluate(
            (date) =>
                new Date(date).toLocaleTimeString('ru', {
                    hour: '2-digit',
                    minute: '2-digit',
                }),
            finishedAt,
        );
        await expect(result).toContainText(completedTime);
        await expect(result.getByRole('progressbar')).toHaveCount(0);
        await expect(page.locator('[data-qa="window-bar"]')).toContainText(ru.aiControl.warming);
        const heights = await page
            .locator('[data-qa="events-grid"] .g-label')
            .evaluateAll((tags) => tags.map((tag) => tag.getBoundingClientRect().height));
        expect(heights.length).toBeGreaterThanOrEqual(4);
        expect(new Set(heights).size).toBe(1);
        await page.screenshot({
            path: `../docs/resources/activity-memory-${dark ? 'dark' : 'light'}.png`,
            animations: 'disabled',
        });
    });
    test(`persisted history loads older pages without duplicating live cards in ${dark ? 'dark' : 'light'} theme`, async ({
        page,
    }) => {
        const archived = {
            ...testActivity[2],
            id: 'archived',
            title: 'Архивный собеседник',
            date: 1700000000,
            preview: 'Ответ из сохранённой истории',
        };
        await page.route('**/api/activity/history?**', (route) => {
            const cursor = new URL(route.request().url()).searchParams.get('cursor');
            return route.fulfill({
                json: cursor
                    ? historyPage([archived])
                    : {
                          ...historyPage([testActivity[2]]),
                          hasMore: true,
                          nextCursor: 'opaque-page-2',
                          total: 2,
                      },
            });
        });
        await page.route('**/api/activity/archived', (route) =>
            route.fulfill({json: activityDetails(archived)}),
        );
        if (dark) {
            await persistTestTheme(page, true);
        }
        await page.reload();
        await page.locator('[data-qa="nav-activity"]').click();
        await expect(page.locator('[data-qa="event-sent"]')).toHaveCount(1);
        await page.getByRole('button', {name: 'Показать более ранние события', exact: true}).click();
        const card = page.locator('[data-qa="event-archived"]');
        await expect(card).toContainText('Ответ из сохранённой истории');
        await expect(page.locator('[data-qa="event-sent"]')).toHaveCount(1);
        await card.hover();
        expect(
            await card
                .getByRole('button', {name: 'История: Архивный собеседник', exact: true})
                .evaluate((element) => getComputedStyle(element, '::before').borderRadius),
        ).toBe('20px');
        await page.screenshot({
            path: `../docs/resources/history-${dark ? 'dark' : 'light'}.png`,
            animations: 'disabled',
        });
        await page.getByRole('button', {name: 'Хранение истории', exact: true}).click();
        const dialog = page.getByRole('dialog', {name: 'Хранение истории', exact: true});
        await expect(dialog.getByRole('spinbutton', {name: 'Срок хранения, дней', exact: true})).toHaveValue(
            '0',
        );
        await expect(dialog.getByRole('spinbutton', {name: 'Лимит места, МБ', exact: true})).toHaveValue('0');
        await dialog.getByRole('spinbutton', {name: 'Лимит места, МБ', exact: true}).fill('1024');
        const saved = page.waitForRequest(
            (request) =>
                new URL(request.url()).pathname === '/api/activity/history/settings' &&
                request.method() === 'PUT',
        );
        await dialog.getByRole('button', {name: 'Сохранить настройки', exact: true}).click();
        expect((await saved).postDataJSON()).toEqual({retentionDays: 0, maxStorageMb: 1024});
        await expect(dialog.getByRole('button', {name: 'Сохранено', exact: true})).toBeVisible();
    });
}
