import {initializeTestTheme, chooseSettingsSection} from '../fixtures/settings';
import {expect, test} from '@playwright/test';

import {workspaceState} from '../fixtures/workspace';
import {computeDevices} from '../fixtures/computeDevices';
import {historyPage} from '../fixtures/activityHistory';
import {syntheticPromptBudget} from '../fixtures/promptBudget';
import ru from '../../src/shared/i18n/ru.json';

test.beforeEach(async ({page}) => {
    await initializeTestTheme(page, false);
    const state = structuredClone(workspaceState);
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
        if (path === '/api/model-profiles') {
            await route.fulfill({json: {profiles: [], presets: []}});
        } else if (path === '/api/speech') {
            await route.fulfill({json: {assets: [], downloads: []}});
        } else if (path === '/api/activity/history') {
            await route.fulfill({json: historyPage(state.activity)});
        } else if (path === '/api/prompts/budget') {
            await route.fulfill({json: syntheticPromptBudget(route.request().postDataJSON())});
        } else if (path === '/api/state') {
            await route.fulfill({json: state});
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
    await page.getByRole('menuitem', {name: 'Настройки', exact: true}).click();
    await chooseSettingsSection(page, 'memory');
    const summary = page.locator('[data-qa="memory-summary"]');
    await expect(summary.getByRole('heading', {name: 'Обо мне'})).toBeVisible();
    await expect(summary.locator('strong')).toHaveText('чай');
    await expect(summary).not.toContainText('**');
    await page.screenshot({path: '../docs/resources/memory-formatted.png', animations: 'disabled'});
});

test('supported workspace pages have no error journal navigation or journal requests', async ({page}) => {
    const journalRequests: string[] = [];
    page.on('request', (request) => {
        const path = new URL(request.url()).pathname;
        if (path.startsWith('/api/diagnostics')) {
            journalRequests.push(request.method() + ' ' + path);
        }
    });
    await page.reload();
    const navigation = page.locator('[data-qa="sidebar-navigation"]');
    await expect(navigation.getByRole('button')).toHaveCount(4);
    await expect(page.locator('[data-qa="nav-errors"]')).toHaveCount(0);
    for (const [tab, content] of [
        ['models', '[data-qa="engine-card"]'],
        ['speech', '[data-qa="speech-library"]'],
        ['activity', '[data-qa="empty-activity"]'],
        ['chats', '[data-qa="chat-list-scroll"]'],
    ]) {
        await navigation.locator(`[data-qa="nav-${tab}"]`).click();
        await expect(page.locator(content)).toBeVisible();
    }
    await page.getByRole('button', {name: ru.profileMenu, exact: true}).click();
    await page.getByRole('menuitem', {name: ru.settings, exact: true}).click();
    await chooseSettingsSection(page, 'storage');
    await expect(page.locator('[data-qa="storage-locations"]')).toBeVisible();
    expect(journalRequests).toEqual([]);
});
