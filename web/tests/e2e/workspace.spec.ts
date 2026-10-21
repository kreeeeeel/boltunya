import {chooseSettingsSection, toggleTheme, persistTestTheme} from '../fixtures/settings';
import {expect, test} from '@playwright/test';
import {readFileSync} from 'node:fs';
import {join} from 'node:path';
import {workspaceState} from '../fixtures/workspace';
import {testHistory} from '../fixtures/chats';
import {testActivity} from '../fixtures/activity';
import type {Message} from '@/shared/api/client';
import {computeDevices} from '../fixtures/computeDevices';
import {historyPage} from '../fixtures/activityHistory';
import {syntheticPromptBudget} from '../fixtures/promptBudget';
import ru from '../../src/shared/i18n/ru.json';

const launchFile = process.env.TIHO_LAUNCH_FILE ?? join(process.env.LOCALAPPDATA ?? '', 'Tiho', 'launch.url');

test.beforeEach(async ({page}) => {
    const state = structuredClone(workspaceState);
    const histories = new Map<string, Message[]>();
    let appearance = {mode: 'system', tokens: {}, css: ''};
    let searchConnection = {configured: false, state: 'unchecked'};
    await page.route('**/api/**', async (route) => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        const parts = path.split('/');
        const match =
            parts.length === 5 &&
            parts[1] === 'api' &&
            parts[2] === 'chats' &&
            parts[3] &&
            ['messages', 'rule'].includes(parts[4])
                ? [path, parts[3], parts[4]]
                : null;
        if (path === '/api/appearance') {
            if (request.method() === 'PUT') appearance = request.postDataJSON();
            await route.fulfill({json: appearance});
        } else if (path === '/api/activity/history') {
            await route.fulfill({json: historyPage(state.activity)});
        } else if (path === '/api/prompts/budget') {
            await route.fulfill({json: syntheticPromptBudget(request.postDataJSON())});
        } else if (path === '/api/model-prompts') {
            await route.fulfill({json: request.method() === 'PUT' ? request.postDataJSON() : {prompts: []}});
        } else if (path === '/api/model-profiles') {
            await route.fulfill({json: {profiles: [], presets: []}});
        } else if (path === '/api/state') {
            await route.fulfill({json: state});
        } else if (path === '/api/search/connection') {
            if (request.method() === 'POST') {
                searchConnection = {configured: true, state: 'unchecked'};
            } else if (request.method() === 'DELETE') {
                searchConnection = {configured: false, state: 'unchecked'};
            }
            await route.fulfill({json: searchConnection});
        } else if (path === '/api/search/check') {
            searchConnection = {...searchConnection, state: 'limited'};
            await route.fulfill({json: searchConnection});
        } else if (path === '/api/storage/locations') {
            await route.fulfill({json: {locations: []}});
        } else if (path === '/api/engine/devices') {
            await route.fulfill({
                json: {
                    ...computeDevices,
                    managed: false,
                    active: null,
                    selectionSupported: false,
                    limitation: 'external',
                },
            });
        } else if (path === '/api/cache') {
            await route.fulfill({
                json: {
                    totalBytes: 0,
                    fileCount: 0,
                    photoBytes: 0,
                    videoBytes: 0,
                    audioBytes: 0,
                    documentBytes: 0,
                },
            });
        } else if (path.startsWith('/api/memory')) {
            await route.fulfill({
                json: {
                    chatId: '',
                    notes: '',
                    summary: '',
                    revision: '1',
                    updatedAt: 0,
                    processedMessages: 0,
                    backfillComplete: true,
                    status: 'ready',
                    error: '',
                    trackedChats: 0,
                    relativePath: '',
                    collection: {
                        active: false,
                        status: 'idle',
                        queuedChats: 0,
                        queuePosition: 0,
                        processedMessages: 0,
                    },
                },
            });
        } else if (match) {
            const [, id, resource] = match;
            const history = histories.get(id) ?? testHistory(id);
            histories.set(id, history);
            if (request.method() === 'POST') {
                history.push({
                    id: String(Date.now()),
                    chatId: id,
                    text: request.postDataJSON().text,
                    outgoing: true,
                    date: Date.now() / 1000,
                    kind: 'text',
                });
            } else if (resource === 'rule') {
                const chat = state.chats.find((item) => item.id === id);
                if (chat) {
                    chat.rule = request.postDataJSON();
                }
            }
            const before = new URL(request.url()).searchParams.get('before');
            const page = before ? history.filter((message) => Number(message.id) < Number(before)) : history;
            await route.fulfill({json: request.method() === 'GET' ? page : {ok: true}});
        } else if (path === '/api/settings') {
            Object.assign(state.settings, request.postDataJSON());
            await route.fulfill({json: {ok: true}});
        } else {
            await route.fulfill({status: 400, json: {error: 'Unexpected test request'}});
        }
    });
    await page.goto(process.env.TIHO_TEST_URL ?? readFileSync(launchFile, 'utf8'));
    await page.getByRole('button', {name: 'Аня Смирнова', exact: true}).click();
    await expect(page.locator('[data-qa="message-incoming"]').first()).toBeVisible();
});

test('chat selection, sending, filtering and saving a rule use the real API flow', async ({page}) => {
    const errors: string[] = [];
    page.on('pageerror', (error) => errors.push(error.message));
    for (const text of [
        'Демо',
        'Ваш Telegram. На вашей волне',
        'Telegram подключён',
        'Enter — отправить',
        'Repli · 1.0',
        'Всё на вашем компьютере',
        'ИИ на паузе',
    ]) {
        await expect(page.getByText(text, {exact: false})).toHaveCount(0);
    }
    await page.getByRole('textbox', {name: 'Написать сообщение…'}).fill('Проверка отправки');
    const sent = page.waitForRequest(
        (request) => request.method() === 'POST' && request.url().endsWith('/api/chats/41/messages'),
    );
    await page.getByRole('button', {name: 'Отправить сообщение'}).click();
    expect((await sent).postDataJSON()).toEqual({text: 'Проверка отправки'});
    await expect(
        page.locator('[data-qa="message-outgoing"]').getByText('Проверка отправки', {exact: true}),
    ).toBeVisible();
    await expect(page.getByRole('textbox', {name: 'Написать сообщение…'})).toHaveValue('');
    await page.getByRole('button', {name: 'Настроить общение', exact: true}).click();
    await expect(page.getByText('Глубина контекста', {exact: true})).toBeVisible();
    const internet = page.getByRole('switch', {name: 'Поиск в интернете', exact: true});
    await expect(internet).toBeChecked();
    await internet.click();
    await page.getByRole('combobox', {name: 'Модель ИИ', exact: true}).click();
    await page.getByRole('option', {name: 'Gemma 3 · 4B'}).click();
    const saved = page.waitForRequest(
        (request) => request.method() === 'PUT' && request.url().endsWith('/api/chats/41/rule'),
    );
    await page.getByRole('button', {name: 'Сохранить настройки'}).click();
    const savedRule = (await saved).postDataJSON();
    expect(savedRule.contextMessages).toBeGreaterThan(0);
    expect(savedRule.model).toBe('gemma3:4b');
    expect(savedRule.useDefaultModel).toBe(false);
    expect(savedRule.internetAccess).toBe(false);
    await expect(page.getByRole('dialog')).toBeVisible();
    await expect(page.getByRole('status')).toHaveText('Сохранено');
    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await page.getByRole('textbox', {name: 'Поиск по диалогам'}).fill('Марк');
    await expect(page.getByRole('button', {name: 'Лиза', exact: true})).toHaveCount(0);
    await page.getByRole('textbox', {name: 'Поиск по диалогам'}).fill('');
    await page.evaluate(() => document.fonts.ready);
    await page.screenshot({animations: 'disabled', path: '../docs/resources/desktop.png'});
    expect(errors).toEqual([]);
});

test('conversation generation shares event phases and thinking and can be stopped in the chat', async ({
    page,
}) => {
    const state = structuredClone(workspaceState);
    state.activity = structuredClone(testActivity);
    await page.route('**/api/state', (route) => route.fulfill({json: state}));
    const generation = page.locator('[data-qa="conversation-generation"]');
    await expect(generation).toBeVisible();
    await expect(generation.getByText('Готовит ответ', {exact: true})).toBeVisible();
    await expect(generation.getByText('Собеседник спрашивает о встрече')).toBeVisible();
    await expect(generation.getByRole('progressbar')).toBeVisible();
    await expect(page.locator('[data-qa="reply-skeleton"]')).toHaveCount(0);
    state.activity[0].thinking = 'Уточняю время встречи по предыдущим сообщениям.';
    await expect(generation.getByText(state.activity[0].thinking, {exact: true})).toBeVisible();
    state.activity[0].phase = 'sending';
    await expect(generation.getByRole('button', {name: 'Остановить: Аня Смирнова'})).toBeDisabled();
    state.activity[0].phase = 'reply';
    const stop = generation.getByRole('button', {name: 'Остановить: Аня Смирнова'});
    await expect(stop).toBeEnabled();
    await page.screenshot({path: '../docs/resources/chat-generation.png', animations: 'disabled'});
    await page.route('**/api/activity/running/cancel', async (route) => {
        state.activity[0].status = 'cancelled';
        await route.fulfill({json: {ok: true}});
    });
    await stop.click();
    await expect(generation).toHaveCount(0);
    await page.getByRole('button', {name: 'Марк Волков', exact: true}).click();
    await expect(generation).toBeVisible();
    await expect(generation.getByText('Место в очереди: 1', {exact: true})).toBeVisible();
    await expect(generation.getByRole('progressbar')).toHaveCount(0);
    await expect(generation.getByText('Собеседник спрашивает о встрече')).toHaveCount(0);
});

test('group history shows author avatars at the end of each sender block', async ({page}) => {
    const state = structuredClone(workspaceState);
    state.chats[0].kind = 'group';
    const base: Message = {
        id: '101',
        chatId: '41',
        text: 'First message',
        kind: 'text',
        date: 1792060000,
        outgoing: false,
        group: true,
        senderId: 'user:10',
        senderName: 'Anna',
        senderAvatarUrl: '/api/media/group-avatar',
    };
    await page.route('**/api/state', (route) => route.fulfill({json: state}));
    await page.route('**/api/chats/41/messages*', (route) =>
        route.fulfill({
            json: [
                base,
                {...base, id: '102', text: 'Second message', date: base.date + 10},
                {
                    ...base,
                    id: '103',
                    text: 'Different author',
                    date: base.date + 20,
                    senderId: 'user:11',
                    senderName: 'Mark',
                    senderAvatarUrl: '',
                },
                {...base, id: '104', text: 'My reply', date: base.date + 30, outgoing: true},
            ],
        }),
    );
    await page.route('**/api/media/group-avatar', (route) =>
        route.fulfill({
            contentType: 'image/svg+xml',
            body: '<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32"><rect width="32" height="32" fill="#2878c9"/></svg>',
        }),
    );
    await page.reload();
    await page.getByRole('button', {name: 'Аня Смирнова', exact: true}).click();
    const rows = page.locator('[data-qa="group-message"]');
    await expect(rows).toHaveCount(3);
    await expect(rows.nth(0).locator('[data-qa="message-sender-avatar"]')).toHaveCount(0);
    await expect(rows.nth(1).locator('[data-qa="message-sender-avatar"] img')).toHaveCSS('opacity', '1');
    await expect(rows.nth(2).locator('[data-qa="message-sender-avatar"]')).toBeVisible();
    await expect(page.locator('[data-qa="message-sender-name"]')).toHaveText(['Anna', 'Mark']);
    await expect(page.locator('[data-qa="message-outgoing"] [data-qa="message-sender-avatar"]')).toHaveCount(
        0,
    );
    for (const width of [1440, 480]) {
        await page.setViewportSize({width, height: 900});
        const bounds = await rows.nth(1).evaluate((row) => {
            const avatar = row.querySelector('[data-qa="message-sender-avatar"]')!.getBoundingClientRect();
            const bubble = row.querySelector('[data-qa="message-incoming"]')!.getBoundingClientRect();
            return {
                gap: bubble.left - avatar.right,
                bottom: Math.abs(avatar.bottom - bubble.bottom),
                overflow: row.scrollWidth > row.clientWidth,
            };
        });
        expect(bounds.gap).toBeCloseTo(8, 1);
        expect(bounds.bottom).toBeLessThan(1);
        expect(bounds.overflow).toBe(false);
    }
});

test('model search icon is centered with consistent padding in both themes', async ({page}) => {
    await page.locator('[data-qa="nav-models"]').click();
    for (const dark of [false, true]) {
        if (dark) await toggleTheme(page);
        const bounds = await page.locator('[data-qa="model-search"]').evaluate((field) => {
            const outer = field.getBoundingClientRect();
            const icon = field.querySelector('svg')!.getBoundingClientRect();
            const input = field.querySelector('input')!.getBoundingClientRect();
            return {
                center: Math.abs(icon.y + icon.height / 2 - outer.y - outer.height / 2),
                left: icon.left - outer.left,
                gap: input.left - icon.right,
            };
        });
        expect(bounds.center).toBeLessThan(1);
        expect(bounds.left).toBeGreaterThanOrEqual(12);
        expect(bounds.gap).toBeGreaterThanOrEqual(8);
    }
});

test('compact contacts align their two lines and show AI only on the avatar', async ({page}) => {
    const rows = page.locator('[data-qa="chat-row"]');
    await expect(rows.first().locator('[data-qa="chat-ai-badge"]')).toBeVisible();
    await expect(rows.nth(2).locator('[data-qa="chat-ai-badge"]')).toHaveCount(0);
    for (const text of ['Вручную', 'Дружелюбно', 'Деловой']) {
        await expect(rows.getByText(text, {exact: true})).toHaveCount(0);
    }
    const geometry = await rows.evaluateAll((items) =>
        items.map((item) => {
            const row = item.getBoundingClientRect();
            const avatar = item.querySelector('[data-qa="chat-avatar"]')!.getBoundingClientRect();
            const name = item.querySelector('[data-qa="chat-name"]')!.getBoundingClientRect();
            const preview = item.querySelector('[data-qa="chat-preview"]')!.getBoundingClientRect();
            return {
                height: row.height,
                avatarOffset: Math.abs(avatar.y + avatar.height / 2 - row.y - row.height / 2),
                textOffset: Math.abs((name.y + preview.bottom) / 2 - row.y - row.height / 2),
            };
        }),
    );
    expect(geometry.every((row) => row.height === 66 && row.avatarOffset < 1 && row.textOffset < 1)).toBe(
        true,
    );
});

test('Telegram uses separate theme backgrounds and does not tile gradient fills', async ({page}) => {
    const state = structuredClone(workspaceState);
    state.chats[0].wallpaper = {kind: 'fill', colors: ['#abcdef', '#123456'], rotation: 45};
    state.chats[0].darkWallpaper = {kind: 'fill', colors: ['#112233', '#334455'], rotation: 90};
    await page.route('**/api/state', (route) => route.fulfill({json: state}));
    await page.reload();
    await page.getByRole('button', {name: 'Аня Смирнова', exact: true}).click();
    const wallpaper = page.locator('[data-qa="chat-wallpaper"]');
    await expect(wallpaper).toHaveCSS('background-size', 'cover');
    await expect(wallpaper).toHaveCSS('background-repeat', 'no-repeat');
    await expect(wallpaper).toHaveCSS(
        'background-image',
        'linear-gradient(45deg, rgb(171, 205, 239), rgb(18, 52, 86))',
    );
    await toggleTheme(page);
    await expect(wallpaper).toHaveCSS(
        'background-image',
        'linear-gradient(90deg, rgb(17, 34, 51), rgb(51, 68, 85))',
    );
});

test('Escape dismisses a dialog first and then closes the conversation', async ({page}) => {
    await page.getByRole('button', {name: 'Параметры общения', exact: true}).click();
    await expect(page.locator('[data-qa="conversation-settings"]')).toHaveCSS('border-radius', '18px');
    await page.getByRole('button', {name: 'Закрыть', exact: true}).click();
    await page.getByRole('button', {name: 'Настроить общение', exact: true}).click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await expect(page.locator('[data-qa="communication-dialog"] .g-modal__content')).toHaveCSS(
        'border-radius',
        '24px',
    );
    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(page.locator('[data-qa="conversation-header"]')).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(page.locator('[data-qa="conversation-header"]')).toHaveCount(0);
    await expect(page.getByText('Выберите диалог', {exact: true})).toBeVisible();
    await page.getByRole('button', {name: 'Аня Смирнова', exact: true}).click();
    await expect(page.locator('[data-qa="message-incoming"]').first()).toBeVisible();
});

test('conversation background appears only for an open chat on the chats page', async ({page}) => {
    const wallpaper = page.locator('[data-qa="chat-wallpaper"]');
    await expect(wallpaper).toBeAttached();
    await page.keyboard.press('Escape');
    await expect(wallpaper).toHaveCount(0);
    await expect(page.locator('[data-qa="workspace-shell"]')).toHaveCSS(
        'background-color',
        'rgb(243, 245, 247)',
    );
    await page.getByRole('button', {name: 'Аня Смирнова', exact: true}).click();
    await expect(wallpaper).toBeAttached();
    for (const tab of ['models', 'activity', 'metrics']) {
        await page.locator(`[data-qa="nav-${tab}"]`).click();
        await expect(wallpaper).toHaveCount(0);
    }
    await page.locator('[data-qa="nav-chats"]').click();
    await expect(wallpaper).toBeAttached();
    const disconnected = {...workspaceState, telegram: {...workspaceState.telegram, state: 'disconnected'}};
    await page.route('**/api/state', (route) => route.fulfill({json: disconnected}));
    await page.reload();
    await expect(page.locator('[data-qa="welcome"]')).toBeVisible();
    await expect(wallpaper).toHaveCount(0);
});

test('Escape clears pointer focus on the chat row while keyboard focus remains visible', async ({page}) => {
    const chat = page.getByRole('button', {name: 'Аня Смирнова', exact: true});
    await chat.click();
    await expect(chat).toBeFocused();
    await page.keyboard.press('Escape');
    await expect(page.getByText('Выберите диалог', {exact: true})).toBeVisible();
    await expect(chat).not.toBeFocused();
    expect(await chat.evaluate((element) => element.matches(':focus-visible'))).toBe(false);
    await chat.focus();
    await expect(chat).toHaveCSS('outline-style', 'solid');
});

[1440, 640].forEach((width) => {
    test(`conversation skeleton preserves the header and composer geometry at ${width}px`, async ({page}) => {
        await page.setViewportSize({width, height: 900});
        await page.emulateMedia({reducedMotion: 'reduce'});
        await page.keyboard.press('Escape');
        let release: () => void = () => {};
        const gate = new Promise<void>((resolve) => {
            release = resolve;
        });
        await page.route('**/api/chats/41/messages', async (route) => {
            await gate;
            await route.fulfill({json: testHistory('41')});
        });
        await page.getByRole('button', {name: 'Аня Смирнова', exact: true}).click();
        const skeleton = page.locator('[data-qa="conversation-skeleton"]');
        await expect(skeleton).toBeVisible();
        const geometry = await skeleton.evaluate((element) =>
            ['skeleton-conversation-header', 'skeleton-message-composer'].map((qa) => {
                const rect = element.querySelector(`[data-qa="${qa}"]`)!.getBoundingClientRect();
                return {x: rect.x, y: rect.y, width: rect.width, height: rect.height};
            }),
        );
        const controls = await skeleton
            .locator('[data-qa="skeleton-composer-control"]')
            .evaluateAll((elements) =>
                elements.map((element) => {
                    const rect = element.getBoundingClientRect();
                    return {x: rect.x, center: rect.y + rect.height / 2};
                }),
            );
        if (width === 1440) {
            await page.screenshot({
                path: '../docs/resources/conversation-loading.png',
                animations: 'disabled',
            });
        }
        release();
        await expect(skeleton).toHaveCount(0);
        await page.locator('[data-qa="conversation-header"]').evaluate(async (element) => {
            await Promise.all(element.parentElement!.getAnimations().map((animation) => animation.finished));
        });
        const loaded = await page.evaluate(() =>
            ['conversation-header', 'message-composer'].map((qa) => {
                const rect = document.querySelector(`[data-qa="${qa}"]`)!.getBoundingClientRect();
                return {x: rect.x, y: rect.y, width: rect.width, height: rect.height};
            }),
        );
        const loadedControls = await page
            .locator('[data-qa="message-composer"] button')
            .evaluateAll((elements) =>
                elements.map((element) => {
                    const rect = element.getBoundingClientRect();
                    return {x: rect.x, center: rect.y + rect.height / 2};
                }),
            );
        expect(loadedControls).toHaveLength(controls.length);
        loadedControls.forEach((control, index) => {
            expect(Math.abs(control.x - controls[index].x)).toBeLessThanOrEqual(1);
            expect(Math.abs(control.center - controls[index].center)).toBeLessThanOrEqual(1);
        });
        loaded.forEach((rect, index) =>
            Object.entries(rect).forEach(([key, value]) => {
                expect(
                    Math.abs(value - geometry[index][key as keyof typeof rect]),
                    JSON.stringify({geometry, loaded}),
                ).toBeLessThanOrEqual(1);
            }),
        );
    });
});

test('model placeholders follow the engine search and card grid layout', async ({page}) => {
    let loading = true;
    const engine = {
        ...workspaceState.engine,
        models: Array.from({length: 12}, (_, index) => ({
            ...workspaceState.engine.models[index % workspaceState.engine.models.length],
            id: `test-model-${index}`,
        })),
    };
    await page.route('**/api/state', (route) =>
        route.fulfill({json: {...workspaceState, engine: loading ? undefined : engine}}),
    );
    await page.reload();
    await page.getByRole('button', {name: 'Модели ИИ', exact: true}).click();
    await page.emulateMedia({reducedMotion: 'reduce'});
    await expect(page.locator('[data-qa="models-skeleton"]')).toBeVisible();
    const skeleton = await page.locator('[data-qa="skeleton-engine-card"]').boundingBox();
    const cards = await page.locator('[data-qa="model-card-skeleton"]').evaluateAll((elements) =>
        elements.map((element) => {
            const rect = element.getBoundingClientRect();
            return {x: rect.x, y: rect.y, width: rect.width};
        }),
    );
    await page.screenshot({path: '../docs/resources/models-loading.png', animations: 'disabled'});
    loading = false;
    await expect(page.locator('[data-qa="models-skeleton"]')).toHaveCount(0);
    const engineBounds = await page.locator('[data-qa="engine-card"]').boundingBox();
    expect(Math.abs(engineBounds!.x - skeleton!.x)).toBeLessThanOrEqual(1);
    expect(Math.abs(engineBounds!.width - skeleton!.width)).toBeLessThanOrEqual(1);
    const actual = await page.locator('[data-qa="model-card"]').evaluateAll((elements) =>
        elements.slice(0, 3).map((element) => {
            const rect = element.getBoundingClientRect();
            return {x: rect.x, y: rect.y, width: rect.width};
        }),
    );
    actual.forEach((rect, index) =>
        Object.entries(rect).forEach(([key, value]) => {
            expect(Math.abs(value - cards[index][key as keyof typeof rect])).toBeLessThanOrEqual(2);
        }),
    );
});

test('default and unavailable wallpapers use a plain theme background', async ({page}) => {
    const state = structuredClone(workspaceState);
    state.chats[0].wallpaper = {kind: 'wallpaper', url: '/api/media/missing-background'};
    await page.route('**/api/state', (route) => route.fulfill({json: state}));
    await page.route('**/api/media/missing-background', (route) => route.fulfill({status: 404}));
    await page.reload();
    await page.getByRole('button', {name: 'Аня Смирнова', exact: true}).click();
    const wallpaper = page.locator('[data-qa="chat-wallpaper"]');
    await expect(wallpaper).toHaveCSS('background-size', 'cover');
    await expect(wallpaper).toHaveCSS('background-image', 'none');
    await expect(wallpaper).toHaveCSS('background-color', 'rgb(231, 238, 234)');
    await page.getByRole('button', {name: 'Марк Волков', exact: true}).click();
    await expect(wallpaper).toHaveCSS('background-image', 'none');
    await toggleTheme(page);
    await expect(wallpaper).toHaveCSS('background-color', 'rgb(23, 29, 36)');
    await page.getByRole('button', {name: 'Аня Смирнова', exact: true}).click();
    await expect(wallpaper).toHaveCSS('background-image', 'none');
    await expect(wallpaper).toHaveCSS('background-color', 'rgb(23, 29, 36)');
});

test('disconnected account explains Aigram and opens the external-label key guide', async ({page}) => {
    await page.route('**/api/state', (route) =>
        route.fulfill({
            json: {
                ...workspaceState,
                telegram: {...workspaceState.telegram, state: 'disconnected'},
                chats: [],
            },
        }),
    );
    await page.reload();
    await expect(page.locator('[data-qa="welcome"]')).toBeVisible();
    await expect(page.getByRole('heading', {name: 'Aigram', exact: true})).toBeVisible();
    await expect(page).toHaveTitle('Aigram — Telegram');
    await expect(
        page.locator('[data-qa="chat-row"], [data-qa="workspace-view"], [data-qa^="nav-"]'),
    ).toHaveCount(0);
    await page.evaluate(() => document.fonts.ready);
    await page.screenshot({animations: 'disabled', path: '../docs/resources/welcome.png'});
    await page.getByRole('button', {name: 'Подключить Telegram', exact: true}).click();
    await expect(page.getByRole('link', {name: 'Открыть my.telegram.org'})).toHaveAttribute(
        'href',
        'https://my.telegram.org/apps',
    );
    for (const name of ['API ID', 'API Hash', 'Номер телефона']) {
        const input = page.getByLabel(name, {exact: true});
        await expect(input).toHaveValue('');
        expect(await input.getAttribute('placeholder')).toBeFalsy();
    }
    await page.getByRole('button', {name: 'Продолжить', exact: true}).click();
    await expect(page.getByText('Укажите числовой API ID')).toBeVisible();
    await page.screenshot({animations: 'disabled', path: '../docs/resources/telegram-guide.png'});
});

test('welcome stays readable in both themes and has no model selection before sign-in', async ({page}) => {
    const offline = {...workspaceState, telegram: {...workspaceState.telegram, state: 'disconnected'}};
    await page.route('**/api/state', (route) => route.fulfill({json: offline}));
    await page.reload();
    await expect(page.getByRole('button', {name: 'Подключить Telegram', exact: true})).toBeVisible();
    for (const text of ['Где получить API ID', 'подскажем при входе', 'Локальный ИИ']) {
        await expect(page.getByText(text, {exact: false})).toHaveCount(0);
    }
    await expect(page.getByRole('button', {name: 'Настроить', exact: true})).toHaveCount(0);
    for (const dark of [true, false]) {
        await persistTestTheme(page, dark);
        await page.reload();
        await expect(page.locator('body')).toContainClass(dark ? 'g-root_theme_dark' : 'g-root_theme_light');
        await expect(page.getByRole('heading', {name: 'Aigram', exact: true})).toBeVisible();
        const fontSizes = await page
            .locator('[data-qa="welcome"] .g-text')
            .evaluateAll((nodes) => nodes.map((node) => Number.parseFloat(getComputedStyle(node).fontSize)));
        expect(Math.min(...fontSizes)).toBeGreaterThanOrEqual(15);
        expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
        await page.screenshot({
            animations: 'disabled',
            path: `../docs/resources/welcome-${dark ? 'dark' : 'light'}.png`,
        });
    }
    await page.setViewportSize({width: 390, height: 844});
    const brand = page.locator('[data-qa="welcome"]').getByText('Aigram', {exact: true});
    await expect(brand).toBeInViewport();
    await expect(page.getByRole('button', {name: 'Подключить Telegram', exact: true})).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
    await page.setViewportSize({width: 880, height: 580});
    await expect(brand).toBeInViewport();
    await page.getByRole('button', {name: 'Подключить Telegram', exact: true}).click();
    await expect(page.getByRole('dialog')).toBeVisible();
});

test('dark/light dialogs follow the active palette and preferences survive reload', async ({page}) => {
    for (const dark of [true, false]) {
        await toggleTheme(page);
        await expect(page.locator('body')).toContainClass(dark ? 'g-root_theme_dark' : 'g-root_theme_light');
        await page.getByRole('button', {name: 'Настроить общение', exact: true}).click();
        const palette = await page.locator('.g-modal__content').evaluate((element) => ({
            background: getComputedStyle(element).backgroundColor,
            panel: getComputedStyle(element).getPropertyValue('--tiho-panel').trim(),
        }));
        const expected = dark ? 'rgb(36, 42, 52)' : 'rgb(255, 255, 255)';
        expect(palette.background).toBe(expected);
        await page.getByRole('button', {name: 'Отмена', exact: true}).click();
    }
    await toggleTheme(page);
    await page.getByRole('button', {name: 'Мой профиль', exact: true}).click();
    await page.getByRole('menuitem', {name: 'Настройки', exact: true}).click();
    await chooseSettingsSection(page, 'language');
    await page.getByRole('radio', {name: 'English', exact: true}).click();
    await page.keyboard.press('Escape');
    await expect(page.getByRole('textbox', {name: 'Search chats'})).toBeVisible();
    await page.reload();
    await expect(page.locator('body')).toContainClass('g-root_theme_dark');
    await expect(page.getByRole('textbox', {name: 'Search chats'})).toBeVisible();
    await page.getByRole('button', {name: 'Аня Смирнова', exact: true}).click();
    await expect(page.locator('[data-qa="message-incoming"]').first()).toBeVisible();
    await page.screenshot({animations: 'disabled', path: '../docs/resources/dark.png'});
});

test('mobile supports switching between conversation and chat list', async ({page}) => {
    await page.setViewportSize({width: 390, height: 844});
    const pause = await page.getByRole('button', {name: 'Запустить ИИ', exact: true}).boundingBox();
    expect(pause).not.toBeNull();
    expect((pause?.x ?? 0) + (pause?.width ?? 0)).toBeLessThanOrEqual(390);
    await page.getByRole('button', {name: 'К списку диалогов'}).click();
    await expect(page.getByRole('textbox', {name: 'Поиск по диалогам'})).toBeVisible();
    await page.getByRole('button', {name: 'Марк Волков', exact: true}).click();
    await expect(page.locator('[data-qa="conversation-header"]')).toContainText('Марк Волков');
    expect(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)).toBe(false);
    await page.screenshot({animations: 'disabled', path: '../docs/resources/mobile.png'});
});

test('inset chat rows, search icon, readable text and message edges stay aligned', async ({page}) => {
    await page.evaluate(() => document.fonts.ready);
    const geometry = await page.evaluate(() => {
        const bounds = (selector: string) =>
            Array.from(document.querySelectorAll(selector)).map((element) => {
                const rect = element.getBoundingClientRect();
                return {x: rect.x, y: rect.y, width: rect.width, height: rect.height};
            });
        const search = document.querySelector('[data-qa="chat-search"]')!;
        const searchIcon = search.querySelector('svg')!.getBoundingClientRect();
        const searchText = search.querySelector('input')!.getBoundingClientRect();
        return {
            rows: bounds('[data-qa="chat-row"]'),
            list: bounds('[data-qa="chat-list-scroll"]')[0],
            listOuter: (() => {
                const rect = document
                    .querySelector('[data-qa="chat-list-scroll"]')!
                    .parentElement!.getBoundingClientRect();
                return {x: rect.x, width: rect.width};
            })(),
            avatars: bounds('[data-qa="chat-avatar"]'),
            names: bounds('[data-qa="chat-name"]'),
            checkboxes: bounds('[data-qa="chat-checkbox"]'),
            header: bounds('[data-qa="conversation-header"]')[0],
            canvas: bounds('[data-qa="conversation-messages"]')[0],
            incoming: bounds('[data-qa="message-incoming"]')[0],
            outgoing: bounds('[data-qa="message-outgoing"]')[0],
            iconLeft: searchIcon.x - search.getBoundingClientRect().x,
            iconGap: searchText.x - searchIcon.right,
            fonts: Array.from(document.querySelectorAll('[data-qa="workspace-view"] span'))
                .filter(
                    (element) => element.textContent?.trim() && element.getBoundingClientRect().height > 0,
                )
                .map((element) => parseFloat(getComputedStyle(element).fontSize)),
            nav: Array.from(document.querySelectorAll('[data-qa^="nav-"]')).map((button) => {
                const outer = button.getBoundingClientRect();
                const icon = button.querySelector('svg')!.getBoundingClientRect();
                return {
                    dx: Math.abs(outer.x + outer.width / 2 - icon.x - icon.width / 2),
                    dy: Math.abs(outer.y + outer.height / 2 - icon.y - icon.height / 2),
                };
            }),
        };
    });
    for (const column of [geometry.avatars, geometry.names]) {
        expect(
            Math.max(...column.map((item) => item.x)) - Math.min(...column.map((item) => item.x)),
        ).toBeLessThan(1);
    }
    expect(
        geometry.rows.every(
            (row) => Math.abs(row.width - geometry.list.width) < 1 && Math.abs(row.x - geometry.list.x) < 1,
        ),
    ).toBe(true);
    expect(geometry.list.x - geometry.listOuter.x).toBeCloseTo(8, 2);
    expect(geometry.listOuter.width - geometry.list.width).toBeCloseTo(16, 2);
    expect(geometry.rows.every((row) => row.height <= 76.1)).toBe(true);
    expect(geometry.header.height).toBeCloseTo(60, 2);
    expect(geometry.incoming.x - geometry.canvas.x).toBeLessThanOrEqual(24);
    expect(
        geometry.canvas.x + geometry.canvas.width - geometry.outgoing.x - geometry.outgoing.width,
    ).toBeLessThanOrEqual(24);
    expect(geometry.iconLeft).toBeGreaterThanOrEqual(12);
    expect(geometry.iconGap).toBeGreaterThanOrEqual(10);
    expect(Math.min(...geometry.fonts)).toBeGreaterThanOrEqual(13);
    expect(geometry.nav.every((item) => item.dx < 1 && item.dy < 1)).toBe(true);
    await page.getByRole('button', {name: 'Параметры общения', exact: true}).click();
    await expect(page.getByText('Ваш помощник', {exact: true})).toHaveCount(0);
    await expect(page.getByText('Общается так, как удобно вам', {exact: true})).toHaveCount(0);
});

test('plain and Telegram photo backgrounds cover the shell behind opaque controls at every window size', async ({
    page,
}) => {
    await page.route('**/api/media/wallpaper-test', (route) =>
        route.fulfill({
            contentType: 'image/svg+xml',
            body: '<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"><rect width="100" height="100" fill="#f00"/></svg>',
        }),
    );
    for (const wallpaper of [
        undefined,
        {kind: 'wallpaper' as const, url: '/api/media/wallpaper-test', blurred: true},
    ]) {
        const state = structuredClone(workspaceState);
        state.chats[0].wallpaper = wallpaper;
        await page.route('**/api/state', (route) => route.fulfill({json: state}));
        await persistTestTheme(page, Boolean(wallpaper));
        await page.reload();
        await page.getByRole('button', {name: 'Аня Смирнова', exact: true}).click();
        for (const width of [1440, 1000, 480]) {
            await page.setViewportSize({width, height: 900});
            await expect(page.locator('[data-qa="chat-wallpaper"]')).toBeAttached();
            const bounds = await page.evaluate(() => {
                const shell = document.querySelector('[data-qa="workspace-shell"]')!.getBoundingClientRect();
                const wallpaper = document
                    .querySelector('[data-qa="chat-wallpaper"]')!
                    .getBoundingClientRect();
                return {
                    shell: {x: shell.x, y: shell.y, width: shell.width, height: shell.height},
                    wallpaper: {
                        x: wallpaper.x,
                        y: wallpaper.y,
                        width: wallpaper.width,
                        height: wallpaper.height,
                    },
                };
            });
            expect(bounds.wallpaper).toEqual(bounds.shell);
            await expect(page.locator('[data-qa="chat-wallpaper"]')).toHaveCSS('z-index', '-1');
            await expect(page.locator('[data-qa="sidebar"]')).toHaveCSS(
                'background-color',
                'rgba(0, 0, 0, 0)',
            );
            await expect(page.locator('[data-qa="sidebar-navigation"]')).toHaveCSS('border-radius', '18px');
            await expect(page.locator('[data-qa="window-bar"]')).toHaveCSS('border-bottom-width', '0px');
            await expect(page.locator('[data-qa="conversation-header"]')).toHaveCSS(
                'background-color',
                wallpaper ? 'rgb(36, 42, 52)' : 'rgb(255, 255, 255)',
            );
            await expect(page.locator('[data-qa="message-composer"]')).toHaveCSS(
                'background-color',
                wallpaper ? 'rgb(36, 42, 52)' : 'rgb(255, 255, 255)',
            );
        }
        if (wallpaper) {
            await expect(page.locator('[data-qa="chat-wallpaper"]')).toHaveCSS('filter', 'blur(8px)');
        }
        await page.setViewportSize({width: 1440, height: 900});
    }
});

test('overlay scrollbar preserves content width and supports wheel and keyboard', async ({page}) => {
    const before = await page.locator('[data-qa="chat-row"]').first().boundingBox();
    const chats = Array.from({length: 80}, (_, index) => ({
        ...workspaceState.chats[0],
        id: String(index + 100),
        title: `Контакт ${index + 1}`,
    }));
    await page.route('**/api/state', (route) => route.fulfill({json: {...workspaceState, chats}}));
    await page.reload();
    const list = page.locator('[data-qa="chat-list-scroll"]');
    await expect(page.locator('[data-qa="chat-row"]')).toHaveCount(40);
    const after = await page.locator('[data-qa="chat-row"]').first().boundingBox();
    expect(after?.width).toBeCloseTo(before?.width ?? 0, 2);
    expect(
        await list.evaluate((element) => element.clientWidth === element.getBoundingClientRect().width),
    ).toBe(true);
    const bar = list.locator('..').getByRole('scrollbar');
    await expect(bar).toBeVisible();
    expect((await bar.boundingBox())?.width).toBeLessThanOrEqual(6);
    await list.hover();
    await page.mouse.wheel(0, 500);
    await expect.poll(() => list.evaluate((element) => element.scrollTop)).toBeGreaterThan(0);
    await bar.focus();
    await page.keyboard.press('End');
    await expect(page.locator('[data-qa="chat-row"]')).toHaveCount(80);
    await page.keyboard.press('End');
    await expect(bar).toHaveAttribute('aria-valuenow', '100');
    await page.keyboard.press('Home');
    await expect(bar).toHaveAttribute('aria-valuenow', '0');
});

test('initial state, history and preview use skeletons until responses arrive', async ({page}) => {
    let release: () => void = () => {};
    const gate = new Promise<void>((resolve) => {
        release = resolve;
    });
    await page.route('**/api/state', async (route) => {
        await gate;
        await route.fulfill({json: workspaceState});
    });
    await page.reload({waitUntil: 'domcontentloaded'});
    await expect(page.getByRole('status', {name: 'Загрузка…', exact: true})).toBeVisible();
    await expect(page.locator('[data-qa="workspace-view"]')).toHaveCount(0);
    release();
    let releaseHistory: () => void = () => {};
    const historyGate = new Promise<void>((resolve) => {
        releaseHistory = resolve;
    });
    await page.route('**/api/chats/41/messages', async (route) => {
        await historyGate;
        await route.fulfill({json: testHistory('41')});
    });
    await page.getByRole('button', {name: 'Аня Смирнова', exact: true}).click();
    await expect(page.getByRole('status', {name: 'Загрузка переписки'})).toBeVisible();
    releaseHistory();
    await expect(page.locator('[data-qa="message-incoming"]').first()).toBeVisible();
    let releaseReply: () => void = () => {};
    const replyGate = new Promise<void>((resolve) => {
        releaseReply = resolve;
    });
    await page.route('**/api/preview', async (route) => {
        await replyGate;
        await route.fulfill({json: {text: 'Тестовый ответ', durationMs: 100, tokensPerSecond: 50}});
    });
    await page.getByRole('button', {name: 'Настроить общение', exact: true}).click();
    await page.getByRole('button', {name: 'Проверить ответ', exact: true}).click();
    await expect(page.getByRole('status', {name: 'Готовит ответ'})).toBeVisible();
    releaseReply();
    await expect(page.getByText('Тестовый ответ', {exact: true})).toBeVisible();
});

test('model installed icon and text share the same center with compact engine spacing', async ({page}) => {
    await page.getByRole('button', {name: 'Модели ИИ', exact: true}).click();
    const badge = page.locator('.g-label').filter({hasText: 'Установлена'}).first();
    const alignment = await badge.evaluate((element) => {
        const icon = element.querySelector('svg')!.getBoundingClientRect();
        const text = element.querySelector('.g-label__text')!.getBoundingClientRect();
        return Math.abs(icon.y + icon.height / 2 - text.y - text.height / 2);
    });
    expect(alignment).toBeLessThanOrEqual(1);
    await page.screenshot({animations: 'disabled', path: '../docs/resources/models.png'});
    expect(await page.locator('[data-qa="app-scroll"]').evaluate((element) => element.scrollTop)).toBe(0);
});

test('global scroll and reduced motion remain usable on short windows', async ({page}) => {
    await page.setViewportSize({width: 1000, height: 420});
    await page.emulateMedia({reducedMotion: 'reduce'});
    await page.getByRole('button', {name: 'Модели ИИ', exact: true}).click();
    const globalScroll = page.locator('[data-qa="app-scroll"]');
    const scrolling = await globalScroll.evaluate((element) => ({
        height: element.scrollHeight,
        viewport: element.clientHeight,
        overflow: getComputedStyle(element).overflowY,
    }));
    expect(scrolling.height).toBeGreaterThan(scrolling.viewport);
    expect(scrolling.overflow).toBe('auto');
    expect(
        await page
            .locator('[data-qa="workspace-view"]')
            .evaluate((element) => parseFloat(getComputedStyle(element).animationDuration)),
    ).toBeLessThanOrEqual(0.001);
    await globalScroll.evaluate((element) => element.scrollTo(0, element.scrollHeight));
    expect(await globalScroll.evaluate((element) => element.scrollTop)).toBeGreaterThan(0);
});

test('profile menu saves shared instructions and signs out without a settings page', async ({page}) => {
    await expect(page.getByRole('button', {name: 'Настройки', exact: true})).toHaveCount(0);
    await page.getByRole('button', {name: 'Мой профиль', exact: true}).click();
    await page.getByRole('menuitem', {name: 'Настройки', exact: true}).click();
    await chooseSettingsSection(page, 'prompt');
    const field = page.getByRole('textbox', {name: 'Общий промпт', exact: true});
    await field.fill('Не назначай встречи без моего подтверждения.');
    const patch = page.waitForRequest(
        (request) => request.method() === 'PATCH' && request.url().endsWith('/api/settings'),
    );
    await page.getByRole('button', {name: 'Сохранить настройки', exact: true}).click();
    expect((await patch).postDataJSON()).toMatchObject({
        globalPrompt: 'Не назначай встречи без моего подтверждения.',
    });
    await expect(page.getByRole('dialog')).toBeVisible();
    await expect(page.getByRole('status')).toHaveText('Сохранено');
    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await page.getByRole('button', {name: 'Мой профиль', exact: true}).click();
    await page.getByRole('menuitem', {name: 'Настройки', exact: true}).click();
    await chooseSettingsSection(page, 'prompt');
    await expect(field).toHaveValue('Не назначай встречи без моего подтверждения.');
    await page.getByRole('button', {name: 'Отмена', exact: true}).click();
    const disconnected = {
        ...workspaceState,
        telegram: {...workspaceState.telegram, state: 'disconnected'},
        chats: [],
    };
    await page.route('**/api/telegram/logout', async (route) => {
        await page.route('**/api/state', (next) => next.fulfill({json: disconnected}));
        await route.fulfill({json: {ok: true}});
    });
    await page.getByRole('button', {name: 'Мой профиль', exact: true}).click();
    await page.getByRole('menuitem', {name: 'Выйти из Telegram', exact: true}).click();
    await expect(page.locator('[data-qa="welcome"]')).toBeVisible();
    await expect(page.locator('[data-qa="chat-row"]')).toHaveCount(0);
});

test('model cards compare capabilities and measure actual local response speed', async ({page}) => {
    await page.route('**/api/preview', (route) =>
        route.fulfill({json: {text: 'Хорошо!', durationMs: 412, tokensPerSecond: 123.4}}),
    );
    await page.getByRole('button', {name: 'Модели ИИ', exact: true}).click();
    const table = page.getByRole('table', {name: 'Qwen 3 · 0.6B', exact: true});
    await expect(table).toContainText('Скорость ответа');
    await expect(table).toContainText('Рассуждения');
    await expect(table).toContainText('40 960 токенов');
    const request = page.waitForRequest('**/api/preview');
    await page.getByRole('button', {name: 'Проверить скорость', exact: true}).first().click();
    const body = (await request).postDataJSON();
    expect(body.rule.model).toBe('qwen3:0.6b');
    expect(body.chatId).toBeUndefined();
    await expect(table).toContainText('412 мс');
    await expect(table).toContainText('123.4 токенов/с');
    expect(await page.locator('[data-qa="app-scroll"]').evaluate((element) => element.clientWidth)).toBe(
        1440,
    );
});

test('Telegram avatars, photos and static/animated stickers render as media', async ({page}) => {
    const svg =
        '<svg xmlns="http://www.w3.org/2000/svg" width="320" height="240"><rect width="320" height="240" fill="#4690da"/><circle cx="160" cy="110" r="60" fill="#fff"/></svg>';
    await page.route('**/api/media/*', (route) => route.fulfill({body: svg, contentType: 'image/svg+xml'}));
    const animation = {
        v: '5.7.4',
        fr: 30,
        ip: 0,
        op: 30,
        w: 180,
        h: 180,
        nm: 'Sticker',
        ddd: 0,
        assets: [],
        layers: [
            {
                ddd: 0,
                ind: 1,
                ty: 4,
                nm: 'Circle',
                sr: 1,
                ks: {
                    o: {a: 0, k: 100},
                    r: {a: 0, k: 0},
                    p: {a: 0, k: [90, 90, 0]},
                    a: {a: 0, k: [0, 0, 0]},
                    s: {a: 0, k: [100, 100, 100]},
                },
                ao: 0,
                shapes: [
                    {ty: 'el', p: {a: 0, k: [0, 0]}, s: {a: 0, k: [100, 100]}, nm: 'Circle', d: 1},
                    {ty: 'fl', c: {a: 0, k: [0.2, 0.6, 0.9, 1]}, o: {a: 0, k: 100}, r: 1},
                ],
                ip: 0,
                op: 30,
                st: 0,
                bm: 0,
            },
        ],
    };
    await page.route('**/api/media/93', (route) => route.fulfill({json: animation}));
    const state = structuredClone(workspaceState);
    state.chats[0].avatarUrl = '/api/media/90';
    await page.route('**/api/state', (route) => route.fulfill({json: state}));
    const attachment = {
        width: 320,
        height: 240,
        thumbnailFileId: 0,
        size: 100,
        fileName: 'photo.jpg',
        mimeType: 'image/jpeg',
        fileId: 91,
    };
    await page.route('**/api/chats/41/messages', (route) =>
        route.fulfill({
            json: [
                {
                    id: '1',
                    chatId: '41',
                    text: 'Подпись фотографии',
                    outgoing: false,
                    date: 1792064100,
                    kind: 'photo',
                    media: attachment,
                },
                {
                    id: '2',
                    chatId: '41',
                    text: '',
                    outgoing: true,
                    date: 1792064101,
                    kind: 'sticker',
                    media: {...attachment, fileId: 92, mimeType: 'image/webp'},
                },
                {
                    id: '3',
                    chatId: '41',
                    text: '',
                    outgoing: false,
                    date: 1792064102,
                    kind: 'sticker',
                    media: {...attachment, fileId: 93, mimeType: 'application/x-tgsticker'},
                },
            ],
        }),
    );
    await page.reload();
    await page.getByRole('button', {name: 'Аня Смирнова', exact: true}).click();
    const avatar = page.locator('[data-qa="chat-avatar"] img').first();
    await expect
        .poll(() => avatar.evaluate((element) => (element as HTMLImageElement).naturalWidth))
        .toBe(320);
    const photo = page.getByRole('img', {name: 'Фото', exact: true}).first();
    await expect(photo).toBeVisible();
    expect((await photo.boundingBox())?.width).toBeGreaterThan(200);
    await expect(page.getByText('Подпись фотографии', {exact: true})).toBeVisible();
    await expect(page.locator('[role="img"][aria-label="Стикер"] svg')).toBeVisible();
    await page.getByRole('button', {name: 'Открыть фотографию', exact: true}).click();
    await expect(page.getByRole('dialog', {name: 'Открыть медиа', exact: true})).toBeVisible();
    await page.screenshot({animations: 'disabled', path: '../docs/resources/media.png'});
});

test('Telegram formatting includes nested emphasis, links, quotes, code and spoilers', async ({
    page,
    context,
}) => {
    const text = 'Жирный курсив сайт\nconst x = 1;\nЦитата\nсекрет\nhttps://example.org';
    const entities = [
        {offset: 0, length: 6, type: 'bold'},
        {offset: text.indexOf('курсив'), length: 6, type: 'italic'},
        {offset: text.indexOf('сайт'), length: 4, type: 'texturl', url: 'https://example.com'},
        {offset: text.indexOf('const'), length: 12, type: 'precode', language: 'javascript'},
        {offset: text.indexOf('Цитата'), length: 6, type: 'quote'},
        {offset: text.indexOf('секрет'), length: 6, type: 'spoiler'},
    ];
    await page.route('**/api/chats/41/messages', (route) =>
        route.fulfill({
            json: [{id: '1', chatId: '41', text, outgoing: false, date: 1792064100, kind: 'text', entities}],
        }),
    );
    await page.reload();
    await page.getByRole('button', {name: 'Аня Смирнова', exact: true}).click();
    await expect(page.getByRole('link', {name: 'сайт', exact: true})).toHaveAttribute(
        'href',
        'https://example.com/',
    );
    await expect(page.getByRole('link', {name: 'https://example.org', exact: true})).toHaveAttribute(
        'href',
        'https://example.org/',
    );
    expect(
        await page
            .getByText('Жирный', {exact: true})
            .evaluate((element) => getComputedStyle(element).fontWeight),
    ).toBe('700');
    expect(
        await page
            .getByText('курсив', {exact: true})
            .evaluate((element) => getComputedStyle(element).fontStyle),
    ).toBe('italic');
    await expect(page.locator('blockquote')).toHaveText('Цитата');
    await expect(page.locator('pre')).toHaveText('const x = 1;');
    await page.getByRole('button', {name: 'Показать спойлер', exact: true}).click();
    await expect(page.getByText('секрет', {exact: true})).toBeVisible();
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await page.getByRole('button', {name: 'Копировать', exact: true}).click();
    expect(await page.evaluate(() => navigator.clipboard.readText())).toBe('const x = 1;');
});

test('settings persist reply pacing, typing, tray and Windows startup options', async ({page}) => {
    await page.getByRole('button', {name: 'Мой профиль', exact: true}).click();
    await page.getByRole('menuitem', {name: 'Настройки', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Настройки', exact: true});
    await chooseSettingsSection(page, 'replies');
    await dialog.getByRole('switch', {name: 'Показывать «печатает…»'}).click();
    await chooseSettingsSection(page, 'application');
    await dialog.getByRole('switch', {name: 'При закрытии скрывать в трей'}).click();
    await dialog.getByText('Запускать с Windows', {exact: true}).click();
    await dialog.getByText('Включать ИИ при запуске приложения', {exact: true}).click();
    await chooseSettingsSection(page, 'replies');
    await dialog.getByRole('slider', {name: 'Общая задержка перед ответом'}).focus();
    await page.keyboard.press('Home');
    await page.keyboard.press('ArrowRight');
    const patch = page.waitForRequest(
        (request) => request.method() === 'PATCH' && request.url().endsWith('/api/settings'),
    );
    await dialog.getByRole('button', {name: 'Сохранить настройки', exact: true}).click();
    expect((await patch).postDataJSON()).toMatchObject({
        showTyping: false,
        closeToTray: false,
        launchAtLogin: true,
        resumeOnStartup: true,
    });
    await expect(dialog).toBeVisible();
    await expect(dialog.getByRole('status')).toHaveText('Сохранено');
    await dialog.locator('[data-qa="settings-content-replies"]').evaluate((body) => (body.scrollTop = 0));
    await page.screenshot({path: '../docs/resources/settings-saved.png', animations: 'disabled'});
    await dialog.getByText('Показывать «печатает…»', {exact: true}).click();
    await expect(dialog.getByRole('status')).toHaveCount(0);
    await dialog.getByText('Показывать «печатает…»', {exact: true}).click();
    await page.keyboard.press('Escape');
    await page.getByRole('button', {name: 'Мой профиль', exact: true}).click();
    await page.getByRole('menuitem', {name: 'Настройки', exact: true}).click();
    await expect(dialog.getByRole('switch', {name: 'Запускать с Windows'})).toBeChecked();
    await chooseSettingsSection(page, 'replies');
    await expect(dialog.getByRole('switch', {name: 'Показывать «печатает…»'})).not.toBeChecked();
    await page.screenshot({animations: 'disabled', path: '../docs/resources/settings.png'});
});

test('failed settings saves preserve edits and a successful retry stays open', async ({page}) => {
    let fail = true;
    await page.route('**/api/settings', async (route) => {
        if (fail) {
            await route.fulfill({status: 502, json: {message: 'Тестовая ошибка сохранения'}});
        } else {
            await route.fallback();
        }
    });
    await page.getByRole('button', {name: 'Мой профиль', exact: true}).click();
    await page.getByRole('menuitem', {name: 'Настройки', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Настройки', exact: true});
    await chooseSettingsSection(page, 'replies');
    const typing = dialog.getByRole('switch', {name: 'Показывать «печатает…»'});
    await typing.click();
    await dialog.getByRole('button', {name: 'Сохранить настройки', exact: true}).click();
    await expect(dialog).toBeVisible();
    await expect(dialog.getByText('Тестовая ошибка сохранения', {exact: true})).toBeVisible();
    await expect(dialog.getByRole('status')).toHaveCount(0);
    await expect(typing).not.toBeChecked();
    fail = false;
    await dialog.getByRole('button', {name: 'Сохранить настройки', exact: true}).click();
    await expect(dialog.getByRole('status')).toHaveText('Сохранено');
    await expect(dialog.getByText('Тестовая ошибка сохранения', {exact: true})).toHaveCount(0);
    await expect(typing).not.toBeChecked();
});

test('GPU selection saves a supported device without restarting the running Ollama', async ({page}) => {
    const state = structuredClone(workspaceState);
    const engineActions: string[] = [];
    page.on('request', (request) => {
        if (['/api/engine/start', '/api/engine/install'].includes(new URL(request.url()).pathname)) {
            engineActions.push(request.url());
        }
    });
    await page.route('**/api/state', (route) => route.fulfill({json: state}));
    await page.route('**/api/engine/devices', (route) =>
        route.fulfill({
            json: {
                ...computeDevices,
                configured: state.settings.ollamaDevice,
                restartRequired: state.settings.ollamaDevice !== 'auto',
            },
        }),
    );
    await page.route('**/api/settings', async (route) => {
        Object.assign(state.settings, route.request().postDataJSON());
        await route.fulfill({json: {ok: true}});
    });
    await page.getByRole('button', {name: 'Мой профиль', exact: true}).click();
    await page.getByRole('menuitem', {name: 'Настройки', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Настройки', exact: true});
    await chooseSettingsSection(page, 'compute');
    const panel = dialog.locator('[data-qa="compute-device-panel"]');
    await panel.getByRole('combobox', {name: 'Устройство Ollama'}).click();
    await page.getByRole('option', {name: 'NVIDIA GeForce RTX 4070'}).click();
    const save = page.waitForRequest(
        (request) => request.method() === 'PATCH' && request.url().endsWith('/api/settings'),
    );
    await dialog.getByRole('button', {name: 'Сохранить настройки', exact: true}).click();
    expect((await save).postDataJSON().ollamaDevice).toBe(computeDevices.devices[2].id);
    await expect(dialog.getByRole('button', {name: 'Сохранено', exact: true})).toBeVisible();
    await expect(panel.getByText('Применится при следующем запуске Ollama', {exact: true})).toBeVisible();
    await panel.scrollIntoViewIfNeeded();
    await page.screenshot({path: '../docs/resources/compute-device.png', animations: 'disabled'});
    expect(engineActions).toEqual([]);
});

test('external Ollama device selection explains why its controls are disabled', async ({page}) => {
    await page.getByRole('button', {name: 'Мой профиль', exact: true}).click();
    await page.getByRole('menuitem', {name: 'Настройки', exact: true}).click();
    await chooseSettingsSection(page, 'compute');
    const panel = page.locator('[data-qa="compute-device-panel"]');
    await expect(panel.getByRole('combobox', {name: 'Устройство Ollama'})).toBeDisabled();
    await expect(panel.getByText('Этой Ollama управляет внешний процесс или сервис')).toBeVisible();
});

test('all writing styles have emoji and rough styles and imitation survive saving', async ({page}) => {
    await page.getByRole('button', {name: 'Настроить общение', exact: true}).click();
    await page.getByRole('combobox', {name: 'Стиль общения', exact: true}).click();
    const rough = page.getByRole('option', {name: 'Быдланский'});
    await expect(rough).toContainText('🤬');
    await expect(page.getByRole('option', {name: 'Агрессивный'})).toContainText('😠');
    const aligned = await rough.evaluate((option) => {
        const emoji = option.querySelector('[data-qa="style-emoji"]')?.getBoundingClientRect();
        const name = option.querySelector('[data-qa="style-name"]')?.getBoundingClientRect();
        return emoji && name ? Math.abs(emoji.y + emoji.height / 2 - name.y - name.height / 2) : Infinity;
    });
    expect(aligned).toBeLessThanOrEqual(1);
    const rowOffsets = await page.getByRole('option').evaluateAll((options) =>
        options.flatMap((option) => {
            const row = option.getBoundingClientRect();
            return ['style-emoji', 'style-name'].map((qa) => {
                const child = option.querySelector(`[data-qa="${qa}"]`)?.getBoundingClientRect();
                return child ? Math.abs(child.top + child.height / 2 - row.top - row.height / 2) : Infinity;
            });
        }),
    );
    rowOffsets.forEach((offset) => expect(offset).toBeLessThanOrEqual(1));
    await rough.click();
    const selectedStyle = page.getByRole('combobox', {name: 'Стиль общения', exact: true});
    await expect(selectedStyle).toContainText('Быдланский');
    const selectedBounds = await selectedStyle.evaluate((field) => {
        const bounds = field.getBoundingClientRect();
        const emoji = field.querySelector('[data-qa="style-emoji"]')?.getBoundingClientRect();
        const name = field.querySelector('[data-qa="style-name"]')?.getBoundingClientRect();
        return [emoji, name].map((child) =>
            child ? Math.abs(child.y + child.height / 2 - bounds.y - bounds.height / 2) : Infinity,
        );
    });
    selectedBounds.forEach((offset) => expect(offset).toBeLessThanOrEqual(1));
    await page.screenshot({path: '../docs/resources/communication-settings.png', animations: 'disabled'});
    await expect(page.getByRole('switch', {name: 'Подстраиваться под мою переписку'})).toBeChecked();
    const save = page.waitForRequest(
        (request) => request.method() === 'PUT' && request.url().endsWith('/api/chats/41/rule'),
    );
    await page.getByRole('button', {name: 'Сохранить настройки', exact: true}).click();
    expect((await save).postDataJSON()).toMatchObject({style: 'rough'});
});

test('reply length and delay occupy separate full-width rows and retain their values', async ({page}) => {
    await page.getByRole('button', {name: 'Настроить общение', exact: true}).click();
    const dialog = page.getByRole('dialog');
    await dialog.locator('[data-qa="reply-delay-setting"]').scrollIntoViewIfNeeded();
    const geometry = await dialog.evaluate((element) => {
        const length = element.querySelector('[data-qa="response-length-setting"]')!.getBoundingClientRect();
        const delay = element.querySelector('[data-qa="reply-delay-setting"]')!.getBoundingClientRect();
        return {
            gap: delay.top - length.bottom,
            left: Math.abs(length.left - delay.left),
            width: Math.abs(length.width - delay.width),
        };
    });
    expect(geometry.gap).toBeGreaterThanOrEqual(16);
    expect(geometry.left).toBeLessThanOrEqual(1);
    expect(geometry.width).toBeLessThanOrEqual(1);
    await dialog.getByRole('slider', {name: 'Длина ответа', exact: true}).press('End');
    await dialog.getByRole('slider', {name: 'Задержка перед ответом', exact: true}).press('Home');
    await dialog.getByRole('slider', {name: 'Задержка перед ответом', exact: true}).press('ArrowRight');
    await page.screenshot({path: '../docs/resources/communication-pacing.png', animations: 'disabled'});
    const saved = page.waitForRequest(
        (request) => request.method() === 'PUT' && request.url().endsWith('/api/chats/41/rule'),
    );
    await dialog.getByRole('button', {name: 'Сохранить настройки', exact: true}).click();
    expect((await saved).postDataJSON()).toMatchObject({maxTokens: 512, delaySeconds: 1});
    await expect(dialog).toBeVisible();
    await expect(dialog.getByRole('status')).toHaveText('Сохранено');
    await page.keyboard.press('Escape');
    await page.getByRole('button', {name: 'Настроить общение', exact: true}).click();
    await expect(page.getByRole('slider', {name: 'Длина ответа', exact: true})).toHaveAttribute(
        'aria-valuenow',
        '512',
    );
    await expect(page.getByRole('slider', {name: 'Задержка перед ответом', exact: true})).toHaveAttribute(
        'aria-valuenow',
        '1',
    );
});

test('style option content is centered within every dropdown row in dark theme', async ({page}) => {
    await persistTestTheme(page, true);
    await page.reload();
    await page.getByRole('button', {name: 'Аня Смирнова', exact: true}).click();
    await page.getByRole('button', {name: 'Настроить общение', exact: true}).click();
    await page.getByRole('combobox', {name: 'Стиль общения', exact: true}).click();
    const offsets = await page.getByRole('option').evaluateAll((options) =>
        options.flatMap((option) => {
            const bounds = option.getBoundingClientRect();
            return Array.from(option.querySelectorAll('[data-qa="style-emoji"], [data-qa="style-name"]')).map(
                (child) => {
                    const rect = child.getBoundingClientRect();
                    return Math.abs(rect.top + rect.height / 2 - bounds.top - bounds.height / 2);
                },
            );
        }),
    );
    expect(offsets.length).toBeGreaterThan(4);
    offsets.forEach((offset) => expect(offset).toBeLessThanOrEqual(1));
    await page.screenshot({path: '../docs/resources/communication-styles-dark.png', animations: 'disabled'});
});

test('Electron title bar uses its isolated bridge and native drag region', async ({page}) => {
    await page.addInitScript(() => {
        const actions: string[] = [];
        let maximized = false;
        let changed: ((state: {available: boolean; maximized: boolean}) => void) | undefined;
        Object.assign(window, {
            testWindowActions: actions,
            boltunyaDesktop: {
                state: async () => ({available: true, maximized}),
                action: async (action: string) => {
                    actions.push(action);
                    if (action === 'maximize') maximized = !maximized;
                    changed?.({available: true, maximized});
                },
                onState: (listener: typeof changed) => {
                    changed = listener;
                    return () => {
                        changed = undefined;
                    };
                },
                sync: async () => {},
                quit: async () => {},
            },
        });
    });
    await page.reload();
    const bar = page.locator('[data-qa="window-bar"]');
    expect((await bar.boundingBox())?.height).toBe(36);
    expect(
        await bar.evaluate((element) => getComputedStyle(element).getPropertyValue('-webkit-app-region')),
    ).toBe('drag');
    await bar.getByRole('button', {name: 'Свернуть', exact: true}).click();
    await bar.getByRole('button', {name: 'Развернуть', exact: true}).click();
    await bar.getByRole('button', {name: 'Восстановить размер', exact: true}).click();
    await bar.getByRole('button', {name: 'Закрыть окно', exact: true}).click();
    expect(
        await page.evaluate(() => (window as unknown as {testWindowActions: string[]}).testWindowActions),
    ).toEqual(['minimize', 'maximize', 'maximize', 'close']);
});

test('language settings include SVG flags and persist Kazakh and Ukrainian', async ({page}) => {
    for (const item of [
        {
            profile: 'Мой профиль',
            settings: 'Настройки',
            menu: 'Язык',
            label: 'Қазақша',
            search: 'Сұхбаттарды іздеу',
            id: 'kk',
        },
        {
            profile: 'Менің профилім',
            settings: 'Баптаулар',
            menu: 'Тіл',
            label: 'Українська',
            search: 'Пошук діалогів',
            id: 'uk',
        },
        {
            profile: 'Мій профіль',
            settings: 'Налаштування',
            menu: 'Мова',
            label: 'Русский',
            search: 'Поиск по диалогам',
            id: 'ru',
        },
    ]) {
        await page.getByRole('button', {name: item.profile, exact: true}).click();
        await page.getByRole('menuitem', {name: item.settings, exact: true}).click();
        await page.getByRole('dialog').getByRole('menuitem', {name: item.menu, exact: true}).click();
        const language = page.getByRole('radio', {name: item.label, exact: true});
        await expect(page.locator(`label:has(input[value="${item.id}"]) img`)).toHaveAttribute(
            'src',
            `/assets/flags/${item.id}.svg`,
        );
        await language.click();
        await page.keyboard.press('Escape');
        await expect(page.getByRole('textbox', {name: item.search})).toBeVisible();
        await page.reload();
        await expect(page.getByRole('textbox', {name: item.search})).toBeVisible();
    }
});

test('named writing styles can be saved and selected for a conversation', async ({page}) => {
    await page.getByRole('button', {name: 'Мой профиль', exact: true}).click();
    await page.getByRole('menuitem', {name: 'Настройки', exact: true}).click();
    await chooseSettingsSection(page, 'styles');
    const dialog = page.getByRole('dialog');
    await expect.poll(async () => Math.round((await dialog.boundingBox())!.width)).toBe(960);
    await page.getByRole('button', {name: 'Создать стиль', exact: true}).click();
    await page.getByLabel('Название', {exact: true}).fill('Спокойный');
    await page.getByRole('button', {name: 'Выбрать эмодзи', exact: true}).click();
    const picker = page.getByRole('dialog', {name: 'Выбрать эмодзи', exact: true});
    expect((await picker.boundingBox())?.width).toBeLessThan(250);
    const field = await page.getByLabel('Название', {exact: true}).boundingBox();
    const emoji = await page.getByRole('button', {name: 'Выбрать эмодзи', exact: true}).boundingBox();
    expect(emoji && field && Math.abs(emoji.y + emoji.height / 2 - field.y - field.height / 2)).toBeLessThan(
        2,
    );
    await page.getByRole('button', {name: '🧊', exact: true}).click();
    await page.getByLabel('Как общаться', {exact: true}).fill('Коротко, спокойно и на ты. Без смайлов.');
    await page.screenshot({path: '../docs/resources/style-editor.png', animations: 'disabled'});
    const saved = page.waitForRequest(
        (request) => request.url().endsWith('/api/settings') && request.method() === 'PATCH',
    );
    await page.getByRole('button', {name: 'Сохранить настройки', exact: true}).click();
    const preset = (await saved).postDataJSON().customStyles[0];
    expect(preset).toMatchObject({
        name: 'Спокойный',
        emoji: '🧊',
        instructions: 'Коротко, спокойно и на ты. Без смайлов.',
    });
    await expect(dialog.getByRole('status')).toHaveText('Сохранено');
    await expect(dialog.getByRole('button', {name: 'Сохранено', exact: true})).toBeVisible();
    await dialog.getByRole('button', {name: 'Отмена', exact: true}).click();
    await expect(dialog).toHaveCount(0);
    await page.getByRole('button', {name: 'Настроить общение', exact: true}).click();
    await page.getByRole('combobox', {name: 'Стиль общения', exact: true}).click();
    await expect(page.getByRole('option', {name: 'Свой стиль', exact: true})).toHaveCount(0);
    await page.getByRole('option', {name: 'Спокойный'}).click();
    const rule = page.waitForRequest(
        (request) => request.url().endsWith('/api/chats/41/rule') && request.method() === 'PUT',
    );
    await page.getByRole('button', {name: 'Сохранить настройки', exact: true}).click();
    expect((await rule).postDataJSON()).toMatchObject({style: 'custom', styleId: preset.id});
    await page.keyboard.press('Escape');
    await page.getByRole('button', {name: 'Настроить общение', exact: true}).click();
    await expect(page.getByRole('combobox', {name: 'Стиль общения', exact: true})).toContainText('Спокойный');
    await page.keyboard.press('Escape');
    await page.getByRole('button', {name: 'Параметры общения', exact: true}).click();
    await expect(
        page.locator('[data-qa="conversation-settings"]').getByText('Спокойный', {exact: true}),
    ).toBeVisible();
});

test('legacy unnamed custom selections retain instructions and show a concrete style', async ({page}) => {
    const state = structuredClone(workspaceState);
    const chat = state.chats[0];
    chat.rule = {...chat.rule, style: 'custom', styleId: '', instructions: 'Без длинных ответов.'};
    await page.route('**/api/state', (route) => route.fulfill({json: state}));
    await page.reload();
    await page.getByRole('button', {name: 'Аня Смирнова', exact: true}).click();
    await page.getByRole('button', {name: 'Настроить общение', exact: true}).click();
    await expect(page.getByRole('combobox', {name: 'Стиль общения', exact: true})).toContainText(
        'Дружелюбный',
    );
    await expect(page.getByRole('textbox', {name: 'Личные инструкции'})).toHaveValue('Без длинных ответов.');
    const saved = page.waitForRequest(
        (request) => request.method() === 'PUT' && request.url().endsWith(`/api/chats/${chat.id}/rule`),
    );
    await page.getByRole('button', {name: 'Сохранить настройки', exact: true}).click();
    expect((await saved).postDataJSON()).toMatchObject({
        style: 'friendly',
        styleId: '',
        instructions: 'Без длинных ответов.',
    });
});

test('history loads automatically above the viewport and preserves the visible message', async ({page}) => {
    await page.emulateMedia({reducedMotion: 'reduce'});
    const base = Date.UTC(2026, 9, 15, 10) / 1000;
    const history = (start: number, end: number) =>
        Array.from({length: end - start}, (_, index) => ({
            id: String(start + index),
            chatId: '41',
            text: `Сообщение ${start + index}: длинная реплика для проверки прокрутки`,
            outgoing: index % 2 === 0,
            date: base + (start + index) * 60,
            kind: 'text',
        }));
    let release: (() => void) | undefined;
    await page.route('**/api/chats/41/messages*', async (route) => {
        if (new URL(route.request().url()).searchParams.has('before')) {
            await new Promise<void>((resolve) => {
                release = resolve;
            });
            await route.fulfill({json: history(50, 100)});
        } else await route.fulfill({json: history(100, 150)});
    });
    await page.reload();
    await page.getByRole('button', {name: 'Аня Смирнова', exact: true}).click();
    const viewport = page.locator('[data-qa="conversation-messages"]');
    await expect(page.locator('[data-message-id="149"]')).toBeVisible();
    await viewport.evaluate((element) => {
        element.scrollTop = 40;
    });
    await expect(page.getByRole('status', {name: 'Загрузка переписки'})).toBeVisible();
    const before = await page.locator('[data-message-id="100"]').boundingBox();
    release?.();
    await expect(page.locator('[data-message-id="99"]')).toBeAttached();
    const after = await page.locator('[data-message-id="100"]').boundingBox();
    expect(after?.y).toBeCloseTo(before?.y ?? 0, 0);
    await expect(page.getByRole('button', {name: 'Показать предыдущие сообщения'})).toHaveCount(0);
    expect(
        await page
            .locator('[data-qa="message-date"]')
            .first()
            .evaluate((element) => getComputedStyle(element).position),
    ).toBe('sticky');
});

test('interface text cannot be selected, messages can, and search focus has a two pixel border', async ({
    page,
}) => {
    expect(
        await page
            .locator('[data-qa="chat-name"]')
            .first()
            .evaluate((element) => getComputedStyle(element).userSelect),
    ).toBe('none');
    expect(
        await page
            .locator('[data-qa="message-incoming"]')
            .first()
            .evaluate((element) => getComputedStyle(element).userSelect),
    ).toBe('text');
    await page.getByRole('textbox', {name: 'Поиск по диалогам'}).focus();
    const border = await page
        .locator('[data-qa="chat-search"]')
        .evaluate((element) => getComputedStyle(element).borderLeftWidth);
    expect(border).toBe('2px');
});

test('tray surface uses Gravity menu actions without an outer border', async ({page}) => {
    const actions: string[] = [];
    await page.exposeFunction('testTrayAction', (action: string) => actions.push(action));
    await page.addInitScript(() =>
        Object.assign(window, {
            boltunyaTray: {
                action: (window as unknown as {testTrayAction: (action: string) => void}).testTrayAction,
            },
        }),
    );
    await page.goto(new URL('/tray/', page.url()).href);
    await page.setViewportSize({width: 264, height: 214});
    const menu = page.locator('[data-qa="tray-menu"]');
    await expect(menu).toBeVisible();
    expect(await menu.evaluate((element) => getComputedStyle(element).borderWidth)).toBe('0px');
    for (const name of ['Открыть', 'Включить ИИ', 'Настройки', 'Завершить']) {
        await page.getByRole('menuitem', {name, exact: true}).click();
    }
    await expect(page.getByRole('menuitem', {name: 'Завершить', exact: true})).toBeInViewport();
    await page.screenshot({path: '../docs/resources/tray.png', animations: 'disabled'});
    await expect.poll(() => actions).toEqual(['show', 'pause', 'settings', 'quit']);
});

test('download progress stays inside its model card and the models page scrolls to its end', async ({
    page,
}) => {
    const state = structuredClone(workspaceState);
    state.engine.models[0].installed = false;
    state.engine.downloading = state.engine.models[0].id;
    state.engine.progress = 43;
    await page.route('**/api/state', (route) => route.fulfill({json: state}));
    await page.reload();
    await page.locator('[data-qa="nav-models"]').click();
    await expect(page.getByText('Локальные модели', {exact: true})).toHaveCount(0);
    const table = page.getByRole('table', {name: state.engine.models[0].name});
    const card = table.locator('..');
    await expect(card.getByRole('progressbar', {name: 'Прогресс загрузки'})).toHaveAttribute(
        'aria-valuenow',
        '43',
    );
    const download = card.getByRole('button', {name: 'Отменить загрузку', exact: true});
    await expect(download).toContainText('43%');
    await expect(download.getByText('43%', {exact: true})).toHaveCSS('opacity', '1');
    await download.scrollIntoViewIfNeeded();
    await page.screenshot({path: '../docs/resources/model-download.png', animations: 'disabled'});
    await download.hover();
    await expect(download.getByText('43%', {exact: true})).toHaveCSS('opacity', '0');
    await expect(download.getByText('Отменить', {exact: true})).toHaveCSS('opacity', '1');
    state.engine.progress = 78;
    await expect(card.getByRole('progressbar', {name: 'Прогресс загрузки'})).toHaveAttribute(
        'aria-valuenow',
        '78',
    );
    await page.setViewportSize({width: 900, height: 580});
    const footer = page.getByText('Скорость измеряется на вашем компьютере и сохраняется');
    await footer.scrollIntoViewIfNeeded();
    await expect(footer).toBeInViewport();
    const bounds = await footer.boundingBox();
    expect((bounds?.y ?? 0) + (bounds?.height ?? 0)).toBeLessThanOrEqual(580);
    await page.route('**/api/models/cancel', async (route) => {
        state.engine.downloading = '';
        await route.fulfill({json: {ok: true}});
    });
    await download.click();
    await expect(card.getByRole('progressbar')).toHaveCount(0);
    await expect(card.getByRole('button', {name: 'Скачать модель', exact: true})).toBeVisible();
});

test('Ollama actions distinguish missing installed and downloading runtimes without resizing the button', async ({
    page,
}) => {
    const state = structuredClone(workspaceState);
    state.engine.online = false;
    state.engine.installed = false;
    await page.route('**/api/state', (route) => route.fulfill({json: state}));
    await page.reload();
    await page.locator('[data-qa="nav-models"]').click();
    const engine = page.locator('[data-qa="engine-card"]');
    await expect(engine.getByText('Ollama не установлена', {exact: true})).toBeVisible();
    await expect(engine.getByRole('button', {name: 'Запустить Ollama', exact: true})).toHaveCount(0);
    const install = engine.getByRole('button', {name: 'Установить Ollama', exact: true});
    const initial = await install.boundingBox();
    state.engine.downloading = 'Ollama';
    state.engine.progress = 37;
    const progress = engine.getByRole('button', {name: 'Отменить загрузку', exact: true});
    await expect(progress).toBeVisible();
    await expect(engine.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '37');
    const downloading = await progress.boundingBox();
    const engineBounds = await engine.boundingBox();
    expect(downloading!.x + downloading!.width).toBeLessThanOrEqual(
        engineBounds!.x + engineBounds!.width - 16,
    );
    expect(Math.abs(initial!.width - downloading!.width)).toBeLessThanOrEqual(1);
    expect(initial!.height).toBeCloseTo(downloading!.height, 2);
    await page.screenshot({path: '../docs/resources/ollama-download.png', animations: 'disabled'});
    await page.setViewportSize({width: 900, height: 580});
    expect((await progress.boundingBox())!.width).toBeLessThanOrEqual(176);
    await progress.hover();
    await expect(progress.getByText('Отменить', {exact: true})).toHaveCSS('opacity', '1');
    await page.route('**/api/models/cancel', async (route) => {
        state.engine.downloading = '';
        await route.fulfill({json: {ok: true}});
    });
    await progress.click();
    await expect(install).toBeVisible();
    state.engine.installed = true;
    await expect(engine.getByRole('button', {name: 'Запустить Ollama', exact: true})).toBeVisible();
    await expect(install).toHaveCount(0);
    await page.route('**/api/engine/start', async (route) => {
        state.engine.online = true;
        await route.fulfill({json: {ok: true}});
    });
    await engine.getByRole('button', {name: 'Запустить Ollama', exact: true}).click();
    await expect(engine.getByText('Ollama работает', {exact: true})).toBeVisible();
    await expect(engine.getByRole('button', {name: 'Запустить Ollama', exact: true})).toHaveCount(0);
});

test('downloaded models show visible warmup text instead of a selected loading button', async ({page}) => {
    const state = structuredClone(workspaceState);
    state.engine.warming = state.settings.defaultModel;
    await page.route('**/api/state', (route) => route.fulfill({json: state}));
    await page.reload();
    await page.locator('[data-qa="nav-models"]').click();
    const card = page.locator(`[data-model-id="${state.settings.defaultModel}"]`);
    const button = card.getByRole('button', {name: 'Прогревается', exact: true});
    await expect(button).toBeVisible();
    await expect(button.getByText('Прогревается', {exact: true})).toBeVisible();
    await expect(button).toHaveAttribute('aria-busy', 'true');
    const opacity = await button
        .getByText('Прогревается', {exact: true})
        .evaluate((element) => getComputedStyle(element).opacity);
    expect(opacity).toBe('1');
    state.engine.warming = '';
    await expect(card.getByRole('button', {name: 'Выбрана', exact: true})).toBeVisible();
});

test('empty activity is centered and uses the generated illustration', async ({page}) => {
    await page.emulateMedia({reducedMotion: 'reduce'});
    await page.locator('[data-qa="nav-activity"]').click();
    const empty = page.locator('[data-qa="empty-activity"]');
    await expect(empty.getByText('Пока нет событий', {exact: true})).toBeVisible();
    const image = empty.locator('img[src="/assets/illustrations/activity.png"]');
    await expect
        .poll(() => image.evaluate((element) => (element as HTMLImageElement).naturalWidth))
        .toBeGreaterThan(0);
    await expect
        .poll(async () => {
            const center = await empty.boundingBox();
            return (center?.y ?? 0) + (center?.height ?? 0) / 2;
        })
        .toBeCloseTo(468, 0);
    await expect(page.getByText('Что происходит в диалогах', {exact: true})).toHaveCount(0);
    await page.screenshot({path: '../docs/resources/activity.png', animations: 'disabled'});
});

test('voice messages use Telegram waveforms with working playback controls', async ({page}) => {
    const samples = Buffer.alloc(64000);
    const header = Buffer.alloc(44);
    header.write('RIFF');
    header.writeUInt32LE(36 + samples.length, 4);
    header.write('WAVE', 8);
    header.write('fmt ', 12);
    header.writeUInt32LE(16, 16);
    header.writeUInt16LE(1, 20);
    header.writeUInt16LE(1, 22);
    header.writeUInt32LE(8000, 24);
    header.writeUInt32LE(16000, 28);
    header.writeUInt16LE(2, 32);
    header.writeUInt16LE(16, 34);
    header.write('data', 36);
    header.writeUInt32LE(samples.length, 40);
    await page.route('**/api/media/94', (route) =>
        route.fulfill({body: Buffer.concat([header, samples]), contentType: 'audio/wav'}),
    );
    await page.route('**/api/chats/41/messages*', (route) =>
        route.fulfill({
            json: [
                {
                    id: '94',
                    chatId: '41',
                    text: '',
                    outgoing: false,
                    date: 1792064100,
                    kind: 'voice',
                    media: {
                        fileId: 94,
                        mimeType: 'audio/wav',
                        fileName: 'voice.wav',
                        duration: 4,
                        waveform: [255, 255, 255, 255, 255],
                    },
                },
            ],
        }),
    );
    await page.reload();
    await page.getByRole('button', {name: 'Аня Смирнова', exact: true}).click();
    const seek = page.getByRole('slider', {name: 'Позиция воспроизведения', exact: true});
    await expect(seek).toHaveAttribute('aria-valuemax', '4');
    await page.getByRole('button', {name: 'Воспроизвести аудио', exact: true}).click();
    await page.getByRole('button', {name: 'Приостановить аудио', exact: true}).click();
    expect(await page.locator('audio').evaluate((element) => (element as HTMLAudioElement).paused)).toBe(
        true,
    );
});

test('tray settings remain accessible before Telegram sign-in', async ({page}) => {
    await page.route('**/api/state', (route) =>
        route.fulfill({json: {...workspaceState, telegram: {state: 'disconnected'}, chats: []}}),
    );
    await page.reload();
    await expect(page.getByRole('heading', {name: 'Aigram', exact: true})).toBeVisible();
    await page.evaluate(() => window.dispatchEvent(new Event('boltun-settings')));
    await expect(page.getByRole('dialog', {name: 'Настройки', exact: true})).toBeVisible();
    await page.getByRole('button', {name: 'Отмена', exact: true}).click();
    await expect(page.getByRole('button', {name: 'Подключить Telegram', exact: true})).toBeVisible();
});

test('AI replies are marked locally and media bubbles fill their photo width', async ({page}) => {
    const history = testHistory('41');
    history[1].automated = true;
    await page.route('**/api/chats/41/messages*', (route) => route.fulfill({json: history}));
    await page.reload();
    await page.getByRole('button', {name: 'Аня Смирнова', exact: true}).click();
    await expect(page.locator('[data-qa="message-ai"]')).toHaveCount(1);
    await expect(page.locator('[data-qa="message-ai"]')).toHaveText('ИИ');
    await expect(page.locator('[data-qa="chat-checkbox"]')).toHaveCount(0);
});

test('resource charts render real API readings and tolerate unavailable GPU', async ({page}) => {
    const sample = {
        timestamp: Date.now(),
        cpuPercent: 27,
        appCpuPercent: 5,
        memoryUsedBytes: 8589934592,
        memoryTotalBytes: 17179869184,
        appMemoryBytes: 536870912,
        backendMemoryBytes: 268435456,
        ollamaMemoryBytes: 1073741824,
        networkReceiveBytesPerSecond: 1048576,
        networkSendBytesPerSecond: 524288,
        gpuPercent: null,
        gpuMemoryUsedBytes: null,
        gpuMemoryTotalBytes: null,
    };
    await page.route('**/api/metrics', (route) => route.fulfill({json: [sample]}));
    await page.locator('[data-qa="nav-metrics"]').click();
    await expect(page.getByText('27.0%', {exact: true})).toBeVisible();
    await expect(page.getByText('Нет данных', {exact: true}).first()).toBeVisible();
    await expect(page.getByText('Сеть · MB/s', {exact: true})).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
    await page.screenshot({path: '../docs/resources/metrics.png', animations: 'disabled'});
});

test('group settings support per-chat mention mode and disabled global default', async ({page}) => {
    const state = structuredClone(workspaceState);
    state.chats[0].kind = 'group';
    state.chats[0].rule.groupReplies = 'inherit';
    state.settings.groupReplies = 'disabled';
    await page.route('**/api/state', (route) => route.fulfill({json: state}));
    await page.reload();
    await page.getByRole('button', {name: 'Аня Смирнова', exact: true}).click();
    await page.getByRole('button', {name: 'Настроить общение', exact: true}).click();
    await page.getByRole('combobox', {name: 'Ответы в группах', exact: true}).click();
    await page.getByRole('option', {name: 'Только при упоминании', exact: true}).click();
    const request = page.waitForRequest(
        (request) => request.method() === 'PUT' && request.url().endsWith('/api/chats/41/rule'),
    );
    await page.getByRole('button', {name: 'Сохранить настройки', exact: true}).click();
    expect((await request).postDataJSON().groupReplies).toBe('mentions');
});

test('modal actions stay visible while the form scrolls in a short window', async ({page}) => {
    await page.setViewportSize({width: 1000, height: 580});
    await page.getByRole('button', {name: 'Настроить общение', exact: true}).click();
    const dialog = page.getByRole('dialog');
    const body = dialog.locator('.g-dialog-body');
    const save = dialog.getByRole('button', {name: 'Сохранить настройки', exact: true});
    await expect(save).toBeInViewport();
    await dialog.evaluate((element) =>
        Promise.allSettled(element.getAnimations({subtree: true}).map((animation) => animation.finished)),
    );
    const before = await save.boundingBox();
    await body.evaluate((element) => (element.scrollTop = element.scrollHeight));
    const after = await save.boundingBox();
    expect(after?.y).toBeCloseTo(before?.y ?? 0, 1);
    expect(await body.evaluate((element) => element.scrollTop)).toBeGreaterThan(0);
    await expect(dialog.getByRole('button', {name: 'Отмена', exact: true})).toBeInViewport();
});

test('memory dialog edits notes queues a deep rebuild and preserves its footer', async ({page}) => {
    let document = {
        chatId: '41',
        notes: '# Память',
        summary: 'Мы дружим со школы. Любит чай. [id 12]',
        revision: 'first',
        updatedAt: 1000,
        processedMessages: 730,
        backfillComplete: false,
        status: 'queued',
        error: '',
        trackedChats: 1,
        relativePath: 'memory/100/41/MEMORY.md',
        collection: {active: false, status: 'idle', queuedChats: 0, queuePosition: 0, processedMessages: 0},
    };
    await page.route('**/api/memory/41', async (route) => {
        const request = route.request();
        if (request.method() === 'PATCH')
            document = {...document, notes: request.postDataJSON().notes, revision: 'second'};
        if (request.method() === 'POST' && request.postDataJSON().rebuild)
            document = {...document, summary: '', processedMessages: 0};
        await route.fulfill({json: document});
    });
    await page.getByRole('button', {name: 'Память диалога', exact: true}).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByText(document.summary, {exact: true})).toBeVisible();
    await page.getByRole('textbox', {name: 'Ваши заметки', exact: true}).fill('Не обращаться на вы.');
    const edit = page.waitForRequest(
        (request) => request.method() === 'PATCH' && request.url().endsWith('/api/memory/41'),
    );
    await page.getByRole('button', {name: 'Сохранить заметки', exact: true}).click();
    expect((await edit).postDataJSON()).toEqual({notes: 'Не обращаться на вы.', revision: 'first'});
    const rebuild = page.waitForRequest(
        (request) => request.method() === 'POST' && request.url().endsWith('/api/memory/41'),
    );
    await page.getByRole('button', {name: 'Пересобрать сводку', exact: true}).click();
    expect((await rebuild).postDataJSON()).toEqual({rebuild: true, immediate: false});
    await expect(page.getByRole('textbox', {name: 'Ваши заметки', exact: true})).toHaveValue(
        'Не обращаться на вы.',
    );
    await expect(page.getByText('Сводка ещё не собрана.', {exact: false})).toBeVisible();
    await page.screenshot({path: '../docs/resources/memory.png', animations: 'disabled'});
});

test('global memory and its background interval are accessible from profile settings', async ({page}) => {
    const document = {
        chatId: '',
        notes: '# Общая память',
        summary: 'Пишет коротко. Любит чай.',
        revision: 'first',
        updatedAt: 0,
        processedMessages: 0,
        backfillComplete: false,
        status: 'idle',
        error: '',
        trackedChats: 2,
        relativePath: 'memory/MEMORY.md',
    };
    await page.route('**/api/memory', async (route) => {
        if (route.request().method() === 'PATCH') document.notes = route.request().postDataJSON().notes;
        await route.fulfill({json: document});
    });
    await page.getByRole('button', {name: 'Мой профиль', exact: true}).click();
    await page.getByRole('menuitem', {name: 'Настройки', exact: true}).click();
    await chooseSettingsSection(page, 'memory');
    await expect(page.getByText('Диалогов в памяти: 2')).toBeVisible();
    await expect(page.getByText('Пишет коротко. Любит чай.', {exact: true})).toBeVisible();
    await page
        .getByRole('textbox', {name: 'Ваши заметки', exact: true})
        .fill('Предпочитаю короткие сообщения.');
    await page.getByRole('button', {name: 'Сохранить заметки', exact: true}).click();
    await chooseSettingsSection(page, 'collection');
    await page.getByRole('combobox', {name: 'Частота обновления памяти', exact: true}).click();
    await page.getByRole('option', {name: 'Каждые 12 ч.', exact: true}).click();
    const patch = page.waitForRequest(
        (request) => request.method() === 'PATCH' && request.url().endsWith('/api/settings'),
    );
    await page.getByRole('button', {name: 'Сохранить настройки', exact: true}).click();
    expect((await patch).postDataJSON()).toMatchObject({memoryEnabled: true, memoryIntervalHours: 12});
});

test('conversation header shows a real username or participant count below the title', async ({page}) => {
    const state = structuredClone(workspaceState);
    state.chats[0].username = 'anya_smirnova';
    await page.route('**/api/state', (route) => route.fulfill({json: state}));
    const header = page.locator('[data-qa="conversation-header"]');
    await expect(header.getByText('@anya_smirnova', {exact: true})).toBeVisible();
    const title = await header.getByText(state.chats[0].title, {exact: true}).boundingBox();
    const subtitle = header.locator('[data-qa="chat-subtitle"]');
    const tag = await subtitle.boundingBox();
    expect(tag && title && tag.y >= title.y + title.height).toBe(true);
    expect(tag && title ? tag.y - title.y - title.height : 10).toBeLessThanOrEqual(1);
    expect(
        await subtitle.evaluate((element) => parseFloat(getComputedStyle(element).fontSize)),
    ).toBeGreaterThanOrEqual(14);
    state.chats[0].kind = 'supergroup';
    state.chats[0].memberCount = 123;
    await expect(header.getByText('123 участника', {exact: true})).toBeVisible();
    await expect(header.getByText('@anya_smirnova', {exact: true})).toHaveCount(0);
    state.chats[0].kind = 'private';
    state.chats[0].username = '';
    await expect(subtitle).toHaveCount(0);
});

test('context collection opens from communication settings and preserves unsaved changes', async ({page}) => {
    let document = {
        chatId: '41',
        notes: 'Ручные заметки',
        summary: 'Существующий контекст',
        revision: 'first',
        updatedAt: 0,
        processedMessages: 240,
        backfillComplete: true,
        status: 'ready',
        error: '',
        trackedChats: 1,
        relativePath: 'memory/100/41/MEMORY.md',
        collection: {active: false, status: 'idle', queuedChats: 0, queuePosition: 0, processedMessages: 0},
    };
    await page.route('**/api/memory/41', async (route) => {
        if (route.request().method() === 'POST')
            document = {
                ...document,
                status: 'queued',
                collection: {
                    active: true,
                    status: 'queued',
                    queuedChats: 1,
                    queuePosition: 1,
                    processedMessages: 40,
                },
            };
        await route.fulfill({json: document});
    });
    await page.route('**/api/memory/41/cancel', (route) => {
        document = {...document, status: 'ready', collection: {...document.collection, active: false}};
        return route.fulfill({json: document});
    });
    const header = page.locator('[data-qa="conversation-header"]');
    await expect(header.getByRole('button', {name: 'Собрать контекст', exact: true})).toHaveCount(0);
    await page.getByRole('button', {name: 'Параметры общения', exact: true}).click();
    const panel = page.locator('[data-qa="conversation-settings"]');
    await panel.getByRole('button', {name: 'Собрать контекст', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Собрать контекст', exact: true});
    await expect(dialog.getByText('Просмотрено сообщений: 240', {exact: true})).toBeVisible();
    await dialog.locator('.g-dialog-footer').getByRole('button', {name: 'Закрыть', exact: true}).click();
    await expect(panel).toBeVisible();
    await panel.getByRole('button', {name: 'Настроить общение', exact: true}).click();
    const settings = page.getByRole('dialog', {name: 'Общение с Аня Смирнова', exact: true});
    await settings
        .getByRole('textbox', {name: 'Личные инструкции', exact: true})
        .fill('Несохранённая правка');
    const open = settings.getByRole('button', {name: 'Собрать контекст', exact: true});
    await open.click();
    await expect(dialog.getByText('Просмотрено сообщений: 240', {exact: true})).toBeVisible();
    const incremental = page.waitForRequest(
        (request) => request.method() === 'POST' && request.url().endsWith('/api/memory/41'),
    );
    await dialog.getByRole('button', {name: 'Начать сбор', exact: true}).click();
    expect((await incremental).postDataJSON()).toEqual({rebuild: false, immediate: true});
    await expect(dialog.getByText('В очереди', {exact: true})).toBeVisible();
    await page.setViewportSize({width: 1000, height: 640});
    await expect(dialog.getByRole('button', {name: 'Отменить сбор', exact: true})).toBeInViewport();
    await page.screenshot({path: '../docs/resources/context-collection.png', animations: 'disabled'});
    await dialog.locator('.g-dialog-footer').getByRole('button', {name: 'Закрыть', exact: true}).click();
    await expect(settings).toBeVisible();
    await expect(settings.getByRole('textbox', {name: 'Личные инструкции', exact: true})).toHaveValue(
        'Несохранённая правка',
    );
    await expect(settings.locator('[data-qa="context-progress"]')).toBeVisible();
    await expect(settings.getByText('Просмотрено сообщений: 40', {exact: true})).toBeVisible();
    await settings.getByRole('button', {name: 'Отменить сбор', exact: true}).click();
    await expect(open).toBeVisible();
    await open.click();
    await dialog.getByRole('radio', {name: 'Вся доступная история'}).click();
    const full = page.waitForRequest(
        (request) => request.method() === 'POST' && request.url().endsWith('/api/memory/41'),
    );
    await dialog.getByRole('button', {name: 'Начать сбор', exact: true}).click();
    expect((await full).postDataJSON()).toEqual({rebuild: true, immediate: true});
});

test('download preferences save independently and cache cleanup displays returned remaining size', async ({
    page,
}) => {
    let stats = {
        totalBytes: 10 * 1024 * 1024,
        fileCount: 2,
        photoBytes: 4 * 1024 * 1024,
        videoBytes: 6 * 1024 * 1024,
        audioBytes: 0,
        documentBytes: 0,
    };
    await page.route('**/api/cache', (route) => route.fulfill({json: stats}));
    await page.route('**/api/cache/clear', (route) => {
        stats = {...stats, totalBytes: 0, fileCount: 0, photoBytes: 0, videoBytes: 0};
        return route.fulfill({json: stats});
    });
    await page.getByRole('button', {name: 'Мой профиль', exact: true}).click();
    await page.getByRole('menuitem', {name: 'Настройки', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Настройки', exact: true});
    await chooseSettingsSection(page, 'media');
    await dialog.locator('label.g-switch').filter({hasText: 'Фотографии'}).click();
    await dialog.locator('label.g-switch').filter({hasText: 'Видео и GIF'}).click();
    await dialog.locator('label.g-switch').filter({hasText: 'Документы и файлы'}).click();
    await dialog.getByRole('combobox', {name: 'Максимальный размер файла', exact: true}).click();
    await page.getByRole('option', {name: '5 МБ', exact: true}).click();
    await chooseSettingsSection(page, 'cache');
    await expect(dialog.getByText('10 МБ · файлов: 2', {exact: true})).toBeVisible();
    await dialog.getByRole('button', {name: 'Очистить медиакеш', exact: true}).click();
    await expect(dialog.getByText('0 МБ · файлов: 0', {exact: true})).toBeVisible();
    await chooseSettingsSection(page, 'media');
    const saved = page.waitForRequest(
        (request) => request.method() === 'PATCH' && request.url().endsWith('/api/settings'),
    );
    await dialog.getByRole('button', {name: 'Сохранить настройки', exact: true}).click();
    expect((await saved).postDataJSON()).toMatchObject({
        autoDownloadPhotos: false,
        autoDownloadVideos: true,
        autoDownloadFiles: true,
        autoDownloadMaxMb: 5,
    });
});

test('disabled image autoload makes no request until explicit download', async ({page}) => {
    const message = {
        id: '700',
        chatId: '41',
        text: 'Фото без автозагрузки',
        outgoing: false,
        date: 1792072800,
        kind: 'photo',
        media: {
            fileId: 700,
            mimeType: 'image/png',
            fileName: 'photo.png',
            width: 320,
            height: 240,
            thumbnailFileId: 0,
            size: 68,
            autoDownload: false,
            downloaded: false,
        },
    };
    let reads = 0,
        downloads = 0;
    await page.route('**/api/chats/41/messages*', (route) => route.fulfill({json: [message]}));
    await page.route('**/api/media/700', (route) => {
        reads++;
        return route.fulfill({
            contentType: 'image/png',
            body: Buffer.from(
                'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a4n0AAAAASUVORK5CYII=',
                'base64',
            ),
        });
    });
    await page.route('**/api/media/700/download', (route) => {
        downloads++;
        return route.fulfill({json: {ok: true}});
    });
    await expect(page.getByText('Фото без автозагрузки', {exact: true})).toBeVisible();
    expect(reads).toBe(0);
    expect(downloads).toBe(0);
    await page.getByRole('button', {name: 'Загрузить', exact: true}).click();
    await expect.poll(() => downloads).toBe(1);
    await expect.poll(() => reads).toBe(1);
});

test('deleted messages keep their content in the conversation and in the saved-message dialog', async ({
    page,
}) => {
    const message = {
        id: '800',
        chatId: '41',
        text: 'Этот текст удалили, но он сохранён',
        outgoing: false,
        date: 1792072800,
        kind: 'text',
        deleted: true,
        deletedAt: 1792072900,
        entities: [{offset: 0, length: 10, type: 'bold'}],
    };
    await page.route('**/api/chats/41/messages*', (route) => route.fulfill({json: [message]}));
    await page.route('**/api/chats/41/deleted*', (route) => route.fulfill({json: [message]}));
    const bubble = page.locator('[data-message-id="800"]');
    await expect(bubble).toHaveAttribute('data-message-deleted', 'true');
    await expect(bubble.getByText('Удалено', {exact: true})).toBeVisible();
    const header = page.locator('[data-qa="conversation-header"]');
    await expect(header.getByRole('button', {name: 'Удалённые сообщения', exact: true})).toHaveCount(0);
    await page.getByRole('button', {name: 'Параметры общения', exact: true}).click();
    const settings = page.locator('[data-qa="conversation-settings"]');
    await settings.getByRole('button', {name: 'Удалённые сообщения', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Удалённые сообщения', exact: true});
    await expect(dialog.locator('[data-message-id="800"]')).toBeVisible();
    await expect(dialog.getByText('Удалено', {exact: true})).toBeVisible();
    await expect.poll(async () => (await dialog.boundingBox())?.height ?? 900).toBeLessThan(600);
    await page.screenshot({path: '../docs/resources/deleted-messages.png', animations: 'disabled'});
    await dialog.locator('.g-dialog-footer').getByRole('button', {name: 'Закрыть', exact: true}).click();
    await expect(settings).toBeVisible();
    await page.screenshot({path: '../docs/resources/chat-settings-actions.png', animations: 'disabled'});
});

test('global collection can be started from profile with a complete rebuild', async ({page}) => {
    let document = {
        chatId: '',
        notes: 'Ручной профиль',
        summary: 'Любит чай',
        revision: '1',
        updatedAt: 0,
        processedMessages: 200,
        backfillComplete: true,
        status: 'ready',
        error: '',
        trackedChats: 2,
        relativePath: 'memory/MEMORY.md',
    };
    await page.route('**/api/memory', (route) => route.fulfill({json: document}));
    await page.route('**/api/memory/refresh', (route) => {
        document = {...document, status: 'queued', processedMessages: 0};
        return route.fulfill({json: {ok: true}});
    });
    await page.getByRole('button', {name: 'Мой профиль', exact: true}).click();
    await page.getByRole('menuitem', {name: 'Настройки', exact: true}).click();
    await chooseSettingsSection(page, 'collection');
    const dialog = page.getByRole('dialog', {name: 'Настройки', exact: true});
    await expect(dialog.getByText('Диалогов в памяти: 2')).toBeVisible();
    await dialog.getByRole('radio', {name: 'Вся доступная история'}).click();
    const queued = page.waitForRequest(
        (request) => request.method() === 'POST' && request.url().endsWith('/api/memory/refresh'),
    );
    await dialog.getByRole('button', {name: 'Начать сбор', exact: true}).click();
    expect((await queued).postDataJSON()).toEqual({rebuild: true, immediate: true});
    await expect(dialog.getByText('В очереди · Просмотрено сообщений: 0', {exact: true})).toBeVisible();
    expect(document.notes).toBe('Ручной профиль');
});

test('search key controls mask credentials and explain fallback without closing settings', async ({page}) => {
    await page.getByRole('button', {name: 'Мой профиль', exact: true}).click();
    await page.getByRole('menuitem', {name: 'Настройки', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Настройки', exact: true});
    await chooseSettingsSection(page, 'search');
    const panel = dialog.locator('[data-qa="search-connection"]');
    await expect(panel.getByText('Работает без ключей')).toBeVisible();
    await panel.getByRole('button', {name: 'Дополнительно: резервный поиск Exa', exact: true}).click();
    const key = panel.getByLabel('API-ключ Exa', {exact: true});
    await expect(key).toHaveAttribute('type', 'password');
    await key.fill('fake-exa-key-123');
    const saved = page.waitForRequest(
        (request) => request.method() === 'POST' && request.url().endsWith('/api/search/connection'),
    );
    await panel.getByRole('button', {name: 'Сохранить ключ', exact: true}).click();
    expect((await saved).postDataJSON()).toEqual({apiKey: 'fake-exa-key-123'});
    await expect(key).toHaveValue('');
    await expect(panel.getByText('Собственный ключ сохранён', {exact: true})).toBeVisible();
    await panel.getByRole('button', {name: 'Проверить подключение', exact: true}).click();
    await expect(panel.getByRole('status')).toContainText('Основной бесплатный поиск продолжает работать');
    await page.screenshot({path: '../docs/resources/internet-search-settings.png', animations: 'disabled'});
    await panel.getByRole('button', {name: 'Удалить ключ', exact: true}).click();
    await expect(
        panel.getByText('Exa не подключена. Для основного поиска ключ не нужен.', {exact: true}),
    ).toBeVisible();
});

test('a conversation saves its source link policy and restores it when reopened', async ({page}) => {
    await page.getByRole('button', {name: 'Настроить общение', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Общение с Аня Смирнова', exact: true});
    const mode = dialog.getByRole('combobox', {name: 'Ссылки в ответах', exact: true});
    await expect(mode).toContainText('Когда собеседник просит');
    await mode.click();
    await page.getByRole('option', {name: 'Не отправлять', exact: true}).click();
    const saved = page.waitForRequest(
        (request) => request.method() === 'PUT' && request.url().endsWith('/api/chats/41/rule'),
    );
    await dialog.getByRole('button', {name: 'Сохранить настройки', exact: true}).click();
    expect((await saved).postDataJSON().sourceLinks).toBe('never');
    await expect(dialog).toBeVisible();
    await page.keyboard.press('Escape');
    await page.getByRole('button', {name: 'Настроить общение', exact: true}).click();
    await expect(mode).toContainText('Не отправлять');
});

test('large dialog lists load on scroll, preserve filters and show message authors', async ({page}) => {
    const state = structuredClone(workspaceState);
    state.chats = Array.from({length: 125}, (_, index) => ({
        ...state.chats[0],
        id: String(500 + index),
        title: 'Контакт ' + index,
        preview: 'Сообщение ' + index,
        kind: index === 1 ? 'group' : 'private',
        lastMessageOutgoing: index === 0,
        lastMessageSender: index === 1 ? 'Анна' : '',
        rule: {...state.chats[0].rule, enabled: index < 2},
    }));
    await page.route('**/api/state', (route) => route.fulfill({json: state}));
    await expect(page.getByRole('button', {name: 'Контакт 0', exact: true})).toBeVisible();
    const rows = page.locator('[data-qa="chat-row"]');
    await expect(rows).toHaveCount(40);
    await expect(rows.first()).toContainText('Вы: Сообщение 0');
    await expect(rows.nth(1)).toContainText('Анна: Сообщение 1');
    for (const dark of [false, true]) {
        if (dark) await toggleTheme(page);
        await expect(rows.first().locator('[data-qa="chat-preview-author"]')).toHaveCSS(
            'color',
            dark ? 'rgb(231, 237, 243)' : 'rgb(39, 52, 65)',
        );
        await expect(rows.nth(1).locator('[data-qa="chat-preview-author"]')).toHaveCSS(
            'color',
            dark ? 'rgb(231, 237, 243)' : 'rgb(39, 52, 65)',
        );
        await expect(rows.first().locator('[data-qa="chat-preview-text"]')).toHaveCSS(
            'color',
            dark ? 'rgb(156, 170, 184)' : 'rgb(100, 115, 131)',
        );
        await expect(rows.nth(1).locator('[data-qa="chat-preview-text"]')).toHaveCSS(
            'color',
            dark ? 'rgb(156, 170, 184)' : 'rgb(100, 115, 131)',
        );
    }
    const viewport = page.locator('[data-qa="chat-list-scroll"]');
    await viewport.evaluate((el) => {
        el.scrollTop = el.scrollHeight;
    });
    await expect(rows).toHaveCount(80);
    await viewport.evaluate((el) => {
        el.scrollTop = el.scrollHeight;
    });
    await expect(rows).toHaveCount(120);
    await page.getByRole('tab', {name: 'С ИИ', exact: true}).click();
    await expect(rows).toHaveCount(2);
    await page.getByRole('tab', {name: 'Все', exact: true}).click();
    await expect(rows).toHaveCount(40);
    await expect.poll(() => viewport.evaluate((el) => el.scrollTop)).toBe(0);
    await page.getByRole('textbox', {name: 'Поиск по диалогам'}).fill('Контакт 124');
    await expect(rows).toHaveCount(1);
    await expect(rows).toContainText('Контакт 124');
});

test('username controls copy the tag and Telegram link', async ({page}) => {
    await page.evaluate(() => {
        Object.defineProperty(navigator, 'clipboard', {
            configurable: true,
            value: {
                writeText: async (value: string) => {
                    document.documentElement.dataset.copiedValue = value;
                },
            },
        });
    });
    const state = structuredClone(workspaceState);
    state.chats[0].username = 'anya_smirnova';
    await page.route('**/api/state', (route) => route.fulfill({json: state}));
    const header = page.locator('[data-qa="conversation-header"]');
    await header.getByRole('button', {name: 'Копировать тег', exact: true}).click();
    await expect(page.locator('html')).toHaveAttribute('data-copied-value', '@anya_smirnova');
    await header.getByRole('button', {name: 'Копировать ссылку Telegram', exact: true}).click();
    await expect(page.locator('html')).toHaveAttribute('data-copied-value', 'https://t.me/anya_smirnova');
});

for (const dark of [false, true]) {
    const theme = dark ? 'dark' : 'light';
    for (const terminal of ['cancelled', 'error'] as const) {
        test(`queued draft ${terminal} has one dismissible notice and can be retried in ${theme} theme`, async ({
            page,
        }) => {
            const state = structuredClone(workspaceState);
            const oldFailure = {
                ...testActivity[0],
                id: 'old-auto-failure',
                status: 'error',
                phase: 'error',
                date: 100,
                detail: 'Ошибка предыдущего автоответа.',
            };
            state.activity = [oldFailure];
            let starts = 0;
            let job = {
                id: '',
                status: 'queued',
                result: null,
                error: '',
            } as import('@aigram/api-client').PreviewJob;
            await page.route('**/api/state', (route) => route.fulfill({json: state}));
            await page.route('**/api/preview/jobs', async (route) => {
                starts += 1;
                expect(route.request().postDataJSON().chatId).toBe('41');
                job = {id: `draft-browser-${starts}`, status: 'queued', result: null, error: ''};
                state.activity = [
                    oldFailure,
                    {
                        ...testActivity[0],
                        id: job.id,
                        kind: 'draft',
                        status: 'queued',
                        phase: 'queued',
                        date: 100 + starts,
                        detail: 'qwen3:1.7b',
                    },
                ];
                await route.fulfill({json: job});
            });
            await page.route('**/api/preview/jobs/*', async (route) => {
                job.status = starts === 1 ? terminal : 'ready';
                job.error = job.status === 'error' ? 'Локальная модель не ответила.' : '';
                job.result =
                    job.status === 'ready'
                        ? {text: 'Проверенный новый черновик', durationMs: 100, tokensPerSecond: 12}
                        : null;
                state.activity[1] = {
                    ...state.activity[1],
                    status: job.status,
                    phase: job.status,
                    detail: job.error,
                };
                await route.fulfill({json: job});
            });
            if (dark) {
                await persistTestTheme(page, true);
                await page.reload();
                await page.getByRole('button', {name: 'Аня Смирнова', exact: true}).click();
            }
            const input = page.getByRole('textbox', {name: ru.messagePlaceholder, exact: true});
            await input.fill('Мой незавершённый текст');
            await expect(page.locator('[data-qa="conversation-generation-error"]')).toHaveCount(1);
            const prepare = page.getByRole('button', {name: ru.draft, exact: true});
            await prepare.click();
            const title = terminal === 'cancelled' ? ru.draftQueue.cancelled : ru.draftQueue.failed;
            const notice = page.getByRole(terminal === 'cancelled' ? 'status' : 'alert', {
                name: title,
                exact: true,
            });
            await expect(notice).toBeVisible();
            await expect(page.locator('[data-qa="conversation-generation-error"]')).toHaveCount(0);
            const contentAlerts = page.locator('[data-qa="workspace-view"]').getByRole('alert');
            await expect(contentAlerts).toHaveCount(terminal === 'error' ? 1 : 0);
            await expect(input).toHaveValue('Мой незавершённый текст');
            await expect(prepare).toBeEnabled();
            await page.screenshot({
                path: `../docs/resources/draft-${terminal}-${theme}.png`,
                animations: 'disabled',
            });
            await notice.getByRole('button').click();
            await expect(notice).toHaveCount(0);
            await page.waitForResponse((response) => new URL(response.url()).pathname === '/api/state');
            await expect(page.locator('[data-qa="conversation-generation-error"]')).toHaveCount(0);
            await expect(contentAlerts).toHaveCount(0);
            await prepare.click();
            await expect(input).toHaveValue('Проверенный новый черновик');
            await expect(notice).toHaveCount(0);
            await expect(page.locator('[data-qa="conversation-generation-error"]')).toHaveCount(0);
            expect(starts).toBe(2);
        });
    }
}
