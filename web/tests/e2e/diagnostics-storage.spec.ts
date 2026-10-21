import {chooseSettingsSection} from '../fixtures/settings';
import {expect, test} from '@playwright/test';

import {workspaceState} from '../fixtures/workspace';
import {computeDevices} from '../fixtures/computeDevices';

const errors = [
    {
        id: 'engine-1',
        date: 1792509000,
        firstOccurredAt: 1792508900,
        source: 'engine',
        code: 'ENGINE_UNAVAILABLE',
        message: 'Не удалось подключиться к Ollama.',
        remediation: 'Запустите Ollama на странице моделей и повторите попытку.',
        exceptionType: 'EngineUnavailableException',
        occurrences: 2,
        metadata: {status: '503', stage: 'connect'},
    },
    {
        id: 'reply-1',
        date: 1792508000,
        firstOccurredAt: 1792508000,
        source: 'reply',
        code: 'MODEL_EMPTY_REPLY',
        message: 'Модель не вернула готовый ответ.',
        remediation: 'Проверьте установленную модель или выберите другую.',
        exceptionType: 'EmptyReplyException',
        occurrences: 1,
        metadata: {model: 'deepseek-r1:8b'},
    },
];

test.beforeEach(async ({page}) => {
    const state = structuredClone(workspaceState);
    let entries = [...errors];
    await page.addInitScript(() => {
        window.boltunyaDesktop = {
            state: async () => ({available: true, maximized: false, visible: true, minimized: false}),
            sync: async () => {},
            quit: async () => {},
            action: async () => {},
            onState: () => () => {},
            openStorageLocation: async (id) => {
                document.documentElement.dataset.openedStorage = id;
            },
        };
    });
    await page.route('**/api/**', async (route) => {
        const path = new URL(route.request().url()).pathname;
        if (path === '/api/appearance') {
            await route.fulfill({json: {mode: 'system', tokens: {}, css: ''}});
        } else if (path === '/api/model-profiles') {
            await route.fulfill({json: {profiles: [], presets: []}});
        } else if (path === '/api/state') {
            await route.fulfill({json: state});
        } else if (path === '/api/diagnostics/errors') {
            if (route.request().method() === 'DELETE') {
                entries = [];
            }
            await route.fulfill({
                json: route.request().method() === 'DELETE' ? {ok: true} : {errors: entries},
            });
        } else if (path === '/api/storage/locations') {
            await route.fulfill({
                json: {
                    locations: [
                        {
                            id: 'application',
                            label: '',
                            path: 'C:\\Users\\Example\\AppData\\Local\\Tiho',
                            available: true,
                            openable: true,
                            kind: 'directory',
                            note: '',
                        },
                        {
                            id: 'memory',
                            label: '',
                            path: 'C:\\Users\\Example\\AppData\\Local\\Tiho\\memory',
                            available: true,
                            openable: true,
                            kind: 'directory',
                            note: '',
                        },
                        {
                            id: 'ollama-models',
                            label: '',
                            path: '',
                            available: true,
                            openable: false,
                            kind: 'external',
                            note: 'Модели находятся на внешнем сервере Ollama.',
                        },
                    ],
                },
            });
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
        } else if (path === '/api/search/connection') {
            await route.fulfill({json: {configured: false, state: 'unchecked'}});
        } else if (path === '/api/settings') {
            Object.assign(state.settings, route.request().postDataJSON());
            await route.fulfill({json: {ok: true}});
        } else if (path === '/api/memory') {
            await route.fulfill({
                json: {
                    chatId: '',
                    notes: '',
                    summary:
                        '## Обо мне\n\n- Люблю **чай** и вечерние прогулки.\n- По выходным хожу в бассейн.\n\n## Договорённости\n\nВстречаемся с друзьями в субботу вечером.',
                    revision: '1',
                    updatedAt: 1792508000,
                    processedMessages: 1620,
                    backfillComplete: true,
                    status: 'ready',
                    error: '',
                    trackedChats: 8,
                    relativePath: 'memory/MEMORY.md',
                },
            });
        } else {
            await route.fulfill({status: 400, json: {message: 'Unexpected test request'}});
        }
    });
    await page.goto(process.env.TIHO_TEST_URL ?? 'http://127.0.0.1:3099');
});

test('errors appear as cards with distinct detail blocks and can be cleared', async ({page}) => {
    await page.getByRole('button', {name: 'Ошибки', exact: true}).click();
    const cards = page.locator('[data-qa="error-card"]');
    await expect(cards).toHaveCount(2);
    const first = await cards.nth(0).boundingBox();
    const second = await cards.nth(1).boundingBox();
    expect(first && second && Math.abs(first.y - second.y)).toBeLessThan(2);
    await page.screenshot({path: '../docs/resources/errors.png', animations: 'disabled'});
    await cards.first().click();
    const dialog = page.getByRole('dialog', {name: 'Подробности ошибки', exact: true});
    for (const title of ['Что случилось', 'Что можно сделать', 'Техническая информация']) {
        const block = dialog.getByText(title, {exact: true}).locator('..');
        await expect(block).toHaveCSS('border-radius', '14px');
        expect(await block.evaluate((element) => getComputedStyle(element).backgroundColor)).not.toBe(
            'rgba(0, 0, 0, 0)',
        );
    }
    await page.screenshot({path: '../docs/resources/error-details.png', animations: 'disabled'});
    await page.keyboard.press('Escape');
    await page.getByRole('button', {name: 'Очистить ошибки', exact: true}).click();
    await expect(page.getByText('Ошибок нет', {exact: true})).toBeVisible();
});

test('storage paths open through the allowlisted desktop bridge', async ({page}) => {
    await page.getByRole('button', {name: 'Мой профиль', exact: true}).click();
    await page.getByRole('menuitem', {name: 'Настройки', exact: true}).click();
    await chooseSettingsSection(page, 'storage');
    const panel = page.locator('[data-qa="storage-locations"]');
    await panel.scrollIntoViewIfNeeded();
    await panel.getByRole('button', {name: 'Открыть: Банк памяти', exact: true}).click();
    await expect(page.locator('html')).toHaveAttribute('data-opened-storage', 'memory');
    await expect(panel.getByRole('button', {name: 'Открыть: Модели Ollama', exact: true})).toBeDisabled();
    await page.screenshot({path: '../docs/resources/storage-locations.png', animations: 'disabled'});
});

test('generated memory renders Markdown without visible syntax markers', async ({page}) => {
    await page.getByRole('button', {name: 'Мой профиль', exact: true}).click();
    await page.getByRole('menuitem', {name: 'Память', exact: true}).click();
    const summary = page.locator('[data-qa="memory-summary"]');
    await expect(summary.getByRole('heading', {name: 'Обо мне'})).toBeVisible();
    await expect(summary.locator('strong')).toHaveText('чай');
    await expect(summary).not.toContainText('**');
    await page.screenshot({path: '../docs/resources/memory-formatted.png', animations: 'disabled'});
});
