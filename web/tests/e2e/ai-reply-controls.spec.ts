import {expect, test} from '@playwright/test';
import type {Locator, Page} from '@playwright/test';
import type * as Contract from '@aigram/api-client';
import {mkdirSync, readFileSync} from 'node:fs';
import {join} from 'node:path';
import {workspaceState} from '../fixtures/workspace';
import {testHistory} from '../fixtures/chats';
import {chooseSettingsSection} from '../fixtures/settings';
import {syntheticPromptBudget} from '../fixtures/promptBudget';
import ru from '../../src/shared/i18n/ru.json';
import media from '../../src/modules/media/i18n/ru.json';

const title = 'Синтетический диалог';
const generatedText = '🙂 Готово и секрет';
const generatedEntities: Contract.MessageEntity[] = [
    {offset: generatedText.indexOf('Готово'), length: 6, type: 'bold'},
    {offset: generatedText.indexOf('секрет'), length: 6, type: 'spoiler'},
];
const screenshotDirectory = join(process.cwd(), '..', 'build', 'ai-reply-controls');

async function prepare(page: Page, dark: boolean) {
    const state = structuredClone(workspaceState);
    state.telegram.userId = '100';
    state.settings.formatReplies = false;
    state.settings.replyProcessingMode = 'replace';
    state.settings.proactiveEnabled = false;
    state.settings.proactiveIntervalMinutes = 60;
    state.chats = state.chats.map(
        (chat, index): Contract.Chat => ({
            ...chat,
            title: index === 0 ? title : `Синтетический контакт ${index + 1}`,
            kind: 'private',
            joined: true,
            unread: 0,
            lastMessageId: '7',
            rule: {
                ...chat.rule,
                enabled: false,
                proactiveMode: 'disabled',
                proactiveMessageMode: 'ai',
                proactiveText: '',
            },
        }),
    );
    const sent: Contract.Text[] = [];
    const attachments: Contract.SendMediaRequest[] = [];
    let drafts = 0;
    let uploads = 0;
    await page.route('**/api/**', async (route) => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        if (path === '/api/state') {
            await route.fulfill({json: state});
        } else if (path === '/api/appearance') {
            await route.fulfill({json: {mode: dark ? 'dark' : 'light', tokens: {}, css: ''}});
        } else if (path === '/api/model-profiles') {
            await route.fulfill({json: {profiles: [], presets: []}});
        } else if (path === '/api/model-routing') {
            await route.fulfill({json: {enabled: false, parallelIndependentTasks: false}});
        } else if (path === '/api/model-prompts') {
            await route.fulfill({json: {prompts: []}});
        } else if (path === '/api/prompts/budget') {
            await route.fulfill({json: syntheticPromptBudget(request.postDataJSON())});
        } else if (path === '/api/search/connection') {
            await route.fulfill({json: {configured: false, state: 'unchecked'}});
        } else if (path === '/api/memory/41' && request.method() === 'GET') {
            const document: Contract.MemoryDocument = {
                chatId: '41',
                notes: '',
                summary: '',
                revision: 'synthetic-empty',
                updatedAt: 0,
                processedMessages: 0,
                backfillComplete: false,
                status: 'idle',
                error: '',
                trackedChats: 0,
                relativePath: 'memory/100/41/MEMORY.md',
            };
            await route.fulfill({json: document});
        } else if (path === '/api/settings') {
            Object.assign(state.settings, request.postDataJSON());
            await route.fulfill({json: state.settings});
        } else if (path === '/api/chats/41/rule') {
            state.chats[0].rule = {...state.chats[0].rule, ...request.postDataJSON()};
            state.settings.rules['41'] = state.chats[0].rule;
            await route.fulfill({json: {ok: true}});
        } else if (path === '/api/chats/41/messages') {
            if (request.method() === 'POST') {
                sent.push(request.postDataJSON());
                await route.fulfill({json: {ok: true}});
            } else {
                await route.fulfill({json: testHistory('41')});
            }
        } else if (path === '/api/preview/jobs') {
            drafts += 1;
            await route.fulfill({
                json: {id: `rich-draft-${drafts}`, status: 'queued', result: null, error: ''},
            });
        } else if (path.startsWith('/api/preview/jobs/rich-draft-')) {
            await route.fulfill({
                json: {
                    id: path.split('/').at(-1),
                    status: 'ready',
                    result: {
                        text: generatedText,
                        entities: generatedEntities,
                        durationMs: 124,
                        tokensPerSecond: 20,
                    },
                    error: '',
                },
            });
        } else if (path === '/api/uploads' && request.method() === 'POST') {
            uploads += 1;
            await route.fulfill({
                json: {
                    id: `synthetic-upload-${uploads}`,
                    fileName: decodeURIComponent(request.headers()['x-file-name'] ?? 'synthetic.bin'),
                    size: request.postDataBuffer()?.length ?? 1,
                    mimeType: request.headers()['content-type'],
                },
            });
        } else if (path.startsWith('/api/uploads/') && request.method() === 'DELETE') {
            await route.fulfill({json: {ok: true}});
        } else if (path === '/api/chats/41/attachments') {
            attachments.push(request.postDataJSON());
            await route.fulfill({json: {ok: true}});
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
    await expect(page.locator('body')).toContainClass(`g-root_theme_${dark ? 'dark' : 'light'}`);
    return {state, sent, attachments};
}

async function openSettings(page: Page) {
    await page.getByRole('button', {name: ru.profileMenu, exact: true}).click();
    await page.getByRole('menuitem', {name: ru.settings, exact: true}).click();
    return page.getByRole('dialog', {name: ru.settings, exact: true});
}

async function richDraft(page: Page) {
    await page.getByRole('button', {name: ru.draft, exact: true}).click();
    const editor = page.getByRole('textbox', {name: ru.messagePlaceholder, exact: true});
    await expect(editor).toHaveAttribute('contenteditable', 'true');
    await expect(editor).toContainClass('ProseMirror');
    await expect(editor).toHaveText(generatedText);
    await expect(editor.locator('span[class*="bold"]')).toHaveText('Готово');
    await expect(page.getByRole('toolbar', {name: ru.messagePlaceholder, exact: true})).toBeVisible();
    return editor;
}

async function screenshots(page: Page, name: string, dark: boolean, target?: Locator) {
    mkdirSync(screenshotDirectory, {recursive: true});
    for (const width of [1440, 390]) {
        await page.setViewportSize({width, height: 900});
        await page.evaluate(
            () =>
                new Promise<void>((resolve) =>
                    requestAnimationFrame(() => requestAnimationFrame(() => resolve())),
                ),
        );
        if (target) {
            await expect(target).toBeVisible();
            await target.evaluate(async (element) => {
                const body = element.closest('.g-dialog-body');
                await Promise.all(
                    (body?.getAnimations() ?? []).map((animation) =>
                        animation.finished.catch(() => undefined),
                    ),
                );
            });
            await expect
                .poll(() =>
                    target.evaluate(
                        (element) => getComputedStyle(element.closest('.g-dialog-body') ?? element).overflowY,
                    ),
                )
                .not.toBe('clip');
            await target.evaluate((element) => {
                const body = element.closest<HTMLElement>('.g-dialog-body');
                if (body && body.scrollHeight > body.clientHeight) {
                    const bounds = element.getBoundingClientRect();
                    body.scrollTop +=
                        bounds.top -
                        body.getBoundingClientRect().top -
                        (body.clientHeight - bounds.height) / 2;
                } else {
                    element.scrollIntoView({block: 'center', inline: 'nearest'});
                }
            });
            await expect(target).toBeInViewport({ratio: 1});
        }
        await expect
            .poll(async () => (await page.getByRole('alert').allTextContents()).filter((text) => text.trim()))
            .toEqual([]);
        await expect
            .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
            .toBe(true);
        await page.screenshot({
            path: join(screenshotDirectory, `${name}-${dark ? 'dark' : 'light'}-${width}.png`),
            animations: 'disabled',
        });
    }
    await page.setViewportSize({width: 1440, height: 900});
}

for (const dark of [false, true]) {
    const theme = dark ? 'dark' : 'light';
    test(`global reply processing and an independent chat override persist in ${theme}`, async ({page}) => {
        const {state} = await prepare(page, dark);
        const settings = await openSettings(page);
        await chooseSettingsSection(page, 'replies');
        const globalMode = settings.getByRole('combobox', {name: ru.replyInterruption.title, exact: true});
        await expect(globalMode).toContainText(ru.replyInterruption.options.replace);
        await globalMode.click();
        await page.getByRole('option', {name: ru.replyInterruption.options.finish, exact: true}).click();
        const savedSettings = page.waitForRequest(
            (request) => request.url().endsWith('/api/settings') && request.method() === 'PATCH',
        );
        await settings.getByRole('button', {name: ru.save, exact: true}).click();
        expect((await savedSettings).postDataJSON()).toMatchObject({replyProcessingMode: 'finish'});
        await expect(settings.getByRole('button', {name: ru.saved, exact: true})).toBeVisible();
        await settings.getByRole('button', {name: ru.close, exact: true}).click();
        await page.getByRole('button', {name: title, exact: true}).click();
        await page.getByRole('button', {name: ru.configure, exact: true}).click();
        const dialog = page.locator('[data-qa="communication-dialog"]');
        const override = dialog.getByRole('combobox', {name: ru.replyInterruption.mode, exact: true});
        await expect(override).toContainText(ru.replyInterruption.options.inherit);
        await override.click();
        await page.getByRole('option', {name: ru.replyInterruption.options.each, exact: true}).click();
        const savedRule = page.waitForRequest(
            (request) => request.url().endsWith('/api/chats/41/rule') && request.method() === 'PUT',
        );
        await dialog.getByRole('button', {name: ru.save, exact: true}).click();
        expect((await savedRule).postDataJSON()).toMatchObject({
            enabled: false,
            replyInterruptionMode: 'each',
        });
        await expect(dialog.getByRole('button', {name: ru.saved, exact: true})).toBeVisible();
        await expect(dialog.getByRole('switch', {name: ru.enableAi, exact: true})).not.toBeChecked();
        await screenshots(page, 'reply-processing-mode', dark, override);
        await dialog.getByRole('button', {name: ru.cancel, exact: true}).click();
        await page.reload();
        const reopened = await openSettings(page);
        await chooseSettingsSection(page, 'replies');
        await expect(
            reopened.getByRole('combobox', {name: ru.replyInterruption.title, exact: true}),
        ).toContainText(ru.replyInterruption.options.finish);
        expect(state.chats[0].rule.replyInterruptionMode).toBe('each');
        expect(state.chats[0].rule.enabled).toBe(false);
    });

    test(`reply formatting is explicit and persists without closing settings in ${theme}`, async ({page}) => {
        const {state} = await prepare(page, dark);
        const dialog = await openSettings(page);
        await chooseSettingsSection(page, 'replies');
        const formatting = dialog.getByRole('switch', {name: ru.formatReplies, exact: true});
        await expect(formatting).not.toBeChecked();
        await formatting.press('Space');
        const saved = page.waitForRequest(
            (request) => request.url().endsWith('/api/settings') && request.method() === 'PATCH',
        );
        await dialog.getByRole('button', {name: ru.save, exact: true}).click();
        expect((await saved).postDataJSON()).toMatchObject({formatReplies: true});
        await expect(dialog.getByRole('button', {name: ru.saved, exact: true})).toBeVisible();
        await expect(dialog).toBeVisible();
        expect(state.settings.formatReplies).toBe(true);
        await formatting.scrollIntoViewIfNeeded();
        await screenshots(page, 'settings-formatting', dark, formatting);

        await page.reload();
        const reopened = await openSettings(page);
        await chooseSettingsSection(page, 'replies');
        await expect(reopened.getByRole('switch', {name: ru.formatReplies, exact: true})).toBeChecked();
    });

    test(`formatted generated draft uses a compact real editor and sends native entities in ${theme}`, async ({
        page,
    }) => {
        const {sent} = await prepare(page, dark);
        await page.getByRole('button', {name: title, exact: true}).click();
        const editor = await richDraft(page);
        await editor.press('Control+Home');
        await editor.press('!');
        await expect(editor).toHaveText('!' + generatedText);
        await expect(editor.locator('span[class*="bold"]')).toHaveText('Готово');
        const bounds = await page.locator('[data-qa="message-edit-editor"]').boundingBox();
        expect(bounds?.height).toBeLessThan(300);
        await screenshots(page, 'rich-generated-draft', dark, editor);
        await page.getByRole('button', {name: ru.send, exact: true}).click();
        await expect.poll(() => sent.length).toBe(1);
        expect(sent[0].text).toBe('!' + generatedText);
        expect(sent[0].text).not.toContain('**');
        expect(sent[0].entities).toEqual(
            expect.arrayContaining([
                {offset: 4, length: 6, type: 'bold'},
                expect.objectContaining({
                    offset: generatedText.indexOf('секрет') + 1,
                    length: 6,
                    type: 'spoiler',
                }),
            ]),
        );
        await expect(page.locator('[data-qa="message-edit-editor"]')).toHaveCount(0);
    });

    test(`rich draft becomes one native first caption for photo and document batches in ${theme}`, async ({
        page,
    }) => {
        const {sent, attachments} = await prepare(page, dark);
        await page.getByRole('button', {name: title, exact: true}).click();
        for (const kind of ['media', 'document'] as const) {
            await richDraft(page);
            await page.getByRole('button', {name: media.attach, exact: true}).click();
            const chooser = page.waitForEvent('filechooser');
            await page
                .getByRole('menuitem', {
                    name: kind === 'document' ? media.documents : 'Фотографии',
                    exact: true,
                })
                .click();
            const png = Buffer.from(
                'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aFl8AAAAASUVORK5CYII=',
                'base64',
            );
            await (
                await chooser
            ).setFiles(
                [1, 2].map((index) => ({
                    name: `synthetic-${index}.${kind === 'document' ? 'txt' : 'png'}`,
                    mimeType: kind === 'document' ? 'text/plain' : 'image/png',
                    buffer: kind === 'document' ? Buffer.from('Synthetic document') : png,
                })),
            );
            const dialog = page.getByRole('dialog', {
                name: kind === 'document' ? media.documents : media.photos,
                exact: true,
            });
            const caption = dialog.getByRole('textbox', {name: media.caption, exact: true});
            await expect(caption).toHaveAttribute('contenteditable', 'true');
            await expect(caption).toHaveText(generatedText);
            await expect(caption.locator('span[class*="bold"]')).toHaveText('Готово');
            await dialog.getByRole('button', {name: media.send, exact: true}).click();
            await expect(dialog).not.toBeVisible();
            await expect.poll(() => attachments.length).toBe(kind === 'media' ? 1 : 2);
            const request = attachments.at(-1);
            expect(request).toMatchObject({kind, caption: generatedText});
            expect(request?.uploadIds).toHaveLength(2);
            expect(request?.captionEntities).toEqual(expect.arrayContaining(generatedEntities));
            expect(sent).toHaveLength(0);
            await expect(page.locator('[data-qa="message-edit-editor"]')).toHaveCount(0);
        }
    });

    test(`fixed proactive text saves with auto replies off and the global cog opens the same dialog in ${theme}`, async ({
        page,
    }) => {
        const {state} = await prepare(page, dark);
        await page.getByRole('button', {name: title, exact: true}).click();
        await page.getByRole('button', {name: ru.configure, exact: true}).click();
        const dialog = page.locator('[data-qa="communication-dialog"]');
        await expect(dialog.getByRole('switch', {name: ru.enableAi, exact: true})).not.toBeChecked();
        await dialog.getByRole('combobox', {name: ru.proactive.mode, exact: true}).click();
        await page.getByRole('option', {name: ru.proactive.modes.enabled, exact: true}).click();
        await dialog.getByRole('combobox', {name: ru.proactive.messageMode, exact: true}).click();
        await page.getByRole('option', {name: ru.proactive.messageModes.text, exact: true}).click();
        await dialog
            .getByRole('textbox', {name: ru.proactive.messageText, exact: true})
            .fill('Как прошёл твой день?');
        await dialog.getByRole('switch', {name: ru.proactive.inheritInterval, exact: true}).press('Space');
        await dialog.getByRole('combobox', {name: ru.proactive.unit, exact: true}).click();
        await page.getByRole('option', {name: ru.proactive.minutes, exact: true}).click();
        await dialog.getByRole('spinbutton', {name: ru.proactive.interval, exact: true}).fill('15');
        const saved = page.waitForRequest(
            (request) => request.url().endsWith('/api/chats/41/rule') && request.method() === 'PUT',
        );
        await dialog.getByRole('button', {name: ru.save, exact: true}).click();
        expect((await saved).postDataJSON()).toMatchObject({
            enabled: false,
            proactiveMode: 'enabled',
            proactiveMessageMode: 'text',
            proactiveText: 'Как прошёл твой день?',
            proactiveIntervalMinutes: 15,
        });
        await expect(dialog.getByRole('button', {name: ru.saved, exact: true})).toBeVisible();
        await expect(dialog).toBeVisible();
        await dialog
            .getByRole('textbox', {name: ru.proactive.messageText, exact: true})
            .scrollIntoViewIfNeeded();
        await screenshots(
            page,
            'proactive-text-mode',
            dark,
            dialog.getByRole('textbox', {name: ru.proactive.messageText, exact: true}),
        );
        await dialog.getByRole('button', {name: ru.cancel, exact: true}).click();

        const settings = await openSettings(page);
        await chooseSettingsSection(page, 'proactive');
        expect(state.settings.proactiveEnabled).toBe(false);
        const row = settings.locator('[data-qa="proactive-chat"]').filter({hasText: title});
        await expect(row).toContainText(ru.proactive.messageModes.text);
        await expect(settings.locator('[data-qa="proactive-chat"]')).toHaveCount(1);
        await screenshots(page, 'proactive-active-chats', dark, row);
        await row
            .getByRole('button', {name: ru.proactive.configureChat.replace('{name}', title), exact: true})
            .click();
        await expect(settings).not.toBeVisible();
        await expect(page.getByRole('dialog')).toHaveCount(1);
        await expect(dialog).toBeVisible();
        await expect(dialog.getByRole('textbox', {name: ru.proactive.messageText, exact: true})).toHaveValue(
            'Как прошёл твой день?',
        );
        await expect(dialog.getByRole('switch', {name: ru.enableAi, exact: true})).not.toBeChecked();
        await expect(dialog.getByRole('spinbutton', {name: ru.proactive.interval, exact: true})).toHaveValue(
            '15',
        );
    });
}
