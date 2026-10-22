import {expect, test} from '@playwright/test';
import type {Locator, Page} from '@playwright/test';
import type {SpeechAsset, SpeechState} from '@aigram/api-client';
import {mkdirSync, readFileSync} from 'node:fs';
import {join} from 'node:path';
import {workspaceState} from '../fixtures/workspace';
import {speechState} from '../fixtures/speech';
import {installVoiceMocks} from '../fixtures/voiceDemo';
import en from '../../src/shared/i18n/en.json';
import ru from '../../src/shared/i18n/ru.json';

function installedRvcSpeech(locale: 'en' | 'ru'): SpeechState {
    const definitions = JSON.parse(
        readFileSync('../backend/src/main/resources/speech/catalog.json', 'utf8'),
    ) as {asset: Omit<SpeechAsset, 'installed'>}[];
    const speech = structuredClone(speechState);
    speech.assets = speech.assets.map((asset) => ({
        ...asset,
        title: locale === 'en' && asset.id === 'piper-ru-irina' ? 'Piper · Irina · Russian' : asset.title,
    }));
    speech.assets.push(
        ...definitions
            .filter(({asset}) => asset.id === 'applio-runtime' || asset.id === 'rvc-alice')
            .map(({asset}) => ({
                ...asset,
                title:
                    locale === 'en' && asset.id === 'rvc-alice'
                        ? 'Alice · YandexAliceRVC · community'
                        : asset.title,
                installed: true,
            })),
    );
    return speech;
}

async function section(
    page: Page,
    messages: typeof en | typeof ru,
    name: 'voice' | 'application' | 'compute',
) {
    const dialog = page.getByRole('dialog', {name: messages.settings, exact: true});
    const navigation = dialog.getByRole('combobox', {name: messages.settingsCenter.navigation, exact: true});
    if (await navigation.isVisible()) {
        await navigation.click();
        await page.getByRole('option', {name: messages.settingsCenter.sections[name], exact: true}).click();
    } else {
        await dialog
            .getByRole('menuitem', {name: messages.settingsCenter.sections[name], exact: true})
            .click();
    }
    return dialog;
}

async function capture(page: Page, panel: Locator, locale: 'en' | 'ru', dark: boolean) {
    const directory = join(process.cwd(), '..', 'build', 'rvc-device-ui');
    mkdirSync(directory, {recursive: true});
    for (const width of [1440, 390]) {
        await page.setViewportSize({width, height: 900});
        await page.evaluate(() => document.fonts.ready);
        await panel.scrollIntoViewIfNeeded();
        await expect(panel).toBeInViewport({ratio: 1});
        const bounds = await panel.boundingBox();
        const footer = await page.locator('[data-qa="settings-actions"]:visible').boundingBox();
        expect(bounds).not.toBeNull();
        expect(footer).not.toBeNull();
        expect(bounds!.x).toBeGreaterThanOrEqual(0);
        expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(width);
        expect(bounds!.y + bounds!.height).toBeLessThanOrEqual(footer!.y + 1);
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await expect(page.getByRole('alert')).toHaveCount(0);
        await page.screenshot({
            path: join(directory, `rvc-device-${locale}-${dark ? 'dark' : 'light'}-${width}.png`),
            animations: 'disabled',
        });
        if (!dark && width === 1440) {
            const documents = join(process.cwd(), '..', 'docs', 'resources', locale);
            mkdirSync(documents, {recursive: true});
            await panel.screenshot({path: join(documents, 'voice-rvc-device.png'), animations: 'disabled'});
        }
    }
}

for (const locale of ['en', 'ru'] as const) {
    for (const dark of [false, true]) {
        test(`saves the RVC device before preview and probes only on panel demand in ${locale} ${dark ? 'dark' : 'light'}`, async ({
            page,
            context,
        }) => {
            test.setTimeout(60000);
            const messages = locale === 'en' ? en : ru;
            const base = process.env.TIHO_TEST_URL ?? 'http://127.0.0.1:3100';
            await context.addCookies([
                {name: 'tiho-lang', value: locale, url: base},
                {name: 'tiho-theme', value: dark ? 'dark' : 'light', url: base},
            ]);
            const state = structuredClone(workspaceState);
            state.chats = [];
            state.telegram.name = locale === 'en' ? 'Demo profile' : 'Демо-профиль';
            state.settings.voiceConversionDevice = 'cpu';
            state.settings.voiceSynthesisModel = 'rvc-alice';
            const calls: string[] = [];
            page.on('request', (request) => {
                if (new URL(request.url()).pathname === '/api/speech/compute') {
                    calls.push(request.method());
                }
            });
            const mocks = await installVoiceMocks(page, {
                state,
                speech: installedRvcSpeech(locale),
                history: [],
                audioPath: join(process.cwd(), 'tests', 'fixtures', 'voice.ogg'),
                dark,
            });
            await page.goto(base);
            await expect(page.locator('body')).toContainClass('g-root_theme_' + (dark ? 'dark' : 'light'));
            await page.getByRole('button', {name: messages.profileMenu, exact: true}).click();
            await page.getByRole('menuitem', {name: messages.settings, exact: true}).click();
            await expect(page.getByRole('dialog', {name: messages.settings, exact: true})).toBeVisible();
            expect(calls).toEqual([]);
            expect(mocks.previews).toEqual([]);
            const dialog = await section(page, messages, 'voice');
            const panel = dialog.locator('[data-qa="rvc-device-panel"]');
            expect(calls).toEqual([]);
            const preview = dialog.locator('[data-qa="speech-preview"]');
            const text = preview.getByRole('textbox', {name: messages.voice.previewText, exact: true});
            const sample =
                locale === 'en' ? 'Keep this exact preview text 🙂' : 'Сохраните этот текст проверки 🙂';
            await text.fill(sample);
            await preview.getByRole('button', {name: messages.voice.previewCheck, exact: true}).click();
            await expect(preview.locator('[data-qa="speech-player"]')).toBeVisible();
            await expect
                .poll(() => preview.locator('audio').evaluate((audio: HTMLAudioElement) => audio.duration))
                .toBeGreaterThan(4);
            expect(mocks.previews).toHaveLength(1);
            await section(page, messages, 'compute');
            await expect(panel.getByText(messages.voice.rvcDevice.available, {exact: true})).toBeVisible();
            expect(calls).toEqual(['GET']);
            await panel.getByRole('combobox', {name: messages.voice.rvcDevice.label, exact: true}).click();
            await page.getByRole('option', {name: messages.voice.rvcDevice.cuda, exact: true}).click();
            await section(page, messages, 'voice');
            await expect(
                preview.getByRole('button', {name: messages.voice.previewCheck, exact: true}),
            ).toBeDisabled();
            await expect(
                preview.getByText(messages.voice.rvcDevice.saveBeforePreview, {exact: true}),
            ).toBeVisible();
            await expect(preview.locator('[data-qa="speech-player"]')).toHaveCount(0);
            expect(state.settings.voiceConversionDevice).toBe('cpu');
            await dialog.getByRole('button', {name: messages.save, exact: true}).click();
            await expect(dialog.getByRole('button', {name: messages.saved, exact: true})).toBeVisible();
            await expect(
                preview.getByRole('button', {name: messages.voice.previewCheck, exact: true}),
            ).toBeEnabled();
            await expect(text).toHaveValue(sample);
            await expect(preview.locator('[data-qa="speech-player"]')).toHaveCount(0);
            expect(state.settings.voiceConversionDevice).toBe('cuda');
            expect(calls).toEqual(['GET']);
            expect(mocks.previews).toHaveLength(1);
            await preview.getByRole('button', {name: messages.voice.previewCheck, exact: true}).click();
            await expect(preview.locator('[data-qa="speech-player"]')).toBeVisible();
            expect(mocks.previews).toEqual([
                {text: sample, modelId: 'rvc-alice', speed: 1},
                {text: sample, modelId: 'rvc-alice', speed: 1},
            ]);
            await section(page, messages, 'application');
            expect(calls).toEqual(['GET']);
            await section(page, messages, 'compute');
            await expect(panel.getByText(messages.voice.rvcDevice.available, {exact: true})).toBeVisible();
            expect(calls).toEqual(['GET', 'GET']);
            await panel.getByRole('button', {name: messages.voice.rvcDevice.refresh, exact: true}).click();
            await expect(
                panel.getByRole('button', {name: messages.voice.rvcDevice.refresh, exact: true}),
            ).toBeEnabled();
            expect(calls).toEqual(['GET', 'GET', 'GET']);
            expect(await preview.locator('textarea').inputValue()).toBe(sample);
            await expect(
                panel.getByRole('combobox', {name: messages.voice.rvcDevice.label, exact: true}),
            ).toContainText(messages.voice.rvcDevice.cuda);
            expect(mocks.requests).toEqual([]);
            expect(mocks.sent).toEqual([]);
            await capture(page, panel, locale, dark);
        });
    }
}
