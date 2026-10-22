import {initializeTestTheme} from '../fixtures/settings';
import {expect, test} from '@playwright/test';
import type {Page} from '@playwright/test';
import {mkdirSync, readFileSync} from 'node:fs';
import {join} from 'node:path';
import {defaultRule} from '../../src/shared/api/client';
import {workspaceState} from '../fixtures/workspace';
import {testHistory} from '../fixtures/chats';
import {speechState} from '../fixtures/speech';
import {syntheticPromptBudget} from '../fixtures/promptBudget';
import {chooseChatSettingsSection} from '../fixtures/chatSettings';
import ru from '../../src/shared/i18n/ru.json';

const title = 'Синтетическая беседа';
const proof = join(process.cwd(), '..', 'build', 'chat-settings-ui');

async function prepare(page: Page, dark: boolean) {
    await initializeTestTheme(page, dark);
    const state = structuredClone(workspaceState);
    state.telegram = {...state.telegram, name: 'Синтетический профиль', userId: '100'};
    state.settings = {
        ...state.settings,
        voiceInputEnabled: true,
        voiceReplyMode: 'mirror',
        voiceReplyProbability: 37,
        voiceSynthesisModel: 'piper-ru-irina',
        voiceRecognitionModel: 'whisper-tiny',
        voiceSpeechRate: 1.3,
    };
    state.chats = [
        {
            ...state.chats[0],
            title,
            avatarUrl: '',
            kind: 'group',
            rule: {...defaultRule, ...state.chats[0].rule, enabled: false, voiceSettingsMode: 'inherit'},
        },
    ];
    const saved: unknown[] = [];
    await page.route('**/api/**', async (route) => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        if (path === '/api/state') {
            await route.fulfill({json: state});
        } else if (path === '/api/model-profiles') {
            await route.fulfill({json: {profiles: [], presets: []}});
        } else if (path === '/api/prompts/budget') {
            await route.fulfill({json: syntheticPromptBudget(request.postDataJSON())});
        } else if (path === '/api/speech') {
            await route.fulfill({json: speechState});
        } else if (path === '/api/chats/41/messages') {
            await route.fulfill({json: testHistory('41')});
        } else if (path === '/api/chats/41/rule' && request.method() === 'PUT') {
            const rule = request.postDataJSON();
            saved.push(rule);
            state.chats[0].rule = {...state.chats[0].rule, ...rule};
            state.settings.rules['41'] = state.chats[0].rule;
            await route.fulfill({json: {ok: true}});
        } else {
            await route.fulfill({status: 404, json: {message: 'Unexpected synthetic request: ' + path}});
        }
    });
    const launch =
        process.env.TIHO_TEST_URL ??
        readFileSync(
            process.env.TIHO_LAUNCH_FILE ?? join(process.env.LOCALAPPDATA ?? '', 'Aigram', 'launch.url'),
            'utf8',
        );
    await page.setViewportSize({width: 1440, height: 900});
    await page.goto(launch);
    await page.getByRole('button', {name: title, exact: true}).click();
    await page.getByRole('button', {name: ru.configure, exact: true}).click();
    return {state, saved};
}

async function assertLayout(page: Page) {
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByRole('button', {name: ru.save, exact: true})).toBeInViewport();
    await expect
        .poll(() =>
            dialog.evaluate((element) => {
                const bounds = element.getBoundingClientRect();
                const footer = element
                    .querySelector('[data-qa="chat-settings-footer"]')!
                    .getBoundingClientRect();
                return (
                    bounds.left >= 0 &&
                    bounds.right <= innerWidth &&
                    bounds.top >= 0 &&
                    bounds.bottom <= innerHeight &&
                    footer.bottom <= bounds.bottom &&
                    element.scrollWidth <= element.clientWidth
                );
            }),
        )
        .toBe(true);
    return dialog;
}

for (const dark of [false, true]) {
    test(`chat settings keeps section drafts and independent voice preferences in ${dark ? 'dark' : 'light'}`, async ({
        page,
    }) => {
        const {state, saved} = await prepare(page, dark);
        const dialog = await assertLayout(page);
        await expect(dialog.locator('[data-qa="chat-settings-identity"]')).toContainText(title);
        await expect(
            dialog.getByRole('menuitem', {name: ru.chatSettings.sections.groups, exact: true}),
        ).toBeVisible();
        await chooseChatSettingsSection(page, 'model');
        await dialog
            .getByRole('textbox', {name: ru.instructions, exact: true})
            .fill('Контекст только этой беседы.');
        await chooseChatSettingsSection(page, 'voice');
        await expect(
            dialog.getByRole('combobox', {name: ru.chatSettings.voice.source, exact: true}),
        ).toContainText(ru.chatSettings.voice.inherit);
        await dialog.getByRole('combobox', {name: ru.chatSettings.voice.source, exact: true}).click();
        await page.getByRole('option', {name: ru.chatSettings.voice.override, exact: true}).click();
        await expect(dialog.getByRole('combobox', {name: ru.voice.replyMode, exact: true})).toContainText(
            ru.voice.replyModes.mirror,
        );
        await dialog.getByRole('combobox', {name: ru.voice.replyMode, exact: true}).click();
        await page.getByRole('option', {name: ru.voice.replyModes.random, exact: true}).click();
        await dialog.getByRole('slider', {name: ru.voice.probability, exact: true}).press('End');
        await dialog.getByRole('slider', {name: ru.voice.speechRate, exact: true}).press('Home');
        for (const width of [1440, 390]) {
            await page.setViewportSize({width, height: 900});
            await chooseChatSettingsSection(page, 'voice');
            const model = dialog.getByRole('combobox', {name: ru.voice.models.synthesis, exact: true});
            await model.scrollIntoViewIfNeeded();
            await assertLayout(page);
            mkdirSync(proof, {recursive: true});
            await page.screenshot({
                path: join(proof, `chat-voice-${dark ? 'dark' : 'light'}-${width}.png`),
                animations: 'disabled',
            });
        }
        await chooseChatSettingsSection(page, 'model');
        await expect(dialog.getByRole('textbox', {name: ru.instructions, exact: true})).toHaveValue(
            'Контекст только этой беседы.',
        );
        await dialog.getByRole('button', {name: ru.save, exact: true}).click();
        await expect(dialog.getByRole('button', {name: ru.saved, exact: true})).toBeVisible();
        expect(saved).toEqual([
            expect.objectContaining({
                enabled: false,
                instructions: 'Контекст только этой беседы.',
                voiceSettingsMode: 'override',
                voiceInputEnabled: true,
                voiceReplyMode: 'random',
                voiceReplyProbability: 100,
                voiceSynthesisModel: 'piper-ru-irina',
                voiceSpeechRate: 0.5,
            }),
        ]);
        expect(state.settings.voiceReplyMode).toBe('mirror');
        expect(state.settings.voiceSpeechRate).toBe(1.3);
        await expect(dialog).toBeVisible();
        await dialog.getByRole('button', {name: ru.cancel, exact: true}).click();
        await page.getByRole('button', {name: ru.configure, exact: true}).click();
        await chooseChatSettingsSection(page, 'voice');
        await expect(dialog.getByRole('combobox', {name: ru.voice.replyMode, exact: true})).toContainText(
            ru.voice.replyModes.random,
        );
        await dialog.getByRole('combobox', {name: ru.chatSettings.voice.source, exact: true}).click();
        await page.getByRole('option', {name: ru.chatSettings.voice.inherit, exact: true}).click();
        await dialog.getByRole('button', {name: ru.save, exact: true}).click();
        await expect.poll(() => saved.length).toBe(2);
        expect(saved[1]).toMatchObject({
            voiceSettingsMode: 'inherit',
            voiceReplyMode: 'random',
            enabled: false,
        });
    });
}
