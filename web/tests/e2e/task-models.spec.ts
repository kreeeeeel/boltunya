import {test, expect} from '@playwright/test';
import {readFileSync} from 'node:fs';
import {join} from 'node:path';
import {workspaceState} from '../fixtures/workspace';
import {modelProfiles, providerPreset} from '../fixtures/modelProfiles';
import {chooseSettingsSection, persistTestTheme} from '../fixtures/settings';
import {syntheticPromptBudget} from '../fixtures/promptBudget';
import type * as Contract from '@aigram/api-client';
import ru from '../../src/shared/i18n/ru.json';
test.beforeEach(async ({page}) => {
    const state = structuredClone(workspaceState);
    state.engine.models = [
        {...state.engine.models[1], installed: true, supportsVision: false},
        {...state.engine.models[2], installed: true, supportsVision: true},
        {
            ...state.engine.models[1],
            id: 'custom:local',
            name: 'custom:local',
            family: 'custom',
            size: '—',
            contextTokens: 0,
            memoryGb: 0,
            speed: 'unknown',
            quality: 'unknown',
            license: '—',
            installed: true,
            supportsVision: false,
        },
        {
            ...state.engine.models[2],
            id: 'qwen3-vl:4b',
            name: 'Qwen 3 VL · 4B',
            family: 'qwen-vl',
            contextTokens: 262144,
            size: '3.3 GB',
            installed: false,
            supportsVision: true,
        },
    ];
    let appearance: Contract.AppearanceSettings = {mode: 'light', tokens: {}, css: ''};
    let routing: Contract.ModelTaskRouting = {
        enabled: true,
        parallelIndependentTasks: false,
        replyProfileId: '',
        memoryProfileId: '',
        researchProfileId: '',
        visionProfileId: '',
    };
    await page.route('**/api/**', async (route) => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        if (path === '/api/state') {
            await route.fulfill({json: state});
        } else if (path === '/api/appearance') {
            if (request.method() === 'PUT') appearance = request.postDataJSON();
            await route.fulfill({json: appearance});
        } else if (path === '/api/model-profiles') {
            await route.fulfill({json: {profiles: modelProfiles, presets: [providerPreset]}});
        } else if (path === '/api/model-routing') {
            if (request.method() === 'PUT') routing = request.postDataJSON();
            await route.fulfill({json: routing});
        } else if (path === '/api/prompts/budget') {
            await route.fulfill({json: syntheticPromptBudget(request.postDataJSON())});
        } else if (path === '/api/settings') {
            Object.assign(state.settings, request.postDataJSON());
            await route.fulfill({json: state.settings});
        } else {
            await route.fulfill({status: 404, json: {message: 'Unexpected synthetic request'}});
        }
    });
    const launch =
        process.env.TIHO_TEST_URL ??
        readFileSync(
            process.env.TIHO_LAUNCH_FILE ?? join(process.env.LOCALAPPDATA ?? '', 'Aigram', 'launch.url'),
            'utf8',
        );
    await page.goto(launch);
});
for (const dark of [false, true]) {
    const theme = dark ? 'dark' : 'light';
    test(`installed task choices persist and model categories combine with search in ${theme} theme`, async ({
        page,
    }) => {
        if (dark) {
            await persistTestTheme(page, true);
            await page.reload();
        }
        await expect
            .poll(async () =>
                (await page.locator('body').getAttribute('class'))?.includes(`g-root_theme_${theme}`),
            )
            .toBe(true);
        await page.getByRole('button', {name: ru.profileMenu, exact: true}).click();
        await page.getByRole('menuitem', {name: ru.settings, exact: true}).click();
        await chooseSettingsSection(page, 'routing');
        const dialog = page.getByRole('dialog', {name: ru.settings, exact: true});
        const reply = dialog.getByRole('combobox', {name: ru.modelRouting.tasks.replyProfileId, exact: true});
        await reply.click();
        await expect(page.getByRole('option', {name: 'Ollama · custom:local', exact: true})).toBeVisible();
        await expect(page.getByRole('option', {name: 'Ollama · Qwen 3 VL · 4B', exact: true})).toHaveCount(0);
        await page.getByRole('option', {name: 'Ollama · Qwen 3 · 1.7B', exact: true}).click();
        let saved = page.waitForRequest(
            (request) => request.url().endsWith('/api/model-routing') && request.method() === 'PUT',
        );
        await dialog.getByRole('button', {name: ru.save, exact: true}).click();
        expect((await saved).postDataJSON()).toMatchObject({replyModel: 'qwen3:1.7b', replyProfileId: ''});
        await expect(dialog.getByRole('button', {name: ru.saved, exact: true})).toBeVisible();
        await chooseSettingsSection(page, 'application');
        await chooseSettingsSection(page, 'routing');
        await expect(reply).toContainText('Qwen 3 · 1.7B');
        await reply.click();
        await page.getByRole('option', {name: 'Cloud text · example-model', exact: true}).click();
        saved = page.waitForRequest(
            (request) => request.url().endsWith('/api/model-routing') && request.method() === 'PUT',
        );
        await dialog.getByRole('button', {name: ru.save, exact: true}).click();
        expect((await saved).postDataJSON()).toMatchObject({
            replyModel: '',
            replyProfileId: 'profile-primary',
        });
        await expect(dialog.getByRole('button', {name: ru.saved, exact: true})).toBeVisible();
        const vision = dialog.getByRole('combobox', {
            name: ru.modelRouting.tasks.visionProfileId,
            exact: true,
        });
        await vision.click();
        await expect(page.getByRole('option', {name: 'Ollama · Gemma 3 · 4B', exact: true})).toBeVisible();
        await expect(
            page.getByRole('option', {name: 'Photo model · photo-model', exact: true}),
        ).toBeVisible();
        await expect(page.getByRole('option', {name: 'Ollama · Qwen 3 · 1.7B', exact: true})).toHaveCount(0);
        await expect(page.getByRole('option', {name: 'Ollama · custom:local', exact: true})).toHaveCount(0);
        await expect(page.getByRole('option', {name: 'Cloud text · example-model', exact: true})).toHaveCount(
            0,
        );
        await expect(page.getByRole('option', {name: 'Ollama · Qwen 3 VL · 4B', exact: true})).toHaveCount(0);
        await page.screenshot({path: `../docs/resources/task-routing-${theme}.png`, animations: 'disabled'});
        await page.getByRole('option', {name: 'Ollama · Gemma 3 · 4B', exact: true}).click();
        saved = page.waitForRequest(
            (request) => request.url().endsWith('/api/model-routing') && request.method() === 'PUT',
        );
        await dialog.getByRole('button', {name: ru.save, exact: true}).click();
        expect((await saved).postDataJSON()).toMatchObject({visionModel: 'gemma3:4b', visionProfileId: ''});
        await expect(dialog.getByRole('button', {name: ru.saved, exact: true})).toBeVisible();
        await dialog.getByRole('button', {name: ru.close, exact: true}).click();
        await page.locator('[data-qa="nav-models"]').click();
        const locals = page.locator('[data-qa="model-card"]');
        const profiles = page.locator('[data-qa="profile-model-card"]');
        const search = page.getByRole('textbox', {name: ru.modelSearch.label, exact: true});
        await expect(locals).toHaveCount(4);
        await expect(profiles).toHaveCount(2);
        await expect(page.locator('[data-model-id="custom:local"]')).toContainText(ru.modelMetadataUnknown);
        await page.getByRole('tab', {name: ru.modelFilters.vision, exact: true}).click();
        await expect(locals).toHaveCount(2);
        await expect(profiles).toHaveCount(1);
        await expect(page.locator('[data-model-id="custom:local"]')).toHaveCount(0);
        await search.fill('Qwen');
        await expect(locals).toHaveCount(1);
        await expect(page.locator('[data-model-id="qwen3-vl:4b"]')).toBeVisible();
        await expect(profiles).toHaveCount(0);
        await search.fill('');
        await page.getByRole('tab', {name: ru.modelFilters.generation, exact: true}).click();
        await expect(locals).toHaveCount(2);
        await expect(profiles).toHaveCount(1);
        await page.getByRole('tab', {name: ru.modelFilters.all, exact: true}).click();
        await expect(locals).toHaveCount(4);
        await expect(profiles).toHaveCount(2);
        const top = await page
            .locator('[data-qa="workspace-view"]')
            .getByRole('scrollbar')
            .evaluate((element) => {
                const viewport = document.getElementById(element.getAttribute('aria-controls') ?? '');
                if (!viewport) return null;
                viewport.scrollTop = 0;
                return viewport.scrollTop;
            });
        expect(top).toBe(0);
        await expect(search).toBeInViewport();
        await expect(page.locator('[data-qa="engine-card"]')).toBeInViewport();
        await page.screenshot({path: `../docs/resources/task-models-${theme}.png`, animations: 'disabled'});
    });
}

for (const dark of [false, true]) {
    const theme = dark ? 'dark' : 'light';
    test(`Ollama restart fits the engine card and retries without duplicate errors in ${theme} theme`, async ({
        page,
    }) => {
        const state = structuredClone(workspaceState);
        state.engine.canRestart = true;
        let restartCount = 0;
        const failure = 'Не удалось перезапустить Ollama.';
        await page.route('**/api/state', (route) => route.fulfill({json: state}));
        await page.route('**/api/engine/restart', async (route) => {
            restartCount += 1;
            state.engine.error = restartCount === 1 ? failure : '';
            await route.fulfill(
                restartCount === 1 ? {status: 503, json: {message: failure}} : {json: {ok: true}},
            );
        });
        if (dark) {
            await persistTestTheme(page, true);
            await page.reload();
        }
        await page.locator('[data-qa="nav-models"]').click();
        const card = page.locator('[data-qa="engine-card"]');
        const restart = card.getByRole('button', {name: ru.restartEngine, exact: true});
        await expect(restart).toBeEnabled();
        await restart.click();
        const notice = page.getByRole('alert', {name: ru.engineErrorTitle, exact: true});
        await expect(notice).toContainText(failure);
        await expect(page.locator('[data-qa="workspace-view"]').getByRole('alert')).toHaveCount(1);
        await expect(restart).toBeEnabled();
        await page.evaluate(() => document.fonts.ready);
        for (const width of [1440, 640]) {
            await page.setViewportSize({width, height: 900});
            await card.scrollIntoViewIfNeeded();
            await expect(restart).toBeInViewport();
            const geometry = () =>
                restart.evaluate((button) => {
                    const bounds = button.getBoundingClientRect();
                    const parent = button.closest('[data-qa="engine-card"]')!.getBoundingClientRect();
                    const label = button.querySelector('.g-button__text')!.getBoundingClientRect();
                    const status = button.parentElement!.querySelector('[data-qa="engine-installed"]')!;
                    const statusBounds = status.getBoundingClientRect();
                    const actions = button.parentElement!.getBoundingClientRect();
                    const notice = button
                        .closest('[data-qa="workspace-view"]')!
                        .querySelector('[role="alert"]')!;
                    return {
                        buttonFits: bounds.left >= parent.left && bounds.right <= parent.right,
                        textFits: label.left >= bounds.left && label.right <= bounds.right,
                        clipped: button.scrollWidth > button.clientWidth + 1,
                        pageOverflow: document.documentElement.scrollWidth > window.innerWidth,
                        restartFirst: bounds.right <= statusBounds.left,
                        sameRow: Math.abs(bounds.top - statusBounds.top) <= 1,
                        sameHeight: Math.abs(bounds.height - statusBounds.height) <= 1,
                        compactHeight:
                            Math.abs(bounds.height - 28) <= 1 && Math.abs(actions.height - 28) <= 1,
                        sameRadius:
                            getComputedStyle(button, '::before').borderRadius ===
                            getComputedStyle(status).borderRadius,
                        naturalWidth:
                            Math.abs(
                                actions.width -
                                    bounds.width -
                                    statusBounds.width -
                                    parseFloat(getComputedStyle(button.parentElement!).columnGap),
                            ) <= 1,
                        noticeGap: Math.round(notice.getBoundingClientRect().top - parent.bottom),
                    };
                });
            await expect.poll(geometry).toEqual({
                buttonFits: true,
                textFits: true,
                clipped: false,
                pageOverflow: false,
                restartFirst: true,
                sameRow: true,
                sameHeight: true,
                compactHeight: true,
                sameRadius: true,
                naturalWidth: true,
                noticeGap: 14,
            });
            await page.screenshot({
                path: `../docs/resources/engine-restart-${width === 640 ? 'compact-' : ''}${theme}.png`,
                animations: 'disabled',
            });
        }
        await restart.click();
        await expect(notice).toHaveCount(0);
        expect(restartCount).toBe(2);
        state.engine.canRestart = false;
        await expect(restart).toBeDisabled();
        await expect(restart).toHaveAttribute('title', ru.engineRestartExternal);
        expect(restartCount).toBe(2);
    });
}
