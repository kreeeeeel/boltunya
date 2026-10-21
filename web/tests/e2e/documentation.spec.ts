import {expect, test} from '@playwright/test';
import {readFileSync} from 'node:fs';
import type {AppState, Message} from '@/shared/api/client';
import {workspaceState} from '../fixtures/workspace';
import {persistTestTheme} from '../fixtures/settings';

test('documents the workspace using synthetic conversations and verifies the refreshed layout', async ({
    page,
    context,
}) => {
    const base = process.env.TIHO_TEST_URL ?? 'http://127.0.0.1:3099';
    await context.addCookies([
        {name: 'tiho-lang', value: 'en', url: base},
        {name: 'tiho-theme', value: 'light', url: base},
    ]);
    const state: AppState = structuredClone(workspaceState);
    let appearance = {mode: 'light', tokens: {}, css: ''};
    const catalog = JSON.parse(
        readFileSync('../backend/src/main/resources/models/catalog.json', 'utf8'),
    ) as AppState['engine']['models'];
    state.engine.models = catalog.map((model) => ({
        ...model,
        installed: ['qwen3:1.7b', 'gemma3:4b'].includes(model.id),
    }));
    const names = ['Anna Miller', 'Mark Wilson', 'Lisa', 'Product team', 'Alex', 'Family'];
    const previews = [
        'sounds good, see you then ☀️',
        'Sent the updated mockups',
        'Have you seen the new season?',
        'The release is ready 🎉',
        'Coffee this weekend?',
        'How was your day?',
    ];
    state.chats = state.chats.map((chat, index) => ({
        ...chat,
        title: names[index],
        preview: previews[index],
        username: index === 0 ? 'anna_example' : '',
    }));
    const texts = [
        {text: 'hey! how’s the project going?', outgoing: false},
        {text: 'pretty good, finally shipped the new version))', outgoing: true},
        {text: 'nice 🎉\ncoffee tomorrow? same place?', outgoing: false},
        {text: 'yeah, after two works for me', outgoing: true},
        {text: 'perfect! what’s the weather looking like?', outgoing: false},
        {text: 'looks cloudy tomorrow, 12–15 °C. bring a jacket))', outgoing: true, automated: true},
        {text: 'sounds good, see you then ☀️', outgoing: false},
    ];
    const history: Message[] = texts.map((message, index) => ({
        ...message,
        id: String(index),
        chatId: '41',
        date: 1792141200 + index * 120,
        kind: 'text',
    }));
    await page.route('**/api/**', async (route) => {
        const path = new URL(route.request().url()).pathname;
        if (path === '/api/state') await route.fulfill({json: state});
        else if (path.endsWith('/messages')) await route.fulfill({json: history});
        else if (path === '/api/appearance') {
            if (route.request().method() === 'PUT') {
                appearance = route.request().postDataJSON();
            }
            await route.fulfill({json: appearance});
        } else if (path === '/api/model-profiles') await route.fulfill({json: {profiles: [], presets: []}});
        else await route.fulfill({status: 400, json: {error: 'Unexpected documentation request'}});
    });
    await page.goto(base);
    await page.getByRole('button', {name: 'Anna Miller', exact: true}).click();
    await expect(page.locator('[data-qa="message-ai"]')).toHaveText('AI');
    await page.evaluate(() => document.fonts.ready);
    const font = await page.evaluate(() => ({
        declared: getComputedStyle(document.documentElement).getPropertyValue('--font-ui').trim(),
        applied: getComputedStyle(document.body).fontFamily,
    }));
    expect(font.declared).not.toBe('');
    expect(font.applied.replaceAll('"', '')).toContain(
        font.declared.split(',')[0].trim().replaceAll('"', ''),
    );
    const controlFonts = await page
        .locator('.g-button:visible, input:visible, [data-qa="chat-row"] .g-text:visible')
        .evaluateAll((elements) => [
            ...new Set(elements.map((element) => getComputedStyle(element).fontFamily)),
        ]);
    expect(
        controlFonts.every((family) =>
            family.replaceAll('"', '').includes(font.declared.split(',')[0].trim().replaceAll('"', '')),
        ),
    ).toBe(true);
    const row = page.locator('[data-qa="chat-row"]').first();
    await expect(row).toHaveCSS('border-radius', '12px');
    await expect(page.locator('[data-qa="conversation-header"]')).toHaveCSS('border-radius', '16px');
    const wallpaperBounds = await page.evaluate(() => {
        const shell = document.querySelector('[data-qa="workspace-shell"]')!.getBoundingClientRect();
        const wallpaper = document.querySelector('[data-qa="chat-wallpaper"]')!.getBoundingClientRect();
        return {
            shell: {x: shell.x, y: shell.y, width: shell.width, height: shell.height},
            wallpaper: {x: wallpaper.x, y: wallpaper.y, width: wallpaper.width, height: wallpaper.height},
        };
    });
    expect(wallpaperBounds.wallpaper).toEqual(wallpaperBounds.shell);
    await expect(page.locator('[data-qa="chat-wallpaper"]')).toHaveCSS('z-index', '-1');
    await expect(page.locator('[data-qa="window-bar"]')).toHaveCSS('border-bottom-width', '0px');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({path: '../docs/resources/readme-chat-light.png', animations: 'disabled'});

    await persistTestTheme(page, true);
    await page.reload();
    await page.getByRole('button', {name: 'Anna Miller', exact: true}).click();
    await expect(page.locator('[data-qa="message-ai"]')).toBeVisible();
    await page.evaluate(() => document.fonts.ready);
    await page.screenshot({path: '../docs/resources/readme-chat-dark.png', animations: 'disabled'});

    await page.locator('[data-qa="nav-models"]').click();
    const search = page.locator('[data-qa="model-search"] input');
    await expect(page.locator('[data-qa="model-card"]')).toHaveCount(catalog.length);
    await expect(page.locator('[data-qa="model-card"]').first()).toHaveCSS('border-top-width', '2px');
    await search.fill('GEMMA');
    expect(await page.locator('[data-qa="model-card"]').count()).toBeGreaterThan(0);
    await expect(page.locator('[data-qa="model-card"]').first()).toContainText('Gemma');
    await search.fill('no-such-model');
    await expect(page.locator('[data-qa="model-card"]')).toHaveCount(0);
    await page.getByRole('button', {name: 'Clear search', exact: true}).click();
    await expect(page.locator('[data-qa="model-card"]')).toHaveCount(catalog.length);
    await page.screenshot({path: '../docs/resources/readme-models.png', animations: 'disabled'});
});
