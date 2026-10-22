import {expect, test} from '@playwright/test';
import type {Page} from '@playwright/test';
import type {Message, SpeechAsset, SpeechState} from '@aigram/api-client';
import {mkdirSync, readFileSync} from 'node:fs';
import {join} from 'node:path';
import {workspaceState} from '../fixtures/workspace';
import {installVoiceMocks} from '../fixtures/voiceDemo';
import {persistTestTheme} from '../fixtures/settings';
import en from '../../src/shared/i18n/en.json';
import ru from '../../src/shared/i18n/ru.json';

const englishTitles: Readonly<Record<string, string>> = {
    'piper-ru-irina': 'Piper · Irina · Russian',
    'piper-ru-ruslan': 'Piper · Ruslan · Russian',
    'piper-ru-denis': 'Piper · Denis · Russian',
    'piper-ru-dmitri': 'Piper · Dmitri · Russian',
    'rvc-alice': 'Alice · YandexAliceRVC · community',
    'rvc-bratishkinoff': 'Bratishkin · Snowie V3',
    'rvc-johnny-silverhand-ru': 'Johnny Silverhand · Cyberpunk 2077 · Russian',
    'rvc-stas-ai-kak-prosto': 'Stas Ai Kak Prosto',
    'rvc-stint': 'Stint',
};

function documentationSpeech(locale: 'en' | 'ru'): SpeechState {
    const definitions = JSON.parse(
        readFileSync('../backend/src/main/resources/speech/catalog.json', 'utf8'),
    ) as {asset: Omit<SpeechAsset, 'installed'>}[];
    return {
        assets: definitions
            .filter(
                ({asset}) =>
                    asset.kind !== 'synthesis' ||
                    asset.languages.some((language) => language === 'ru' || language === 'en'),
            )
            .map(({asset}) => ({
                ...asset,
                title: locale === 'en' ? (englishTitles[asset.id] ?? asset.title) : asset.title,
                installed: ['sherpa-runtime', 'whisper-tiny', 'piper-ru-irina', 'piper-en-amy'].includes(
                    asset.id,
                ),
            })),
        downloads: [],
    };
}

async function capture(page: Page, locale: 'en' | 'ru', name: string) {
    await page.evaluate(() => document.fonts.ready);
    const alerts = await page.getByRole('alert').allTextContents();
    expect(alerts.filter((text) => text.trim())).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    if (locale === 'en') {
        const foreignText = Array.from(await page.locator('body').innerText())
            .filter((character) => {
                const code = character.charCodeAt(0);
                return code >= 0x0400 && code <= 0x052f;
            })
            .join('');
        expect(foreignText, 'English documentation must not show untranslated Cyrillic text').toBe('');
    }
    const directory = join(process.cwd(), '..', 'docs', 'resources', locale);
    mkdirSync(directory, {recursive: true});
    await page.screenshot({path: join(directory, name + '.png'), animations: 'disabled'});
}

for (const locale of ['en', 'ru'] as const) {
    test(`captures six current ${locale.toUpperCase()} documentation screens with synthetic conversations`, async ({
        page,
        context,
    }) => {
        test.setTimeout(90000);
        const messages = locale === 'en' ? en : ru;
        const base = process.env.TIHO_TEST_URL ?? 'http://127.0.0.1:3100';
        await context.addCookies([
            {name: 'tiho-lang', value: locale, url: base},
            {name: 'tiho-theme', value: 'light', url: base},
        ]);
        const state = structuredClone(workspaceState);
        state.telegram = {
            state: 'ready',
            name: locale === 'en' ? 'Jamie Cooper' : 'Алексей Орлов',
            userId: '100',
            prompt: '',
            error: '',
        };
        state.settings.voiceSynthesisModel = 'piper-ru-irina';
        state.settings.voiceRecognitionModel = 'whisper-tiny';
        state.settings.voiceSpeechRate = 1;
        state.engine.models = state.engine.models.map((model, index) => ({
            ...model,
            size: locale === 'en' ? ['522 MB', '1.4 GB', '3.4 GB'][index] : model.size,
        }));
        const names =
            locale === 'en'
                ? ['Anna Miller', 'Mark Wilson', 'Lisa', 'Product team', 'Alex', 'Family']
                : ['Аня Смирнова', 'Марк Волков', 'Лиза', 'Команда проекта', 'Саша', 'Семья'];
        const previews =
            locale === 'en'
                ? [
                      'sounds good, see you then ☀️',
                      'Sent the updated mockups',
                      'Have you seen the new season?',
                      'The release is ready 🎉',
                      'Coffee this weekend?',
                      'How was your day?',
                  ]
                : [
                      'Отлично, тогда до завтра ☀️',
                      'Отправил новые макеты',
                      'Ты видел новый сезон?',
                      'Релиз готов 🎉',
                      'Кофе на выходных?',
                      'Как прошёл день?',
                  ];
        state.chats = state.chats.map((chat, index) => ({
            ...chat,
            title: names[index],
            preview: previews[index],
            username: index === 0 ? 'anna_example' : '',
            kind: 'private',
            rule: {
                ...chat.rule,
                voiceSettingsMode: 'override',
                voiceReplyMode: 'random',
                voiceReplyProbability: 50,
                voiceSynthesisModel: 'piper-ru-irina',
                voiceSpeechRate: 1,
            },
        }));
        const texts =
            locale === 'en'
                ? [
                      'hey! how’s the project going?',
                      'pretty good, finally shipped the new version))',
                      'nice 🎉\ncoffee tomorrow? same place?',
                      'yeah, after two works for me',
                      'perfect, see you then ☀️',
                  ]
                : [
                      'Привет! Как продвигается проект?',
                      'Хорошо, наконец выпустил новую версию))',
                      'Класс 🎉\nЗавтра кофе? В том же месте?',
                      'Да, после двух мне удобно',
                      'Отлично, до завтра ☀️',
                  ];
        const history: Message[] = texts.map((text, index) => ({
            id: String(index + 1),
            chatId: '41',
            text,
            outgoing: index % 2 === 1,
            automated: index === 3,
            date: 1792656000 + index * 120,
            kind: 'text',
            senderName: index % 2 === 1 ? state.telegram.name : names[0],
            senderId: index % 2 === 1 ? '100' : '101',
        }));
        await installVoiceMocks(context, {
            state,
            speech: documentationSpeech(locale),
            history,
            audioPath: join(process.cwd(), 'tests', 'fixtures', 'voice.ogg'),
        });
        await page.goto(base);
        await expect(page.locator('html')).toHaveAttribute('lang', locale);
        await expect(page.locator('[data-qa="sidebar-navigation"]').getByRole('button')).toHaveCount(4);
        await expect(page.locator('[data-qa="nav-errors"]')).toHaveCount(0);
        await page.getByRole('button', {name: names[0], exact: true}).click();
        await expect(page.locator('[data-qa="conversation-assistant-bar"]')).toBeVisible();
        await capture(page, locale, 'chats-light');

        await persistTestTheme(page, true);
        await expect(page.locator('body')).toContainClass('g-root_theme_dark');
        await capture(page, locale, 'chats-dark');

        await page.locator('[data-qa="nav-models"]').click();
        await expect(page.locator('[data-qa="model-card"]')).toHaveCount(state.engine.models.length);
        await capture(page, locale, 'models');

        await page.locator('[data-qa="nav-speech"]').click();
        await expect(
            page.getByRole('heading', {name: messages.voice.libraryTitle, exact: true}),
        ).toBeVisible();
        await expect(page.locator('[data-qa="speech-asset"]').first()).toBeVisible();
        await capture(page, locale, 'speech-library');

        await page.locator('[data-qa="nav-chats"]').click();
        await page.getByRole('button', {name: names[0], exact: true}).click();
        await page.getByRole('button', {name: messages.configure, exact: true}).click();
        const rule = page.locator('[data-qa="communication-dialog"]');
        await rule.getByRole('menuitem', {name: messages.chatSettings.sections.voice, exact: true}).click();
        await expect(rule.getByRole('slider', {name: messages.voice.speechRate, exact: true})).toBeVisible();
        await capture(page, locale, 'chat-voice');
        await rule.getByLabel(messages.close, {exact: true}).click();

        await page.getByRole('button', {name: messages.voice.manualTitle, exact: true}).click();
        const voice = page.getByRole('dialog', {name: messages.voice.manualTitle, exact: true});
        await voice
            .getByRole('textbox', {name: messages.voice.manualText, exact: true})
            .fill(
                locale === 'en'
                    ? 'Hey! I’m free after two. See you at our usual café.'
                    : 'Привет! Я свободен после двух. Увидимся в нашем обычном кафе.',
            );
        await expect(
            voice.getByRole('button', {name: messages.voice.previewCheck, exact: true}),
        ).toBeEnabled();
        await capture(page, locale, 'manual-voice');
    });
}
