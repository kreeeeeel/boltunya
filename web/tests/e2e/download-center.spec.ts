import {expect, test} from '@playwright/test';
import type {Locator, Page} from '@playwright/test';
import {mkdirSync, readFileSync} from 'node:fs';
import {join} from 'node:path';
import {workspaceState} from '../fixtures/workspace';
import {speechState} from '../fixtures/speech';
import {installVoiceMocks} from '../fixtures/voiceDemo';
import ru from '../../src/shared/i18n/ru.json';

const proof = join(process.cwd(), '..', 'build', 'download-center-ui');

async function prepare(page: Page, speechTransfer = false) {
    const state = structuredClone(workspaceState);
    const speech = structuredClone(speechState);
    if (speechTransfer) {
        speech.downloads = [
            {
                assetId: 'piper-ru-irina',
                phase: 'downloading',
                downloadedBytes: 25_000_000,
                totalBytes: 100_000_000,
                error: '',
            },
        ];
    }
    await page.addInitScript(() => {
        window.aigramDesktop = {
            state: async () => ({available: true, maximized: false, visible: true, minimized: false}),
            sync: async () => {},
            quit: async () => {},
            openStorageLocation: async () => {},
            action: async () => {},
            onState: () => () => {},
        };
    });
    const mocks = await installVoiceMocks(page, {
        state,
        speech,
        history: [],
        audioPath: join(process.cwd(), 'tests', 'fixtures', 'voice.ogg'),
        dark: speechTransfer,
    });
    const launch =
        process.env.TIHO_TEST_URL ??
        readFileSync(
            process.env.TIHO_LAUNCH_FILE ?? join(process.env.LOCALAPPDATA ?? '', 'Aigram', 'launch.url'),
            'utf8',
        );
    return {mocks, state, speech, launch};
}

async function expectTransfer(page: Page, title: string, percent: number) {
    const button = page.locator('[data-qa="titlebar-download"]');
    const progress = button.getByRole('progressbar', {name: title, exact: true});
    await expect(button).toBeVisible();
    await expect(button).toHaveCSS('height', '28px');
    await expect(progress).toHaveAttribute('aria-valuenow', String(percent));
    await expect
        .poll(() =>
            button.evaluate((element) => {
                const fill = element.querySelector<HTMLElement>(':scope > [aria-hidden="true"]')!;
                return Math.abs(
                    (fill.getBoundingClientRect().width / element.clientWidth) * 100 -
                        Number(element.querySelector('[role="progressbar"]')!.getAttribute('aria-valuenow')),
                );
            }),
        )
        .toBeLessThan(1);
    return button;
}

async function captureGeometry(page: Page, button: Locator, name: string) {
    mkdirSync(proof, {recursive: true});
    for (const width of [880, 1440, 390]) {
        await page.setViewportSize({width, height: 900});
        await expect
            .poll(() =>
                button.evaluate((element) => {
                    const bar = element.closest('[data-qa="window-bar"]')!;
                    const bounds = element.getBoundingClientRect();
                    const barBounds = bar.getBoundingClientRect();
                    const brand = bar.children[0].getBoundingClientRect();
                    const controls = bar.children[2].getBoundingClientRect();
                    return {
                        height: bounds.height,
                        verticalCenter:
                            Math.abs(bounds.top + bounds.height / 2 - barBounds.top - barBounds.height / 2) <
                            1,
                        centered:
                            innerWidth <= 760 ||
                            Math.abs(bounds.left + bounds.width / 2 - barBounds.left - barBounds.width / 2) <
                                1,
                        contained: bounds.left >= barBounds.left && bounds.right <= barBounds.right,
                        separate: bounds.left >= brand.right && bounds.right <= controls.left,
                        noOverflow: document.documentElement.scrollWidth <= innerWidth,
                    };
                }),
            )
            .toEqual({
                height: 28,
                verticalCenter: true,
                centered: true,
                contained: true,
                separate: true,
                noOverflow: true,
            });
        await page.mouse.move(0, 100);
        await page.screenshot({path: join(proof, `${name}-${width}.png`), animations: 'disabled'});
    }
}

async function expectHoverCancellation(button: Locator) {
    const progress = button.getByRole('progressbar');
    const cancellation = button.locator(':scope > span').last();
    await expect(progress).toHaveCSS('opacity', '1');
    await expect(cancellation).toHaveCSS('opacity', '0');
    await button.hover();
    await expect(progress).toHaveCSS('opacity', '0');
    await expect(cancellation).toHaveCSS('opacity', '1');
    await expect(cancellation).toHaveText(ru.cancelDownloadAction);
}

test('engine and model transfers stay centered and cancel through the model API', async ({page}) => {
    const {state, launch} = await prepare(page);
    const cancellations: string[] = [];
    await page.route('**/api/models/cancel', async (route) => {
        expect(route.request().method()).toBe('POST');
        cancellations.push(state.engine.downloading);
        state.engine.downloading = '';
        await route.fulfill({json: {ok: true}});
    });
    for (const [id, title] of [
        ['Ollama', 'Ollama'],
        ['gemma3:4b', 'Gemma 3 · 4B'],
    ]) {
        state.engine.downloading = id;
        state.engine.progress = 38;
        state.engine.downloadedBytes = 38_000_000;
        state.engine.totalBytes = 100_000_000;
        await page.goto(launch);
        const button = await expectTransfer(page, title, 38);
        await captureGeometry(page, button, id === 'Ollama' ? 'engine-light' : 'model-light');
        state.engine.progress = 62;
        state.engine.downloadedBytes = 62_000_000;
        await expectTransfer(page, title, 62);
        await expectHoverCancellation(button);
        await button.click();
        await expect.poll(() => cancellations.at(-1)).toBe(id);
        await expect(button).toHaveCount(0);
    }
    expect(cancellations).toEqual(['Ollama', 'gemma3:4b']);
});

test('speech transfer uses a filled compact button and cancels the exact speech asset', async ({page}) => {
    const {speech, mocks, launch} = await prepare(page, true);
    await page.goto(launch);
    const button = await expectTransfer(page, speech.assets[2].title, 25);
    await captureGeometry(page, button, 'speech-dark');
    speech.downloads[0].downloadedBytes = 50_000_000;
    await expectTransfer(page, speech.assets[2].title, 50);
    await expectHoverCancellation(button);
    const cancellation = page.waitForRequest(
        (request) =>
            request.method() === 'DELETE' &&
            new URL(request.url()).pathname === '/api/speech/assets/piper-ru-irina/install',
    );
    await button.click();
    await cancellation;
    await expect.poll(() => mocks.requests.includes('DELETE piper-ru-irina')).toBe(true);
    await expect(button).toHaveCount(0);
    expect(mocks.requests.filter((request) => request.startsWith('DELETE '))).toEqual([
        'DELETE piper-ru-irina',
    ]);
});
