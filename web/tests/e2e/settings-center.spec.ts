import {test, expect} from '@playwright/test';
import {workspaceState} from '../fixtures/workspace';
import {modelProfiles, providerPreset} from '../fixtures/modelProfiles';
import {chooseSettingsSection} from '../fixtures/settings';
import type {components} from '../../src/shared/api/schema';
import {readFileSync} from 'node:fs';
import {join} from 'node:path';
import ru from '../../src/shared/i18n/ru.json';
test.beforeEach(async ({page}) => {
    const state = structuredClone(workspaceState);
    let appearance: components['schemas']['AppearanceSettings'] = {mode: 'light', tokens: {}, css: ''};
    const profiles = structuredClone(modelProfiles);
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
    await expect.poll(async () => Math.round((await dialog.boundingBox())!.width)).toBe(1160);
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
    await page.getByRole('menuitem', {name: ru.settingsCenter.sections.appearance, exact: true}).click();
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
