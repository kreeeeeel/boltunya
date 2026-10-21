import {expect, test} from '@playwright/test';
import {readFileSync} from 'node:fs';
import {join} from 'node:path';
import {workspaceState} from '../fixtures/workspace';
import {testHistory} from '../fixtures/chats';
import {speechState} from '../fixtures/speech';
import {installVoiceMocks} from '../fixtures/voiceDemo';
import ru from '../../src/shared/i18n/ru.json';

for (const dark of [false, true]) {
    test(`sent voice plays from Telegram media with compact metadata in ${dark ? 'dark' : 'light'} theme`, async ({
        page,
    }) => {
        const state = structuredClone(workspaceState);
        const title = 'Синтетический голосовой чат';
        state.chats[0].title = title;
        await installVoiceMocks(page, {
            state,
            speech: structuredClone(speechState),
            history: testHistory('41'),
            audioPath: join(process.cwd(), 'tests', 'fixtures', 'voice.ogg'),
            dark,
            interactive: true,
        });
        const launch =
            process.env.TIHO_TEST_URL ??
            readFileSync(join(process.env.LOCALAPPDATA ?? '', 'Aigram', 'launch.url'), 'utf8');
        await page.goto(launch);
        await page.getByRole('button', {name: title, exact: true}).click();
        await page.getByRole('button', {name: ru.draft, exact: true}).click();
        await page.getByRole('menuitem', {name: ru.voice.generateVoice, exact: true}).click();
        const draft = page.locator('[data-qa="voice-draft"]');
        await expect(draft.getByRole('button', {name: ru.voice.sendDraft, exact: true})).toBeEnabled();
        await draft.getByRole('button', {name: ru.voice.sendDraft, exact: true}).click();
        await expect(draft).toHaveCount(0);
        const voice = page.locator('[data-qa="message-outgoing"][data-message-kind="voice"]').last();
        const player = voice.locator('audio');
        await expect(player).toHaveAttribute('src');
        const source = new URL((await player.getAttribute('src'))!, page.url());
        expect(source.pathname.split('/').slice(0, 3)).toEqual(['', 'api', 'media']);
        expect(Number(source.pathname.split('/')[3])).toBeGreaterThan(0);
        await expect
            .poll(() => player.evaluate((audio: HTMLAudioElement) => audio.duration))
            .toBeGreaterThan(4);
        await voice.getByRole('button', {name: ru.playAudio, exact: true}).click();
        await expect
            .poll(() => player.evaluate((audio: HTMLAudioElement) => audio.currentTime))
            .toBeGreaterThan(0);
        await expect(voice.getByText(ru.mediaError, {exact: true})).toHaveCount(0);
        await voice.getByRole('button', {name: ru.pauseAudio, exact: true}).click();

        for (const width of [1440, 390]) {
            await page.setViewportSize({width, height: 900});
            await voice.scrollIntoViewIfNeeded();
            await expect
                .poll(() =>
                    voice.evaluate((bubble) => {
                        const bounds = bubble.getBoundingClientRect();
                        const wave = bubble.querySelector('[role="slider"]')!.getBoundingClientRect();
                        const duration = bubble
                            .querySelector('[data-qa="message-audio-duration"]')!
                            .getBoundingClientRect();
                        const metadata = bubble.querySelector('[data-qa="message-meta"]')!;
                        const meta = metadata.getBoundingClientRect();
                        return {
                            durationBaseline:
                                Math.abs(duration.top + duration.height / 2 - meta.top - meta.height / 2) <=
                                4,
                            belowWave: meta.top >= wave.bottom - 2 && meta.top - wave.bottom <= 8,
                            noOverlap: duration.right + 4 <= meta.left,
                            transparent: getComputedStyle(metadata).backgroundColor === 'rgba(0, 0, 0, 0)',
                            insideBubble: meta.right <= bounds.right && meta.bottom <= bounds.bottom,
                            compactHeight: bounds.height <= 84,
                            viewport: bounds.left >= 0 && bounds.right <= innerWidth,
                        };
                    }),
                )
                .toEqual({
                    durationBaseline: true,
                    belowWave: true,
                    noOverlap: true,
                    transparent: true,
                    insideBubble: true,
                    compactHeight: true,
                    viewport: true,
                });
            await page.screenshot({
                path: `../docs/resources/outgoing-voice-${dark ? 'dark' : 'light'}-${width}.png`,
                animations: 'disabled',
            });
        }
    });
}
