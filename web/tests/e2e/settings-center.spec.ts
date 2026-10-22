import {test, expect} from '@playwright/test';
import {workspaceState} from '../fixtures/workspace';
import {modelProfiles, providerPreset} from '../fixtures/modelProfiles';
import {chooseSettingsSection} from '../fixtures/settings';
import {expectSettingsSlider, persistTestTheme} from '../fixtures/settings';
import {syntheticPromptBudget} from '../fixtures/promptBudget';
import {computeDevices} from '../fixtures/computeDevices';
import type * as Contract from '@aigram/api-client';
import {readFileSync} from 'node:fs';
import {join} from 'node:path';
import ru from '../../src/shared/i18n/ru.json';
import type {Locator} from '@playwright/test';
async function expectCompactMemoryActions(actions: Locator) {
    await expect(actions.getByRole('button')).toHaveCount(4);
    await expect
        .poll(async () =>
            Number((await actions.getByRole('button').first().boundingBox())!.height.toFixed(2)),
        )
        .toBe(32);
    await expect(actions).toHaveCSS('display', 'flex');
    await expect(actions).toHaveCSS('flex-wrap', 'wrap');
    await expect(actions).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)');
    const geometry = await actions.evaluate((toolbar) => {
        const bounds = toolbar.getBoundingClientRect();
        const parent = toolbar.parentElement!.getBoundingClientRect();
        return {
            width: bounds.width,
            parentWidth: parent.width,
            buttons: [...toolbar.querySelectorAll('button')].map((button) => {
                const rect = button.getBoundingClientRect();
                const text = button.querySelector('.g-button__text')!.getBoundingClientRect();
                return {
                    height: rect.height,
                    width: rect.width,
                    left: rect.left - bounds.left,
                    right: rect.right - bounds.right,
                    textOverflow: text.right - rect.right,
                    top: rect.top - bounds.top,
                };
            }),
        };
    });
    expect(geometry.width).toBeLessThanOrEqual(geometry.parentWidth);
    geometry.buttons.forEach((button) => {
        expect(button.height).toBe(32);
        expect(button.width).toBeLessThan(geometry.parentWidth - 16);
        expect(button.left).toBeGreaterThanOrEqual(-1);
        expect(button.right).toBeLessThanOrEqual(1);
        expect(button.textOverflow).toBeLessThanOrEqual(0);
    });
    return geometry;
}
test.beforeEach(async ({page}) => {
    const state = structuredClone(workspaceState);
    const profiles = structuredClone(modelProfiles);
    const prompts = new Map<string, Contract.ModelPromptSettings>();
    let routing = {
        enabled: false,
        parallelIndependentTasks: false,
        replyProfileId: '',
        memoryProfileId: '',
        visionProfileId: '',
    };
    await page.route('**/api/**', async (route) => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        const parts = path.split('/');
        if (path === '/api/state') {
            await route.fulfill({json: state});
        } else if (path === '/api/engine/devices') {
            await route.fulfill({json: computeDevices});
        } else if (path === '/api/prompts/budget') {
            await route.fulfill({json: syntheticPromptBudget(request.postDataJSON())});
        } else if (path === '/api/model-prompts') {
            if (request.method() === 'PUT') {
                const body = request.postDataJSON();
                prompts.set(body.profileId || body.model, body);
                await route.fulfill({json: body});
            } else {
                await route.fulfill({json: {prompts: [...prompts.values()]}});
            }
        } else if (path === '/api/settings') {
            Object.assign(state.settings, request.postDataJSON());
            await route.fulfill({json: state.settings});
        } else if (path === '/api/model-routing') {
            if (request.method() === 'PUT') routing = request.postDataJSON();
            await route.fulfill({json: routing});
        } else if (parts[2] === 'model-profiles') {
            if (parts[4] === 'test') {
                await route.fulfill({
                    json: {
                        ok: true,
                        code: '',
                        message: 'Connection works',
                        durationMs: 124,
                        inputTokens: 12,
                        outputTokens: 5,
                        finishReason: 'stop',
                        preview: 'Hi!',
                    },
                });
                return;
            }
            if (request.method() === 'GET') {
                await route.fulfill({json: {profiles, presets: [providerPreset]}});
                return;
            }
            const body = request.postDataJSON();
            const saved = {
                ...body,
                id: parts[3] ?? 'profile-new',
                apiKeyConfigured: Boolean(body.apiKey) || !body.clearApiKey,
            };
            delete saved.apiKey;
            delete saved.clearApiKey;
            const index = profiles.findIndex((profile) => profile.id === saved.id);
            if (index >= 0) profiles[index] = saved;
            else profiles.push(saved);
            await route.fulfill({json: saved});
        } else if (path.startsWith('/api/chats/')) {
            await route.fulfill({json: []});
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
    await page.getByRole('button', {name: ru.profileMenu, exact: true}).click();
    await page.getByRole('menuitem', {name: ru.settings, exact: true}).click();
});
test('center navigation preserves drafts and keeps actions visible on short screens', async ({page}) => {
    const dialog = page.getByRole('dialog', {name: ru.settings, exact: true});
    await expect.poll(async () => Math.round((await dialog.boundingBox())!.width)).toBe(960);
    await chooseSettingsSection(page, 'prompt');
    await dialog.getByRole('textbox', {name: ru.globalPrompt, exact: true}).fill('Prefer short messages.');
    await chooseSettingsSection(page, 'replies');
    await dialog.getByRole('switch', {name: ru.showTyping, exact: true}).press('Space');
    await chooseSettingsSection(page, 'prompt');
    await expect(dialog.getByRole('textbox', {name: ru.globalPrompt, exact: true})).toHaveValue(
        'Prefer short messages.',
    );
    const saved = page.waitForRequest(
        (request) => request.url().endsWith('/api/settings') && request.method() === 'PATCH',
    );
    await dialog.getByRole('button', {name: ru.save, exact: true}).click();
    expect((await saved).postDataJSON()).toMatchObject({
        globalPrompt: 'Prefer short messages.',
        showTyping: false,
    });
    await expect(dialog.getByRole('button', {name: ru.saved, exact: true})).toBeVisible();
    await page.setViewportSize({width: 1100, height: 600});
    await chooseSettingsSection(page, 'providers');
    await dialog.getByRole('button', {name: ru.modelProfiles.create, exact: true}).click();
    await dialog.getByRole('button', {name: ru.modelProfiles.advanced, exact: true}).click();
    await dialog
        .getByRole('combobox', {name: ru.modelProfiles.tokenLimit, exact: true})
        .scrollIntoViewIfNeeded();
    await expect(dialog.getByRole('button', {name: ru.save, exact: true})).toBeInViewport();
    await page.setViewportSize({width: 1440, height: 900});
    await chooseSettingsSection(page, 'application');
    await page.screenshot({path: '../docs/resources/settings-center.png', animations: 'disabled'});
});
test('built-in theme switches directly and persists without custom appearance controls', async ({page}) => {
    const dialog = page.getByRole('dialog', {name: ru.settings, exact: true});
    await dialog.getByRole('button', {name: ru.close, exact: true}).click();
    await page.getByRole('button', {name: ru.theme, exact: true}).click();
    await expect(page.locator('body')).toContainClass('g-root_theme_dark');
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await page.reload();
    await expect(page.locator('body')).toContainClass('g-root_theme_dark');
    await expect(page.locator('style[data-aigram-theme]')).toHaveCount(0);
    await expect(page.locator('[data-aigram-css-scope]')).toHaveCount(0);
});

test('profiles keep keys private, save separately and only test on explicit request', async ({page}) => {
    let tests = 0;
    page.on('request', (request) => {
        if (new URL(request.url()).pathname.endsWith('/test')) tests++;
    });
    await chooseSettingsSection(page, 'providers');
    await page.getByRole('button').filter({hasText: 'Cloud text'}).click();
    const key = page.getByLabel(ru.modelProfiles.apiKey, {exact: true});
    await expect(key).toHaveValue('');
    await expect(key).toHaveAttribute('type', 'password');
    expect(tests).toBe(0);
    await page.getByRole('textbox', {name: ru.modelProfiles.name, exact: true}).fill('My API');
    const saved = page.waitForRequest(
        (request) =>
            request.url().endsWith('/api/model-profiles/profile-primary') && request.method() === 'PUT',
    );
    await page.getByRole('button', {name: ru.save, exact: true}).click();
    expect((await saved).postDataJSON()).not.toHaveProperty('apiKey');
    await expect(page.getByRole('button', {name: ru.saved, exact: true})).toBeVisible();
    expect(tests).toBe(0);
    await page.getByRole('button', {name: ru.modelProfiles.testAction, exact: true}).click();
    await expect(page.getByText('Connection works', {exact: true})).toBeVisible();
    expect(tests).toBe(1);
    await page.getByRole('switch', {name: ru.modelProfiles.clearKey, exact: true}).press('Space');
    const cleared = page.waitForRequest(
        (request) =>
            request.url().endsWith('/api/model-profiles/profile-primary') && request.method() === 'PUT',
    );
    await page.getByRole('button', {name: ru.save, exact: true}).click();
    expect((await cleared).postDataJSON()).toMatchObject({clearApiKey: true});
    await page.getByRole('button', {name: ru.modelProfiles.back, exact: true}).click();
    await expect(page.getByRole('button').filter({hasText: 'My API'})).toBeVisible();
    await page.screenshot({path: '../docs/resources/model-connections.png', animations: 'disabled'});
});
test('routing offers only vision-capable profiles for image analysis', async ({page}) => {
    await chooseSettingsSection(page, 'routing');
    await page.getByRole('switch', {name: ru.modelRouting.enabled, exact: true}).press('Space');
    const vision = page.getByRole('combobox', {name: ru.modelRouting.tasks.visionProfileId, exact: true});
    await vision.click();
    await expect(page.getByRole('option').filter({hasText: 'Cloud text'})).toHaveCount(0);
    await page.getByRole('option').filter({hasText: 'Photo model'}).click();
    await page.getByRole('switch', {name: ru.modelRouting.parallel, exact: true}).press('Space');
    const saved = page.waitForRequest(
        (request) => request.url().endsWith('/api/model-routing') && request.method() === 'PUT',
    );
    await page.getByRole('button', {name: ru.save, exact: true}).click();
    expect((await saved).postDataJSON()).toMatchObject({
        enabled: true,
        visionProfileId: 'profile-vision',
        parallelIndependentTasks: true,
    });
    await expect(page.getByRole('button', {name: ru.saved, exact: true})).toBeVisible();
});
for (const dark of [false, true]) {
    test(`model connections use one centered illustrated empty state in ${dark ? 'dark' : 'light'} theme`, async ({
        page,
    }) => {
        await page.route('**/api/model-profiles', (route) =>
            route.fulfill({json: {profiles: [], presets: [providerPreset]}}),
        );
        if (dark) {
            await persistTestTheme(page, true);
            await page.reload();
            await page.getByRole('button', {name: ru.profileMenu, exact: true}).click();
            await page.getByRole('menuitem', {name: ru.settings, exact: true}).click();
        }
        await chooseSettingsSection(page, 'providers');
        const empty = page.locator('[data-qa="model-connections-empty"]');
        await expect(empty.getByText(ru.modelProfiles.emptyTitle, {exact: true})).toBeVisible();
        await expect(page.locator('[data-qa="settings-content-providers"]')).toHaveCount(0);
        await expect
            .poll(() => empty.locator('img').evaluate((image) => (image as HTMLImageElement).naturalWidth))
            .toBeGreaterThan(0);
        const geometry = await empty.evaluate((element) => {
            const content = element.getBoundingClientRect();
            const page = element.parentElement!.getBoundingClientRect();
            return {
                horizontal: Math.abs(content.x + content.width / 2 - page.x - page.width / 2),
                vertical: Math.abs(content.y + content.height / 2 - page.y - page.height / 2),
            };
        });
        expect(geometry.horizontal).toBeLessThan(2);
        expect(geometry.vertical).toBeLessThan(2);
        await page.screenshot({
            path: `../docs/resources/model-connections-empty-${dark ? 'dark' : 'light'}.png`,
            animations: 'disabled',
        });
        await empty.getByRole('button', {name: ru.modelProfiles.create, exact: true}).click();
        await expect(page.getByRole('textbox', {name: ru.modelProfiles.name, exact: true})).toBeVisible();
    });
    test(`settings navigation stays fixed and language cards align in ${dark ? 'dark' : 'light'} theme`, async ({
        page,
    }) => {
        if (dark) {
            await persistTestTheme(page, true);
            await page.reload();
            await page.getByRole('button', {name: ru.profileMenu, exact: true}).click();
            await page.getByRole('menuitem', {name: ru.settings, exact: true}).click();
        }
        const dialog = page.getByRole('dialog', {name: ru.settings, exact: true});
        await expect.poll(async () => Math.round((await dialog.boundingBox())!.width)).toBe(960);
        await expect(dialog).toHaveCSS('transform', 'none');
        const brand = page.locator('[data-qa="settings-brand"]');
        const initial = await brand.boundingBox();
        await chooseSettingsSection(page, 'language');
        expect((await brand.boundingBox())!.y).toBeCloseTo(initial!.y, 2);
        const geometry = await dialog.getByRole('radio').evaluateAll((radios) =>
            radios.map((radio) => {
                const label = radio.closest('label')!;
                const card = label.getBoundingClientRect();
                const flag = label.querySelector('img')!.getBoundingClientRect();
                const name = label.querySelector('.g-control-label__text .g-text')!;
                const indicator = radio.getBoundingClientRect();
                return {
                    flagCenter: Math.abs(flag.y + flag.height / 2 - card.y - card.height / 2),
                    radioCenter: Math.abs(indicator.y + indicator.height / 2 - card.y - card.height / 2),
                    right: card.right - indicator.right,
                    border: getComputedStyle(label).borderWidth,
                    bold: Number(getComputedStyle(name).fontWeight),
                    height: card.height,
                };
            }),
        );
        expect(geometry).toHaveLength(4);
        const languageGaps = await dialog.getByRole('radio').evaluateAll((radios) =>
            radios.slice(1).map((radio, index) => {
                const previous = radios[index].closest('label')!.getBoundingClientRect();
                return radio.closest('label')!.getBoundingClientRect().top - previous.bottom;
            }),
        );
        expect(languageGaps.every((gap) => gap <= 8)).toBe(true);
        geometry.forEach((card) => {
            expect(card.flagCenter).toBeLessThan(1);
            expect(card.radioCenter).toBeLessThan(1);
            expect(card.right).toBeGreaterThanOrEqual(16);
            expect(card.right).toBeLessThanOrEqual(20);
            expect(card.border).toBe('0px');
            expect(card.bold).toBeGreaterThanOrEqual(600);
            expect(card.height).toBeLessThanOrEqual(50);
        });
        const nav = page.locator('[data-qa="settings-navigation-scroll"]');
        const track = nav.locator('..').getByRole('scrollbar').locator('..');
        await page.mouse.move(1100, 70);
        await expect(track).toHaveCSS('opacity', '0');
        await dialog.getByRole('menuitem', {name: ru.settingsCenter.sections.language, exact: true}).hover();
        await expect(track).toHaveCSS('opacity', '1');
        await page.mouse.move(1100, 70);
        await nav.locator('..').getByRole('scrollbar').focus();
        await nav.locator('..').getByRole('scrollbar').press('ArrowDown');
        await expect(track).toHaveCSS('opacity', '1');
        await page.screenshot({
            path: `../docs/resources/settings-language-${dark ? 'dark' : 'light'}.png`,
            animations: 'disabled',
        });
        await chooseSettingsSection(page, 'compute');
        await expect(page.getByRole('combobox', {name: ru.computeDevice.label, exact: true})).toBeVisible();
        await page.screenshot({
            path: `../docs/resources/settings-compute-${dark ? 'dark' : 'light'}.png`,
            animations: 'disabled',
        });
        await chooseSettingsSection(page, 'replies');
        await expect(page.getByRole('switch', {name: ru.proactive.title, exact: true})).toHaveCount(0);
        const sliders = page.locator('[data-qa="settings-content-replies"] [data-qa="settings-slider"]');
        await expect(sliders).toHaveCount(2);
        await expectSettingsSlider(sliders.nth(0));
        await expectSettingsSlider(sliders.nth(1));
        await page.screenshot({
            path: `../docs/resources/settings-replies-${dark ? 'dark' : 'light'}.png`,
            animations: 'disabled',
        });
        await chooseSettingsSection(page, 'proactive');
        await expect(page.getByRole('switch', {name: ru.proactive.title, exact: true})).not.toBeChecked();
        await page.screenshot({
            path: `../docs/resources/settings-proactive-${dark ? 'dark' : 'light'}.png`,
            animations: 'disabled',
        });
        await page.route('**/api/memory', (route) =>
            route.fulfill({
                json: {
                    chatId: '',
                    notes: 'Предпочитаю встречаться после работы.',
                    summary: '**Интересы**\n\nИзучает Kotlin, работает над своим приложением.',
                    revision: 'synthetic',
                    updatedAt: 1792578000,
                    processedMessages: 240,
                    backfillComplete: true,
                    status: 'ready',
                    error: '',
                    trackedChats: 3,
                    relativePath: 'memory/100/MEMORY.md',
                },
            }),
        );
        await page.evaluate(() => {
            window.aigramDesktop = {
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
        await chooseSettingsSection(page, 'memory');
        const memoryActions = page.locator('[data-qa="memory-actions"]');
        await expect(memoryActions.getByRole('button')).toHaveCount(4);
        const explorer = memoryActions.getByRole('button', {name: ru.storageLocations.open, exact: true});
        await expect(explorer).toHaveCSS('border-width', '0px');
        await explorer.click();
        await expect(page.locator('html')).toHaveAttribute('data-opened-storage', 'memory');
        await expectCompactMemoryActions(memoryActions);
        await page.screenshot({
            path: `../docs/resources/settings-memory-${dark ? 'dark' : 'light'}.png`,
            animations: 'disabled',
        });
        const notes = page.getByRole('textbox', {name: ru.memory.notes, exact: true});
        await notes.fill(Array.from({length: 35}, (_, index) => `Заметка ${index + 1}`).join('\n'));
        await notes.blur();
        await page.mouse.move(100, 40);
        const nativeScrollbar = await notes.evaluate((element) => ({
            color: getComputedStyle(element).scrollbarColor,
            width: getComputedStyle(element, '::-webkit-scrollbar').width,
            arrow: getComputedStyle(element, '::-webkit-scrollbar-button').display,
            thumb: getComputedStyle(element, '::-webkit-scrollbar-thumb').backgroundColor,
        }));
        expect(nativeScrollbar).toEqual({
            color: 'auto',
            width: '6px',
            arrow: 'none',
            thumb: 'rgba(0, 0, 0, 0)',
        });
        await notes.hover();
        expect(
            await notes.evaluate(
                (element) => getComputedStyle(element, '::-webkit-scrollbar-thumb').backgroundColor,
            ),
        ).not.toBe('rgba(0, 0, 0, 0)');
    });
}
test('model additions save separately, preserve drafts and display token estimates', async ({page}) => {
    await chooseSettingsSection(page, 'prompt');
    await page.getByRole('textbox', {name: ru.globalPrompt, exact: true}).fill('Shared wording');
    await page
        .getByRole('textbox', {name: ru.modelPrompts.instructions, exact: true})
        .fill('Prefer a direct answer');
    await page.getByRole('spinbutton', {name: ru.modelPrompts.contextWindow, exact: true}).fill('16384');
    await expect(page.locator('[data-qa="prompt-budget"]')).toContainText(ru.promptBudget.estimated);
    const saved = page.waitForRequest(
        (request) => request.url().endsWith('/api/model-prompts') && request.method() === 'PUT',
    );
    await page.getByRole('button', {name: ru.modelPrompts.save, exact: true}).click();
    expect((await saved).postDataJSON()).toMatchObject({
        instructions: 'Prefer a direct answer',
        contextWindow: 16384,
        profileId: '',
    });
    expect((await saved).postDataJSON()).not.toHaveProperty('globalPrompt');
    await page.getByRole('combobox', {name: ru.modelPrompts.model, exact: true}).click();
    await page.getByRole('option', {name: 'Cloud text · API', exact: true}).click();
    await expect(
        page.getByRole('spinbutton', {name: ru.modelPrompts.contextWindow, exact: true}),
    ).toBeDisabled();
    await expect(page.getByRole('textbox', {name: ru.globalPrompt, exact: true})).toHaveValue(
        'Shared wording',
    );
});
for (const dark of [false, true]) {
    test(`memory actions stay compact and wrap in both memory windows in ${dark ? 'dark' : 'light'} theme`, async ({
        page,
    }) => {
        if (dark) {
            await persistTestTheme(page, true);
            await page.reload();
            await page.getByRole('button', {name: ru.profileMenu, exact: true}).click();
            await page.getByRole('menuitem', {name: ru.settings, exact: true}).click();
        }
        const document = {
            chatId: '',
            notes: 'Ручная заметка',
            summary: 'Факты о встречах и договорённостях.',
            revision: 'synthetic',
            updatedAt: 1792578000,
            processedMessages: 240,
            backfillComplete: true,
            status: 'ready',
            error: '',
            trackedChats: 3,
            relativePath: 'memory/100/MEMORY.md',
        };
        await page.route('**/api/memory', (route) => route.fulfill({json: document}));
        await page.route('**/api/memory/41', (route) => route.fulfill({json: {...document, chatId: '41'}}));
        await page.evaluate(() => {
            window.aigramDesktop = {
                state: async () => ({available: true, maximized: false, visible: true, minimized: false}),
                sync: async () => {},
                quit: async () => {},
                action: async () => {},
                onState: () => () => {},
                openStorageLocation: () =>
                    new Promise<void>((resolve) => {
                        window.addEventListener('test-memory-open-complete', () => resolve(), {once: true});
                    }),
            };
        });
        await chooseSettingsSection(page, 'memory');
        const actions = page.locator('[data-qa="memory-actions"]');
        await expectCompactMemoryActions(actions);
        const explorer = actions.getByRole('button', {name: ru.storageLocations.open, exact: true});
        const initial = (await explorer.boundingBox())!;
        await explorer.click();
        await expect(explorer).toBeDisabled();
        const loading = (await explorer.boundingBox())!;
        expect({width: loading.width, height: loading.height}).toEqual({
            width: initial.width,
            height: initial.height,
        });
        await page.evaluate(() => window.dispatchEvent(new Event('test-memory-open-complete')));
        await expect(explorer).toBeEnabled();
        await page.setViewportSize({width: 420, height: 800});
        const narrow = await expectCompactMemoryActions(actions);
        expect(new Set(narrow.buttons.map((button) => button.top)).size).toBeGreaterThan(1);
        await actions.scrollIntoViewIfNeeded();
        await page.screenshot({
            path: `../docs/resources/memory-actions-narrow-${dark ? 'dark' : 'light'}.png`,
            animations: 'disabled',
        });
        await page.setViewportSize({width: 1440, height: 900});
        await page
            .getByRole('dialog', {name: ru.settings, exact: true})
            .getByRole('button', {name: ru.close, exact: true})
            .click();
        await page.getByRole('button', {name: workspaceState.chats[0].title, exact: true}).click();
        await page.getByRole('button', {name: ru.memory.openChat, exact: true}).click();
        const chatMemory = page.getByRole('dialog', {
            name: ru.memory.chatTitle.replace('{name}', workspaceState.chats[0].title),
            exact: true,
        });
        await expectCompactMemoryActions(chatMemory.locator('[data-qa="memory-actions"]'));
        await page.screenshot({
            path: `../docs/resources/memory-dialog-${dark ? 'dark' : 'light'}.png`,
            animations: 'disabled',
        });
        await page.setViewportSize({width: 420, height: 800});
        const chatNarrow = await expectCompactMemoryActions(chatMemory.locator('[data-qa="memory-actions"]'));
        expect(new Set(chatNarrow.buttons.map((button) => button.top)).size).toBeGreaterThan(1);
    });
}
