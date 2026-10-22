import {expect, test} from '@playwright/test';
import type {Locator, Page} from '@playwright/test';
import type {SpeechAsset} from '@aigram/api-client';
import {copyFileSync, mkdirSync, readFileSync} from 'node:fs';
import {join} from 'node:path';
import {workspaceState} from '../fixtures/workspace';
import {testHistory} from '../fixtures/chats';
import {speechState as installedSpeech} from '../fixtures/speech';
import {installVoiceMocks} from '../fixtures/voiceDemo';
import {chooseSettingsSection, expectSettingsSlider} from '../fixtures/settings';
import ru from '../../src/shared/i18n/ru.json';

const title = 'Синтетический голосовой чат';
const proof = join(process.cwd(), '..', 'build', 'voice-ui');

async function prepare(page: Page, dark: boolean) {
    const state = structuredClone(workspaceState);
    state.settings.voiceInputEnabled = false;
    state.settings.voiceReplyMode = 'text';
    state.settings.voiceReplyProbability = 50;
    state.settings.voiceRecognitionModel = 'whisper-tiny';
    state.settings.voiceSynthesisModel = 'piper-ru-irina';
    state.settings.voiceSpeechRate = 1;
    state.chats[0].title = title;
    const speech = structuredClone(installedSpeech);
    const mocks = await installVoiceMocks(page, {
        state,
        speech,
        history: testHistory('41'),
        audioPath: join(process.cwd(), 'tests', 'fixtures', 'voice.ogg'),
        dark,
    });
    const launch =
        process.env.TIHO_TEST_URL ??
        readFileSync(
            process.env.TIHO_LAUNCH_FILE ?? join(process.env.LOCALAPPDATA ?? '', 'Aigram', 'launch.url'),
            'utf8',
        );
    await page.goto(launch);
    await expect(page.locator('body')).toContainClass('g-root_theme_' + (dark ? 'dark' : 'light'));
    return mocks;
}

async function settings(page: Page) {
    await page.getByRole('button', {name: ru.profileMenu, exact: true}).click();
    const popup = page
        .getByRole('menuitem', {name: ru.settings, exact: true})
        .locator('xpath=ancestor::*[contains(@class,"g-popup")][1]');
    await expect
        .poll(() =>
            popup.evaluate((element) =>
                getComputedStyle(element).getPropertyValue('--g-popup-border-width').trim(),
            ),
        )
        .toBe('0px');
    await page.getByRole('menuitem', {name: ru.settings, exact: true}).click();
    await chooseSettingsSection(page, 'voice');
    return page.getByRole('dialog', {name: ru.settings, exact: true});
}

async function capture(page: Page, target: Locator, name: string, dark: boolean) {
    mkdirSync(proof, {recursive: true});
    for (const width of [1440, 390]) {
        await page.setViewportSize({width, height: 900});
        await page.evaluate(
            () =>
                new Promise<void>((resolve) =>
                    requestAnimationFrame(() => requestAnimationFrame(() => resolve())),
                ),
        );
        await target.scrollIntoViewIfNeeded();
        await expect(target).toBeInViewport();
        await expect
            .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
            .toBe(true);
        if (name === 'manual-voice') {
            await manualBounds(page);
        }
        await page.screenshot({
            path: join(proof, `${name}-${dark ? 'dark' : 'light'}-${width}.png`),
            animations: 'disabled',
        });
        if (dark && width === 1440 && (name === 'voice-settings' || name === 'voice-draft')) {
            copyFileSync(
                join(proof, `${name}-dark-1440.png`),
                join(process.cwd(), '..', 'docs', 'resources', `${name}-dark.png`),
            );
        }
    }
    await page.setViewportSize({width: 1440, height: 900});
}

async function library(page: Page) {
    await page.locator('[data-qa="nav-speech"]').click();
    const view = page.locator('[data-qa="speech-library"]');
    await expect(view).toBeVisible();
    return view;
}

async function prepareTextDraft(page: Page, text: string) {
    await page.getByRole('button', {name: ru.draft, exact: true}).click();
    await page.getByRole('menuitem', {name: ru.voice.generateText, exact: true}).click();
    const editor = page.locator('[data-qa="ai-text-draft"]').getByRole('textbox');
    await expect(editor).toBeEditable();
    await editor.fill(text);
    await expect(editor).toHaveText(text);
    return editor;
}

async function manualBounds(page: Page) {
    const dialog = page.getByRole('dialog', {name: ru.voice.manualTitle, exact: true});
    await expect
        .poll(() =>
            dialog.evaluate((modal) => {
                const bounds = modal.getBoundingClientRect();
                const controls = Array.from(
                    modal.querySelectorAll('button, textarea, [role="combobox"], [role="slider"]'),
                ).map((element) => ({
                    label: element.getAttribute('aria-label') ?? element.textContent,
                    rect: element.getBoundingClientRect(),
                }));
                const outsideControls = controls
                    .filter(
                        ({rect}) =>
                            rect.width > 5 &&
                            rect.height > 5 &&
                            (rect.left < bounds.left || rect.right > bounds.right),
                    )
                    .map(({label, rect}) => ({
                        label,
                        left: rect.left,
                        right: rect.right,
                        modalLeft: bounds.left,
                        modalRight: bounds.right,
                    }));
                const footer = Array.from(modal.querySelectorAll('button'))
                    .filter(
                        (button) =>
                            button.textContent?.includes('Проверить') ||
                            button.textContent?.includes('Озвучить и отправить'),
                    )
                    .map((button) => button.getBoundingClientRect());
                return {
                    noHorizontalOverflow: modal.scrollWidth <= modal.clientWidth,
                    withinViewport:
                        bounds.left >= 0 && bounds.right <= innerWidth && bounds.height <= innerHeight,
                    outsideControls,
                    footerAligned:
                        footer.length === 2 &&
                        Math.abs(footer[0].top - footer[1].top) < 1 &&
                        Math.abs(footer[0].height - footer[1].height) < 1,
                };
            }),
        )
        .toEqual({
            noHorizontalOverflow: true,
            withinViewport: true,
            outsideControls: [],
            footerAligned: true,
        });
}

for (const dark of [false, true]) {
    test(`manual voice composer previews exact text and speaks-and-sends edits without losing the AI text draft in ${dark ? 'dark' : 'light'}`, async ({
        page,
    }) => {
        const {state, speech, sent, drafts} = await prepare(page, dark);
        speech.assets.push({
            ...speech.assets[2],
            id: 'manual-alternative',
            title: 'Синтетический голос для ручной озвучки',
        });
        await page.getByRole('button', {name: title, exact: true}).click();
        await expect(page.getByRole('textbox', {name: ru.messagePlaceholder, exact: true})).toHaveCount(0);
        const textDraft = await prepareTextDraft(page, 'Мой подготовленный черновик');
        await expect(page.getByRole('button', {name: ru.chooseEmoji, exact: true})).toHaveCount(0);
        await expect(page.getByRole('button', {name: 'Стикеры', exact: true})).toHaveCount(0);
        await page.getByRole('button', {name: ru.voice.manualTitle, exact: true}).click();
        const dialog = page.getByRole('dialog', {name: ru.voice.manualTitle, exact: true});
        await expectSettingsSlider(dialog.locator('[data-qa="settings-slider"]'));
        await dialog.getByRole('combobox', {name: ru.voice.models.synthesis, exact: true}).click();
        await page.getByRole('option', {name: 'Синтетический голос для ручной озвучки', exact: true}).click();
        const text = dialog.getByRole('textbox', {name: ru.voice.manualText, exact: true});
        await text.fill('  Дословная проверка 🙂 ');
        await dialog.getByRole('button', {name: ru.voice.previewCheck, exact: true}).click();
        await expect(dialog.locator('audio')).toHaveAttribute('src', '/api/speech/audio/voice-job-2');
        await expect
            .poll(() => dialog.locator('audio').evaluate((element: HTMLAudioElement) => element.duration))
            .toBeGreaterThan(4);
        await dialog.getByRole('button', {name: ru.voice.play, exact: true}).click();
        await expect
            .poll(() => dialog.locator('audio').evaluate((element: HTMLAudioElement) => element.paused))
            .toBe(false);
        await capture(
            page,
            dialog.getByRole('button', {name: ru.voice.manualSpeakAndSend, exact: true}),
            'manual-voice',
            dark,
        );
        await page.setViewportSize({width: 640, height: 520});
        await manualBounds(page);
        await expect(dialog.getByRole('button', {name: ru.close, exact: true})).toBeInViewport();
        await expect(
            dialog.getByRole('button', {name: ru.voice.manualSpeakAndSend, exact: true}),
        ).toBeInViewport();
        await page.setViewportSize({width: 1440, height: 900});
        expect(sent).toEqual([]);
        await text.fill('Изменённый текст для отправки.');
        await expect(dialog.locator('audio')).toHaveCount(0);
        await expect(
            dialog.getByRole('button', {name: ru.voice.manualSpeakAndSend, exact: true}),
        ).toBeEnabled();
        await dialog.getByRole('button', {name: ru.voice.manualSpeakAndSend, exact: true}).click();
        await expect(dialog.getByRole('button', {name: ru.voice.manualSent, exact: true})).toBeDisabled();
        expect(drafts).toEqual([
            {chatId: '41', text: '  Дословная проверка 🙂 ', modelId: 'manual-alternative', speed: 1},
            {chatId: '41', text: 'Изменённый текст для отправки.', modelId: 'manual-alternative', speed: 1},
        ]);
        expect(sent).toEqual([{voiceId: 'voice-job-3'}]);
        expect(state.settings.voiceSynthesisModel).toBe('piper-ru-irina');
        await dialog.getByRole('button', {name: ru.close, exact: true}).click();
        await expect(textDraft).toHaveText('Мой подготовленный черновик');
    });

    test(`manual voice cancellation and closing prevent automatic delivery in ${dark ? 'dark' : 'light'}`, async ({
        page,
    }) => {
        const {controls, sent, requests, drafts} = await prepare(page, dark);
        controls.holdManual = true;
        await page.getByRole('button', {name: title, exact: true}).click();
        await page.getByRole('button', {name: ru.voice.manualTitle, exact: true}).click();
        const dialog = page.getByRole('dialog', {name: ru.voice.manualTitle, exact: true});
        await dialog
            .getByRole('textbox', {name: ru.voice.manualText, exact: true})
            .fill('Этот текст не отправляется.');
        await dialog.getByRole('button', {name: ru.voice.manualSpeakAndSend, exact: true}).click();
        await expect.poll(() => drafts.length).toBe(1);
        await dialog.getByRole('button', {name: ru.voice.cancel, exact: true}).click();
        await expect(dialog.getByText(ru.voice.previewCancelled, {exact: true})).toBeVisible();
        expect(sent).toEqual([]);
        await dialog.getByRole('button', {name: ru.voice.manualSpeakAndSend, exact: true}).click();
        await expect.poll(() => drafts.length).toBe(2);
        await dialog.getByRole('button', {name: ru.close, exact: true}).click();
        await expect(dialog).toHaveCount(0);
        await expect.poll(() => requests.includes('CANCEL voice-job-2')).toBe(true);
        expect(sent).toEqual([]);
    });

    test(`speech catalog tabs and filters preserve selected voices with real card preview in ${dark ? 'dark' : 'light'}`, async ({
        page,
    }) => {
        const {state, speech, previews, sent, controls, requests} = await prepare(page, dark);
        const definitions = JSON.parse(
            readFileSync(
                join(process.cwd(), '..', 'backend', 'src', 'main', 'resources', 'speech', 'catalog.json'),
                'utf8',
            ),
        ) as {asset: Omit<SpeechAsset, 'installed'>}[];
        const catalog = definitions.filter(
            ({asset}) =>
                asset.kind !== 'synthesis' ||
                asset.languages.some((language) => language === 'ru' || language === 'en'),
        );
        const english = catalog.find(
            (entry) => entry.asset.kind === 'synthesis' && entry.asset.languages.includes('en'),
        )?.asset;
        expect(english).toBeDefined();
        if (!english) {
            throw new Error('Shipping catalog must include an English voice for this fixture');
        }
        speech.assets = catalog.map(({asset}) => ({
            ...asset,
            installed:
                asset.id === 'sherpa-runtime' ||
                asset.id === 'whisper-tiny' ||
                asset.id === 'piper-ru-irina' ||
                asset.id === english.id,
        }));
        const dialog = await library(page);
        const catalogView = dialog.locator('[data-qa="speech-assets"]');
        await expect(dialog.locator('[data-qa="speech-preview"]')).toHaveCount(0);
        await expect(dialog.getByRole('tablist').locator('svg')).toHaveCount(0);
        const tabs = dialog.getByRole('tablist').locator('..');
        await expect(tabs).not.toHaveCSS('background-color', 'rgba(0, 0, 0, 0)');
        const card = catalogView.locator('[data-qa="speech-asset"]').first();
        await expect(card).toHaveCSS('border-width', '0px');
        await expect(card).toHaveCSS('border-radius', '16px');
        const source = card.getByRole('link', {name: ru.voice.sourceAndLicense, exact: true});
        const centered = await source.evaluate((link) => {
            const card = link.closest('[data-qa="speech-asset"]')!.getBoundingClientRect();
            const source = link.getBoundingClientRect();
            return Math.abs(source.x + source.width / 2 - card.x - card.width / 2);
        });
        expect(centered).toBeLessThan(1);
        const search = catalogView.getByRole('textbox', {name: ru.voice.searchModels, exact: true});
        expect(
            await search.evaluate(
                (element) => getComputedStyle(element.closest('.g-text-input')!).borderWidth,
            ),
        ).toBe('0px');
        const installedOnly = catalogView.getByRole('switch', {name: ru.voice.installedOnly, exact: true});
        await installedOnly.focus();
        await installedOnly.press('Space');
        await expect(installedOnly).toBeChecked();
        await expect(catalogView.locator('[data-qa="speech-asset"]')).toHaveCount(2);
        await search.fill(english.id);
        await expect(catalogView.locator('[data-qa="speech-asset"]')).toHaveCount(1);
        await search.fill('');
        await installedOnly.press('Space');
        await expect(installedOnly).not.toBeChecked();
        await expect(dialog.getByRole('tab', {name: ru.voice.tabs.synthesis, exact: true})).toHaveAttribute(
            'aria-selected',
            'true',
        );
        await expect(catalogView.locator('[data-qa="speech-asset"]')).toHaveCount(
            catalog.filter(({asset}) => asset.kind === 'synthesis').length,
        );
        await capture(page, search, 'speech-library', dark);
        await search.fill('piper-ru-irina');
        await expect(catalogView.locator('[data-qa="speech-asset"]')).toHaveCount(1);
        await expect(search).toBeFocused();
        await search.fill('');
        await catalogView.getByRole('combobox', {name: ru.voice.languageFilter, exact: true}).click();
        await page
            .getByRole('option', {
                name: new Intl.DisplayNames(['ru'], {type: 'language'}).of('en'),
                exact: true,
            })
            .click();
        await expect(catalogView.locator('[data-qa="speech-asset"]')).toHaveCount(
            catalog.filter(({asset}) => asset.kind === 'synthesis' && asset.languages.includes('en')).length,
        );
        await dialog.getByRole('tab', {name: ru.voice.tabs.runtime, exact: true}).click();
        await expect(catalogView.locator('[data-qa="speech-asset"]')).toHaveCount(
            catalog.filter(({asset}) => asset.kind === 'runtime').length,
        );
        await expect(
            catalogView.getByRole('combobox', {name: ru.voice.languageFilter, exact: true}),
        ).toHaveCount(0);
        await dialog.getByRole('tab', {name: ru.voice.tabs.synthesis, exact: true}).click();
        await expect(
            catalogView.getByRole('combobox', {name: ru.voice.languageFilter, exact: true}),
        ).toContainText(new Intl.DisplayNames(['ru'], {type: 'language'}).of('en') ?? '');
        await catalogView
            .getByRole('button', {name: ru.voice.selectAsset.replace('{name}', english.title), exact: true})
            .click();
        await expect(
            catalogView.getByRole('button', {
                name: ru.voice.selectedAsset.replace('{name}', english.title),
                exact: true,
            }),
        ).toBeDisabled();
        expect(state.settings.voiceSynthesisModel).toBe(english.id);
        await capture(page, search, 'voice-catalog', dark);
        await catalogView
            .getByRole('button', {name: ru.voice.previewAsset.replace('{name}', english.title), exact: true})
            .click();
        const previewDialog = page.getByRole('dialog', {name: ru.voice.previewTitle, exact: true});
        const preview = previewDialog.locator('[data-qa="speech-preview"]');
        await expect(preview.locator('audio')).toHaveAttribute('src', '/api/speech/audio/voice-job-1');
        expect(previews[0]).toMatchObject({modelId: english.id, speed: 1, text: ru.voice.previewSample});
        await expect
            .poll(() => preview.locator('audio').evaluate((element: HTMLAudioElement) => element.duration))
            .toBeGreaterThan(4);
        expect(state.settings.voiceSynthesisModel).toBe(english.id);
        expect(sent).toEqual([]);
        await capture(page, preview, 'voice-catalog-preview', dark);
        await previewDialog.getByRole('button', {name: ru.close, exact: true}).click();
        await expect(dialog.locator('[data-qa="speech-preview"]')).toHaveCount(0);
        controls.holdPreview = true;
        await catalogView
            .getByRole('button', {name: ru.voice.previewAsset.replace('{name}', english.title), exact: true})
            .click();
        await expect(preview.getByText(ru.voice.previewRunning, {exact: true})).toBeVisible();
        await previewDialog.getByRole('button', {name: ru.close, exact: true}).click();
        await expect.poll(() => requests.includes('CANCEL voice-job-2')).toBe(true);
        expect(state.settings.voiceSynthesisModel).toBe(english.id);
        expect(sent).toEqual([]);
    });

    test(`settings test speaks exact text with unsaved voice and speed in ${dark ? 'dark' : 'light'}`, async ({
        page,
    }) => {
        const {state, speech, previews, requests, sent, controls} = await prepare(page, dark);
        speech.assets.push({
            ...speech.assets[2],
            id: 'alternative-voice',
            title: 'Синтетический второй голос',
        });
        await page.evaluate(() => {
            const scope = window as typeof window & {openedSpeechFolders?: string[]};
            scope.openedSpeechFolders = [];
            window.aigramDesktop = {
                state: async () => ({available: true, maximized: false, visible: true, minimized: false}),
                sync: async () => {},
                quit: async () => {},
                action: async () => {},
                onState: () => () => {},
                openStorageLocation: async (id) => {
                    scope.openedSpeechFolders?.push(id);
                },
            };
        });
        const dialog = await settings(page);
        await expectSettingsSlider(dialog.locator('[data-qa="settings-slider"]'));
        await dialog.getByRole('combobox', {name: ru.voice.models.synthesis, exact: true}).click();
        await page.getByRole('option', {name: 'Синтетический второй голос', exact: true}).click();
        await dialog.getByRole('slider', {name: ru.voice.speechRate, exact: true}).press('ArrowRight');
        const preview = dialog.locator('[data-qa="speech-preview"]');
        const input = preview.getByRole('textbox', {name: ru.voice.previewText, exact: true});
        await input.fill('');
        await expect(preview.getByRole('button', {name: ru.voice.previewCheck, exact: true})).toBeDisabled();
        await input.fill('  Проверяем выбранный голос 🙂 ');
        await preview.getByRole('button', {name: ru.voice.previewCheck, exact: true}).click();
        await expect(preview.locator('audio')).toHaveAttribute('src', '/api/speech/audio/voice-job-1');
        await expect
            .poll(() => preview.locator('audio').evaluate((element: HTMLAudioElement) => element.duration))
            .toBeGreaterThan(4);
        expect(previews).toEqual([
            {text: '  Проверяем выбранный голос 🙂 ', modelId: 'alternative-voice', speed: 1.1},
        ]);
        expect(state.settings.voiceSynthesisModel).toBe('piper-ru-irina');
        expect(state.settings.voiceSpeechRate).toBe(1);
        expect(sent).toEqual([]);
        await preview.getByRole('button', {name: ru.voice.play, exact: true}).click();
        await expect
            .poll(() => preview.locator('audio').evaluate((element: HTMLAudioElement) => element.paused))
            .toBe(false);
        await capture(page, preview, 'voice-settings-preview', dark);
        await input.fill('Вторая проверка');
        await expect(preview.locator('audio')).toHaveCount(0);
        controls.holdPreview = true;
        await preview.getByRole('button', {name: ru.voice.previewCheck, exact: true}).click();
        await expect.poll(() => previews.length).toBe(2);
        await preview.getByRole('button', {name: ru.voice.cancel, exact: true}).click();
        await expect(preview.getByText(ru.voice.previewCancelled, {exact: true})).toBeVisible();
        expect(requests).toContain('CANCEL voice-job-2');
        await preview.getByRole('button', {name: ru.voice.previewCheck, exact: true}).click();
        await expect.poll(() => previews.length).toBe(3);
        await dialog.getByRole('button', {name: ru.close, exact: true}).click();
        await expect.poll(() => requests.includes('CANCEL voice-job-3')).toBe(true);
        const catalog = await library(page);
        const folder = catalog
            .locator('[data-qa="speech-toolbar"]')
            .getByRole('button', {name: ru.voice.openFolder, exact: true});
        await expect(folder).toBeEnabled();
        await folder.click();
        expect(
            await page.evaluate(
                () => (window as typeof window & {openedSpeechFolders?: string[]}).openedSpeechFolders,
            ),
        ).toEqual(['speech']);

        expect(sent).toEqual([]);
    });

    test(`compatible custom voice import refreshes the selectable catalog in ${dark ? 'dark' : 'light'}`, async ({
        page,
    }) => {
        const {imports, uploads, sent} = await prepare(page, dark);
        const dialog = await library(page);
        const importToggle = dialog
            .locator('[data-qa="speech-toolbar"]')
            .getByRole('button', {name: ru.voice.importModel, exact: true});
        await expect(importToggle).toHaveAttribute('aria-expanded', 'false');
        await expect(dialog.getByRole('region', {name: ru.voice.importModel, exact: true})).toHaveCount(0);
        await importToggle.click();
        await expect(importToggle).toHaveAttribute('aria-expanded', 'true');
        await expect(dialog.getByRole('region', {name: ru.voice.importModel, exact: true})).toBeVisible();
        const form = dialog.locator('[data-qa="speech-model-import"]');
        await form.locator('input[type="file"]').setInputFiles({
            name: 'synthetic-voice.tar.bz2',
            mimeType: 'application/x-bzip2',
            buffer: Buffer.from('Synthetic model archive for mocked import endpoint'),
        });
        await form.getByRole('textbox', {name: ru.voice.importName, exact: true}).fill('Мой тестовый голос');
        await form.getByRole('textbox', {name: ru.voice.importLanguage, exact: true}).fill('ru');
        await form
            .getByRole('textbox', {name: ru.voice.importLicense, exact: true})
            .fill('Synthetic fixture, not a distributable model');
        await capture(
            page,
            form.getByRole('button', {name: ru.voice.importSubmit, exact: true}),
            'voice-import',
            dark,
        );
        await form.getByRole('button', {name: ru.voice.importSubmit, exact: true}).click();
        await expect(form.getByRole('status')).toHaveText(ru.voice.importReady);
        expect(uploads).toEqual(['synthetic-voice.tar.bz2']);
        expect(imports).toEqual([
            {
                uploadId: '00000000-0000-0000-0000-000000000001',
                title: 'Мой тестовый голос',
                language: 'ru',
                license: 'Synthetic fixture, not a distributable model',
            },
        ]);
        await expect(dialog.getByText(ru.voice.imported, {exact: true})).toBeVisible();
        await dialog
            .getByRole('button', {
                name: ru.voice.selectAsset.replace('{name}', 'Мой тестовый голос'),
                exact: true,
            })
            .click();
        await expect(
            dialog.getByRole('button', {
                name: ru.voice.selectedAsset.replace('{name}', 'Мой тестовый голос'),
                exact: true,
            }),
        ).toBeDisabled();
        expect(sent).toEqual([]);
    });

    test(`voice preferences and explicit asset downloads in ${dark ? 'dark' : 'light'}`, async ({page}) => {
        const {state, speech, requests} = await prepare(page, dark);
        speech.assets.forEach((asset) => {
            asset.installed = false;
        });
        const dialog = await settings(page);
        await expect(
            dialog
                .locator('[data-qa="speech-preview"]')
                .getByRole('button', {name: ru.voice.previewCheck, exact: true}),
        ).toBeDisabled();
        await expect(
            dialog.getByRole('switch', {name: ru.voice.recognitionEnabled, exact: true}),
        ).not.toBeChecked();
        expect(requests).toEqual([]);
        await dialog.getByRole('switch', {name: ru.voice.recognitionEnabled, exact: true}).press('Space');
        await chooseSettingsSection(page, 'replies');
        await dialog.getByRole('combobox', {name: ru.voice.replyMode, exact: true}).click();
        await page.getByRole('option', {name: ru.voice.replyModes.random, exact: true}).click();
        await dialog.getByRole('slider', {name: ru.voice.probability, exact: true}).press('End');
        await dialog.getByRole('button', {name: ru.save, exact: true}).click();
        await expect(dialog.getByRole('button', {name: ru.saved, exact: true})).toBeVisible();
        expect(state.settings).toMatchObject({
            voiceInputEnabled: true,
            voiceReplyMode: 'random',
            voiceReplyProbability: 100,
        });
        await dialog.getByRole('button', {name: ru.close, exact: true}).click();
        const catalog = await library(page);
        await catalog.getByRole('tab', {name: ru.voice.tabs.runtime, exact: true}).click();
        const download = catalog.getByRole('button', {
            name: ru.voice.installAsset.replace('{name}', speech.assets[0].title),
            exact: true,
        });
        const initialHeight = await download.evaluate((element) => element.getBoundingClientRect().height);
        expect(initialHeight).toBe(36);
        await download.click();
        await expect(
            catalog.getByRole('progressbar', {
                name: ru.voice.assetProgress.replace('{name}', speech.assets[0].title),
                exact: true,
            }),
        ).toHaveAttribute('aria-valuenow', '25');
        await expect
            .poll(() =>
                catalog
                    .getByRole('button', {
                        name: ru.voice.cancelAsset.replace('{name}', speech.assets[0].title),
                        exact: true,
                    })
                    .evaluate((element) => element.getBoundingClientRect().height),
            )
            .toBe(initialHeight);
        await capture(page, catalog.locator('[data-qa="speech-assets"]'), 'voice-settings', dark);
        await catalog
            .getByRole('button', {
                name: ru.voice.cancelAsset.replace('{name}', speech.assets[0].title),
                exact: true,
            })
            .click();
        await expect(catalog.getByText(ru.voice.phases.cancelled, {exact: true})).toBeVisible();
        expect(requests).toEqual(['POST sherpa-runtime', 'DELETE sherpa-runtime']);
    });

    test(`voice draft plays real audio and edits resynthesize before sending in ${dark ? 'dark' : 'light'}`, async ({
        page,
    }) => {
        const {sent} = await prepare(page, dark);
        await page.getByRole('button', {name: title, exact: true}).click();
        const textDraft = await prepareTextDraft(page, 'Мой подготовленный текст');
        await page.getByRole('button', {name: ru.draft, exact: true}).click();
        const generate = page.waitForRequest(
            (request) =>
                new URL(request.url()).pathname === '/api/preview/jobs' && request.method() === 'POST',
        );
        await page.getByRole('menuitem', {name: ru.voice.generateVoice, exact: true}).click();
        expect((await generate).postDataJSON()).toMatchObject({output: 'voice'});
        const preview = page.locator('[data-qa="voice-draft"]');
        await expect(preview).toBeVisible();
        await expect
            .poll(() => preview.locator('audio').evaluate((element: HTMLAudioElement) => element.readyState))
            .toBeGreaterThan(0);
        await expect
            .poll(() => preview.locator('audio').evaluate((element: HTMLAudioElement) => element.duration))
            .toBeGreaterThan(4);
        await expect(preview.locator('audio')).toHaveAttribute('src', '/api/speech/audio/voice-job-2');
        await preview.getByRole('button', {name: ru.voice.play, exact: true}).click();
        await expect
            .poll(() => preview.locator('audio').evaluate((element: HTMLAudioElement) => element.paused))
            .toBe(false);
        const transcript = preview.getByRole('textbox', {name: ru.voice.transcript, exact: true});
        await transcript.fill('Изменённый текст для озвучки.');
        await expect(preview.getByRole('button', {name: ru.voice.sendDraft, exact: true})).toBeDisabled();
        await expect(preview.locator('audio')).toHaveCount(0);
        const synthesis = page.waitForRequest(
            (request) => new URL(request.url()).pathname === '/api/speech/drafts',
        );
        await preview.getByRole('button', {name: ru.voice.regenerate, exact: true}).click();
        expect((await synthesis).postDataJSON()).toEqual({
            chatId: '41',
            text: 'Изменённый текст для озвучки.',
        });
        await expect(preview.getByRole('button', {name: ru.voice.sendDraft, exact: true})).toBeEnabled();
        await capture(page, preview, 'voice-draft', dark);
        await preview.getByRole('button', {name: ru.voice.sendDraft, exact: true}).click();
        await expect.poll(() => sent.length).toBe(1);
        expect(sent[0]).toEqual({voiceId: 'voice-job-3'});
        await expect(preview).toHaveCount(0);
        await expect(textDraft).toHaveText('Мой подготовленный текст');
    });
}
