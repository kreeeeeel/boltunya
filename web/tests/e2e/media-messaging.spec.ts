import {expect, test} from '@playwright/test';
import {readFileSync} from 'node:fs';
import {join} from 'node:path';
import {workspaceState} from '../fixtures/workspace';
import type {Message} from '../../src/shared/api/client';
import ru from '../../src/shared/i18n/ru.json';
import labels from '../../src/modules/media/i18n/ru.json';

test.beforeEach(async ({page}) => {
    const state = structuredClone(workspaceState);
    state.chats = [
        {
            ...state.chats[0],
            kind: 'group',
            typingUsers: [
                {id: '101', name: 'Анна', action: 'typing'},
                {id: '102', name: 'Марк', action: 'typing'},
            ],
        },
    ];
    const messages: Message[] = [1, 2].map((id) => ({
        id: String(id),
        chatId: '41',
        text: `Synthetic photo ${id}`,
        outgoing: false,
        date: 1792064000 + id,
        kind: 'photo',
        senderName: 'Анна',
        media: {
            fileId: id,
            mimeType: 'image/png',
            fileName: `photo-${id}.png`,
            width: 600,
            height: 400,
            thumbnailFileId: 0,
            size: 2000,
            downloaded: true,
        },
    }));
    let polls = 0;
    await page.route('**/api/**', async (route) => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        if (path === '/api/state') {
            await route.fulfill({json: state});
        } else if (path === '/api/appearance') {
            await route.fulfill({json: {mode: 'dark', tokens: {}, css: ''}});
        } else if (path === '/api/model-profiles') {
            await route.fulfill({json: {profiles: [], presets: []}});
        } else if (path === '/api/chats/41/messages') {
            await route.fulfill({json: messages});
        } else if (path === '/api/chats/41/gallery') {
            await route.fulfill({json: {messages, hasMoreBefore: false, hasMoreAfter: false}});
        } else if (path.startsWith('/api/media/')) {
            await route.fulfill({
                contentType: 'image/png',
                body: readFileSync(join(process.cwd(), 'public/assets/illustrations/welcome.png')),
            });
        } else if (path === '/api/preview/jobs') {
            state.activity = [
                {
                    id: 'draft-job',
                    chatId: '41',
                    title: state.chats[0].title,
                    status: 'queued',
                    kind: 'draft',
                    detail: 'qwen3:1.7b',
                    date: 1792064000,
                    durationMs: 0,
                    feedbackAvailable: false,
                    feedbackRating: null,
                    phase: 'queued',
                    queuePosition: 1,
                },
            ];
            await route.fulfill({json: {id: 'draft-job', status: 'queued', result: null, error: ''}});
        } else if (path === '/api/preview/jobs/draft-job') {
            polls++;
            const ready = polls >= 3;
            state.activity[0] = {
                ...state.activity[0],
                status: ready ? 'ready' : 'generating',
                phase: 'reply',
            };
            await route.fulfill({
                json: {
                    id: 'draft-job',
                    status: ready ? 'ready' : 'generating',
                    result: ready ? {text: 'Готовый черновик', durationMs: 1200, tokensPerSecond: 20} : null,
                    error: '',
                },
            });
        } else if (path === '/api/chats/41/messages/1/context') {
            await route.fulfill({json: {ok: true}});
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
    await page.getByRole('button', {name: state.chats[0].title, exact: true}).click();
});

test('photo viewer supports navigation and zoom and Escape leaves the chat open', async ({page}) => {
    await page.getByRole('button', {name: ru.openPhoto, exact: true}).first().click();
    const viewer = page.getByRole('dialog', {name: labels.open, exact: true});
    await expect(viewer).toBeVisible();
    await viewer.getByRole('button', {name: labels.zoomIn, exact: true}).click();
    await expect(viewer.getByRole('button', {name: labels.reset, exact: true})).toHaveText('150%');
    await page.keyboard.press('ArrowRight');
    await expect(viewer.getByText('Synthetic photo 2', {exact: true})).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(viewer).not.toBeVisible();
    await expect(page.locator('[data-qa="conversation-header"]')).toBeVisible();
});

test('manual generation uses a queue card while keeping the composer visible', async ({page}) => {
    await page.getByRole('button', {name: ru.draft, exact: true}).click();
    await expect(page.locator('[data-qa="conversation-generation"]')).toBeVisible();
    await expect(page.getByRole('textbox', {name: ru.messagePlaceholder, exact: true})).toBeVisible();
    await expect(page.locator('[data-qa="message-composer"] .g-skeleton')).toHaveCount(0);
    await expect(page.getByRole('textbox', {name: ru.messagePlaceholder, exact: true})).toHaveValue(
        'Готовый черновик',
    );
});

test('nested context menu marks a message without removing it and group typing identifies participants', async ({
    page,
}) => {
    await expect(page.locator('[data-qa="conversation-header"] [data-qa="typing-indicator"]')).toContainText(
        'Анна',
    );
    await page.locator('[data-message-id="1"]').click({button: 'right'});
    await page.getByRole('menuitem', {name: ru.messageMenu.exclude, exact: true}).hover();
    await page.getByRole('menuitem', {name: ru.messageMenu.scopes.message, exact: true}).click();
    await expect(page.locator('[data-message-id="1"] [data-qa="message-context-excluded"]')).toBeVisible();
    await expect(page.getByText('Synthetic photo 1', {exact: true})).toBeVisible();
});
