import {initializeTestTheme} from '../fixtures/settings';
import {expect, test} from '@playwright/test';
import type {Page} from '@playwright/test';
import {readFileSync} from 'node:fs';
import {join} from 'node:path';
import type {MessageEditOptions, MessageEditRequest} from '@aigram/api-client';
import type {Message} from '../../src/shared/api/client';
import {workspaceState} from '../fixtures/workspace';
import ru from '../../src/shared/i18n/ru.json';

const originalText = '🙂 Привет, друг!\nПолезная ссылка и секрет.';
const originalEntities: MessageEditOptions['entities'] = [
    {kind: 'existing', sourceIndex: 0, offset: originalText.indexOf('Привет'), length: 6, typeHint: 'bold'},
    {
        kind: 'existing',
        sourceIndex: 1,
        offset: originalText.indexOf('ссылка'),
        length: 6,
        typeHint: 'texturl',
        url: 'https://example.com/guide',
    },
    {
        kind: 'existing',
        sourceIndex: 2,
        offset: originalText.indexOf('секрет'),
        length: 6,
        typeHint: 'spoiler',
    },
];

async function prepare(page: Page, dark: boolean) {
    await initializeTestTheme(page, dark);
    const state = structuredClone(workspaceState);
    let messages: Message[] = Array.from({length: 50}, (_, index) => ({
        id: String(80 + index),
        chatId: '41',
        outgoing: false,
        date: 1792064000 + index,
        kind: 'text',
        text: `Синтетическое сообщение ${index + 1}. История для проверки положения прокрутки.`,
    }));
    messages = messages.map((message) =>
        ['99', '129'].includes(message.id)
            ? {
                  ...message,
                  outgoing: true,
                  text: originalText,
                  automated: true,
                  contextExcluded: true,
                  editedAt: 100,
                  kind: message.id === '129' ? 'photo' : 'text',
                  entities: originalEntities.map((entity) => ({
                      offset: entity.offset,
                      length: entity.length,
                      type: entity.typeHint ?? '',
                      url: entity.url,
                  })),
                  ...(message.id === '129'
                      ? {
                            media: {
                                fileId: 333,
                                mimeType: 'image/png',
                                fileName: 'synthetic.png',
                                width: 320,
                                height: 200,
                                thumbnailFileId: 0,
                                size: 1024,
                            },
                        }
                      : {}),
              }
            : message,
    );
    const requests: MessageEditRequest[] = [];
    let revision = 'a'.repeat(64);
    let references = originalEntities;
    let holdHistory = false;
    let release: (() => void) | undefined;
    let editReads = 0;

    await page.route('**/api/**', async (route) => {
        const request = route.request();
        const url = new URL(request.url());
        const parts = url.pathname.split('/');
        if (url.pathname === '/api/state') {
            await route.fulfill({json: state});
        } else if (url.pathname === '/api/preview/jobs') {
            await route.fulfill({
                json: {
                    id: 'preserved-ai-draft',
                    status: 'ready',
                    result: {
                        text: 'Мой несохранённый ответ',
                        entities: [],
                        durationMs: 1,
                        tokensPerSecond: 1,
                    },
                },
            });
        } else if (url.pathname === '/api/speech') {
            await route.fulfill({json: {assets: [], downloads: []}});
        } else if (url.pathname === '/api/model-profiles') {
            await route.fulfill({json: {profiles: [], presets: []}});
        } else if (url.pathname === '/api/chats/41/messages') {
            const before = url.searchParams.get('before');
            const snapshot = before
                ? messages.filter((message) => BigInt(message.id) < BigInt(before)).slice(-20)
                : messages.slice(-30);
            if (!before && holdHistory) {
                holdHistory = false;
                await new Promise<void>((resolve) => {
                    release = resolve;
                });
            }
            await route.fulfill({json: snapshot});
        } else if (parts[4] === 'messages' && parts[6] === 'edit') {
            editReads += request.method() === 'GET' ? 1 : 0;
            const message = messages.find((item) => item.id === parts[5]);
            if (!message) {
                await route.fulfill({status: 404, json: {code: 'MESSAGE_EDIT_NOT_ALLOWED'}});
            } else if (request.method() === 'GET') {
                await route.fulfill({
                    json: {
                        text: message.text,
                        entities: references,
                        revision,
                        kind: message.kind === 'text' ? 'text' : 'caption',
                        maxLength: message.kind === 'text' ? 4096 : 1024,
                    },
                });
            } else {
                const body = request.postDataJSON() as MessageEditRequest;
                requests.push(body);
                if (body.revision !== revision) {
                    await route.fulfill({status: 409, json: {code: 'MESSAGE_EDIT_CONFLICT'}});
                    return;
                }
                const updated = {
                    ...message,
                    text: body.text,
                    editedAt: 100,
                    entities: body.entities.map((entity) => ({
                        offset: entity.offset,
                        length: entity.length,
                        type: entity.kind === 'existing' ? (entity.typeHint ?? '') : entity.kind,
                        url: entity.url,
                    })),
                };
                messages = messages.map((item) => (item.id === updated.id ? updated : item));
                revision = 'b'.repeat(64);
                references = body.entities.map((entity, index) => ({
                    ...entity,
                    kind: 'existing',
                    sourceIndex: index,
                }));
                await route.fulfill({json: updated});
            }
        } else if (url.pathname === '/api/media/333') {
            await route.fulfill({
                contentType: 'image/svg+xml',
                body: '<svg xmlns="http://www.w3.org/2000/svg" width="320" height="200"><rect width="320" height="200" rx="16" fill="#649da8"/><circle cx="180" cy="80" r="38" fill="#e3bd77"/></svg>',
            });
        } else {
            await route.fulfill({status: 404, json: {code: 'SYNTHETIC_ROUTE_NOT_FOUND'}});
        }
    });
    const launch =
        process.env.TIHO_TEST_URL ??
        readFileSync(
            process.env.TIHO_LAUNCH_FILE ?? join(process.env.LOCALAPPDATA ?? '', 'Tiho', 'launch.url'),
            'utf8',
        );
    await page.goto(launch);
    await page.getByRole('button', {name: state.chats[0].title, exact: true}).click();
    await expect(page.locator('[data-message-row-id="129"]')).toBeVisible();
    return {
        requests,
        hold: () => {
            holdHistory = true;
        },
        held: () => !!release,
        release: () => release?.(),
        editReads: () => editReads,
    };
}

for (const dark of [false, true]) {
    test(`edits paginated formatted messages without losing draft markers or scroll in ${dark ? 'dark' : 'light'} theme`, async ({
        page,
    }) => {
        const scenario = await prepare(page, dark);
        await page.getByRole('button', {name: ru.draft, exact: true}).click();
        await page.getByRole('menuitem', {name: ru.voice.generateText, exact: true}).click();
        const composer = page.getByRole('textbox', {name: ru.assistantBar.draftText, exact: true});
        await expect(composer).toHaveText('Мой несохранённый ответ');
        const viewport = page.locator('[data-qa="conversation-messages"]');
        await viewport.evaluate((element) => {
            element.scrollTop = 40;
            element.dispatchEvent(new Event('scroll'));
        });
        await expect(page.locator('[data-message-row-id="80"]')).toBeAttached();
        await expect(page.getByRole('status', {name: ru.loadingConversation})).toBeHidden();
        async function settleLayout() {
            await page.evaluate(
                () =>
                    new Promise<void>((resolve) => {
                        requestAnimationFrame(() => requestAnimationFrame(() => resolve()));
                    }),
            );
        }
        await settleLayout();
        const row = page.locator('[data-message-row-id="99"]');
        const bubble = row.locator('[data-message-id="99"]');
        await viewport.evaluate((element) => {
            const target = element.querySelector('[data-message-id="99"]');
            if (target) {
                const bounds = target.getBoundingClientRect();
                element.scrollTop +=
                    bounds.top -
                    element.getBoundingClientRect().top -
                    (element.clientHeight - bounds.height) / 2;
            }
        });
        await settleLayout();
        await expect(bubble).toBeInViewport({ratio: 1});
        expect(
            await bubble.evaluate((element) => {
                const bounds = element.getBoundingClientRect();
                return element.contains(document.elementFromPoint(bounds.left + 24, bounds.top + 16));
            }),
            'the context click reaches the message rather than the sticky date',
        ).toBe(true);
        const top = await viewport.evaluate((element) => element.scrollTop);
        const beforeClick = {
            row: await row.boundingBox(),
            bubble: await bubble.boundingBox(),
            viewport: await viewport.boundingBox(),
        };
        async function assertScrollPreserved(label: string) {
            await settleLayout();
            const position = await viewport.evaluate((element) => {
                const row = element.querySelector('[data-message-row-id="99"]');
                return {
                    top: element.scrollTop,
                    height: element.clientHeight,
                    total: element.scrollHeight,
                    rowTop: row?.getBoundingClientRect().top,
                    rowBottom: row?.getBoundingClientRect().bottom,
                    focusedElement: document.activeElement?.outerHTML.slice(0, 300),
                };
            });
            await test.info().attach(label, {
                body: JSON.stringify({expectedTop: top, ...position}, null, 4),
                contentType: 'application/json',
            });
            expect(position.top, `${label}: ${JSON.stringify({beforeClick, after: position})}`).toBe(top);
        }
        await bubble.click({button: 'right', position: {x: 24, y: 16}});
        await assertScrollPreserved('after-context-click');
        await page.getByRole('menuitem', {name: ru.messageEdit.action, exact: true}).click();
        const dialog = page.getByRole('dialog', {name: ru.messageEdit.titleText, exact: true});
        const editor = dialog.getByRole('textbox', {name: ru.messageEdit.titleText, exact: true});
        await expect(editor).toContainText('Полезная ссылка и секрет.');
        await assertScrollPreserved('after-edit-open');
        await dialog.screenshot({
            path: `../docs/resources/message-edit-${dark ? 'dark' : 'light'}.png`,
            animations: 'disabled',
        });
        await editor.press('Control+End');
        await editor.pressSequentially(' **буквально**');
        await dialog.getByRole('button', {name: ru.messageEdit.save, exact: true}).click();
        await expect(dialog.getByRole('button', {name: ru.saved, exact: true})).toBeVisible();
        await assertScrollPreserved('after-edit-save');
        expect(scenario.requests).toHaveLength(1);
        expect(scenario.requests[0].text).toBe(originalText + ' **буквально**');
        expect(
            scenario.requests[0].entities
                .filter((entity) => entity.kind === 'existing')
                .map((entity) => entity.sourceIndex)
                .sort(),
        ).toEqual([0, 1, 2]);
        await expect(row).toContainText(originalText + ' **буквально**');
        await expect(row.locator('[data-qa="message-ai"]')).toBeVisible();
        await expect(row.locator('[data-qa="message-context-excluded"]')).toBeVisible();
        await expect(row.locator('[data-qa="message-edited"]')).toBeVisible();
        await dialog.getByRole('button', {name: ru.messageEdit.close, exact: true}).click();
        await expect(dialog).toBeHidden();
        await expect(composer).toHaveText('Мой несохранённый ответ');
        await assertScrollPreserved('after-edit-close');
    });
}

test('keeps a same-second edit when an older in-flight history response arrives', async ({page}) => {
    const scenario = await prepare(page, true);
    await page.locator('[data-message-row-id="129"]').click({button: 'right'});
    await page.getByRole('menuitem', {name: ru.messageEdit.action, exact: true}).click();
    const dialog = page.getByRole('dialog', {name: ru.messageEdit.titleCaption, exact: true});
    const editor = dialog.getByRole('textbox', {name: ru.messageEdit.titleCaption, exact: true});
    scenario.hold();
    await expect.poll(scenario.held).toBe(true);
    await editor.press('Control+End');
    await editor.pressSequentially(' Готово');
    await dialog.getByRole('button', {name: ru.messageEdit.save, exact: true}).click();
    await expect(dialog.getByRole('button', {name: ru.saved, exact: true})).toBeVisible();
    const response = page.waitForResponse(
        (value) => new URL(value.url()).pathname === '/api/chats/41/messages',
    );
    scenario.release();
    await response;
    await expect(page.locator('[data-message-row-id="129"]')).toContainText(originalText + ' Готово');
    await expect.poll(() => scenario.editReads()).toBe(2);
    await page.evaluate(
        () =>
            new Promise<void>((resolve) => {
                requestAnimationFrame(() => requestAnimationFrame(() => resolve()));
            }),
    );
    await expect(page.locator('[data-message-row-id="129"]')).toContainText(originalText + ' Готово');
});

test('compact caption editor confirms discard and allows an empty caption', async ({page}) => {
    await page.setViewportSize({width: 520, height: 740});
    const scenario = await prepare(page, true);
    await page.locator('[data-message-row-id="129"]').click({button: 'right'});
    await page.getByRole('menuitem', {name: ru.messageEdit.action, exact: true}).click();
    const dialog = page.getByRole('dialog', {name: ru.messageEdit.titleCaption, exact: true});
    const editor = dialog.getByRole('textbox', {name: ru.messageEdit.titleCaption, exact: true});
    await expect(editor).toContainText('Полезная ссылка и секрет.');
    await dialog.screenshot({path: '../docs/resources/message-edit-compact.png', animations: 'disabled'});
    const bounds = await dialog.boundingBox();
    expect(bounds!.width).toBeLessThanOrEqual(520);
    await editor.press('Control+A');
    await editor.press('Backspace');
    await page.keyboard.press('Escape');
    const confirmation = page.getByRole('dialog', {name: ru.messageEdit.discardTitle, exact: true});
    await expect(confirmation).toBeVisible();
    const keepEditing = confirmation.getByRole('button', {
        name: ru.messageEdit.keepEditing,
        exact: true,
    });
    await expect(keepEditing).toBeFocused();
    await expect(keepEditing).toBeInViewport({ratio: 1});
    const confirmationBounds = await confirmation.boundingBox();
    expect(confirmationBounds!.x).toBeGreaterThanOrEqual(0);
    expect(confirmationBounds!.y).toBeGreaterThanOrEqual(0);
    expect(confirmationBounds!.x + confirmationBounds!.width).toBeLessThanOrEqual(520);
    expect(confirmationBounds!.y + confirmationBounds!.height).toBeLessThanOrEqual(740);
    await page.screenshot({
        path: '../build/message-edit-confirmation-compact.png',
        animations: 'disabled',
    });
    await keepEditing.press('Control+Enter');
    expect(scenario.requests).toHaveLength(0);
    await expect(confirmation).toBeVisible();
    await keepEditing.click();
    await expect(editor).toBeFocused();
    await expect(editor).toHaveText('');
    await dialog.getByRole('button', {name: ru.messageEditFormat.undo, exact: true}).click();
    await expect(editor).toContainText('Полезная ссылка и секрет.');
    await dialog.getByRole('button', {name: ru.messageEditFormat.redo, exact: true}).click();
    await expect(editor).toHaveText('');
    await dialog.getByRole('button', {name: ru.messageEdit.save, exact: true}).click();
    await expect(dialog.getByRole('button', {name: ru.saved, exact: true})).toBeVisible();
    expect(scenario.requests[0].text).toBe('');
    expect(scenario.requests[0].entities).toEqual([]);
    await expect(page.locator('[data-qa="conversation-header"]')).toBeVisible();
});
