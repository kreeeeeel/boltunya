import {test, expect} from '@playwright/test';
import {workspaceState} from '../fixtures/workspace';
import {modelProfiles, providerPreset} from '../fixtures/modelProfiles';
import {chooseSettingsSection} from '../fixtures/settings';
import {persistTestTheme} from '../fixtures/settings';
import {syntheticPromptBudget} from '../fixtures/promptBudget';
import {computeDevices} from '../fixtures/computeDevices';
import type {components} from '../../src/shared/api/schema';
import {readFileSync} from 'node:fs';
import {join} from 'node:path';
import ru from '../../src/shared/i18n/ru.json';
test.beforeEach(async ({page}) => {
    const state = structuredClone(workspaceState);
    let appearance: components['schemas']['AppearanceSettings'] = {mode: 'light', tokens: {}, css: ''};
    const profiles = structuredClone(modelProfiles);
    const prompts = new Map<string, components['schemas']['ModelPromptSettings']>();
    let routing = {
        enabled: false,
        parallelIndependentTasks: false,
        replyProfileId: '',
        memoryProfileId: '',
        researchProfileId: '',
        visionProfileId: '',
    };
    await page.route('**/api/**', async (route) => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        const parts = path.split('/');
        if (path === '/api/state') {
            await route.fulfill({json: state});
        } else if (path === '/api/appearance') {
            if (request.method() === 'PUT') appearance = request.postDataJSON();
            await route.fulfill({json: appearance});
        } else if (path === '/api/engine/devices') {
            await route.fulfill({json: computeDevices});
        } else if (path === '/api/search/connection') {
            await route.fulfill({json: {configured: false, state: 'unchecked'}});
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
test('custom CSS stays scoped, blocks remote resources and persists with a safe reset', async ({page}) => {
    await chooseSettingsSection(page, 'appearance');
    const dialog = page.getByRole('dialog', {name: ru.settings, exact: true});
    const css = dialog.getByRole('textbox', {name: ru.appearance.css, exact: true});
    await css.fill('* { border-radius: 22px; }');
    await dialog.getByRole('textbox', {name: ru.appearance.tokens.accent, exact: true}).fill('#a855f7');
    await dialog.getByRole('button', {name: ru.appearance.preview, exact: true}).click();
    expect(await dialog.evaluate((element) => element.closest('[data-aigram-css-scope]'))).toBeNull();
    await expect(dialog.getByRole('button', {name: ru.appearance.reset, exact: true})).toBeVisible();
    const style = page.locator('style[data-aigram-theme]');
    await expect.poll(() => style.textContent()).toContain('[data-aigram-css-scope] *');
    await dialog.getByRole('button', {name: ru.save, exact: true}).click();
    await expect(dialog.getByRole('button', {name: ru.saved, exact: true})).toBeVisible();
    await page.screenshot({path: '../docs/resources/appearance-editor.png', animations: 'disabled'});
    await page.reload();
    await expect.poll(() => page.locator('style[data-aigram-theme]').textContent()).toContain('#a855f7');
    await page.getByRole('button', {name: ru.profileMenu, exact: true}).click();
    await page.getByRole('menuitem', {name: ru.settings, exact: true}).click();
    await chooseSettingsSection(page, 'appearance');
    await css.fill('.x { background: url(https://example.com/tracker); }');
    await dialog.getByRole('button', {name: ru.appearance.preview, exact: true}).click();
    await expect(dialog.getByRole('alert')).toHaveText(ru.appearance.unsafeCss);
    await css.fill('* { position: fixed; inset: 0; z-index: 2147483647; opacity: 0; }');
    await dialog.getByRole('button', {name: ru.appearance.preview, exact: true}).click();
    await dialog.getByRole('button', {name: ru.appearance.reset, exact: true}).click();
    await expect(css).toHaveValue('');
    await dialog.getByRole('button', {name: ru.save, exact: true}).click();
    await expect.poll(() => page.locator('style[data-aigram-theme]').textContent()).toBe('');
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
        const brand = page.locator('[data-qa="settings-brand"]');
        const initial = await brand.boundingBox();
        await chooseSettingsSection(page, 'language');
        expect((await brand.boundingBox())!.y).toBe(initial!.y);
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
                };
            }),
        );
        expect(geometry).toHaveLength(4);
        geometry.forEach((card) => {
            expect(card.flagCenter).toBeLessThan(1);
            expect(card.radioCenter).toBeLessThan(1);
            expect(card.right).toBeGreaterThanOrEqual(16);
            expect(card.right).toBeLessThanOrEqual(20);
            expect(card.border).toBe('0px');
            expect(card.bold).toBeGreaterThanOrEqual(600);
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
        await chooseSettingsSection(page, 'search');
        await page.getByText(ru.internetSearch.optionalExa, {exact: true}).click();
        await page.screenshot({
            path: `../docs/resources/settings-search-${dark ? 'dark' : 'light'}.png`,
            animations: 'disabled',
        });
        await chooseSettingsSection(page, 'appearance');
        await expect(page.locator('[data-qa="appearance-live-preview"]')).toBeVisible();
        await page.screenshot({
            path: `../docs/resources/settings-appearance-${dark ? 'dark' : 'light'}.png`,
            animations: 'disabled',
        });
    });
}

test('the color picker updates its field and live example before applying the whole theme', async ({
    page,
}) => {
    await chooseSettingsSection(page, 'appearance');
    const picker = page.getByRole('group', {
        name: ru.appearance.pickColor.replace('{name}', ru.appearance.tokens.accent),
    });
    await picker.getByRole('button').click();
    const color = page.locator('.g-color-picker__popup input').first();
    await color.fill('#34d399ff');
    await color.blur();
    await expect(page.getByRole('textbox', {name: ru.appearance.tokens.accent, exact: true})).toHaveValue(
        '#34d399ff',
    );
    await page.locator('[data-qa="appearance-live-preview"]').getByText(ru.appearance.exampleName).click();
    await expect(page.locator('[data-qa="appearance-live-preview"]')).toContainText(
        ru.appearance.exampleOutgoing,
    );
    await expect(page.locator('[data-qa="appearance-live-preview"] > :first-child > :first-child')).toHaveCSS(
        'background-color',
        'rgb(52, 211, 153)',
    );
    await page.screenshot({path: '../docs/resources/appearance-color-picker.png', animations: 'disabled'});
});

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
