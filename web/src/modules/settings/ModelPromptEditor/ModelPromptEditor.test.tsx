import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {act, fireEvent, render, screen, waitFor} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {ModelPromptEditor} from '@/modules/settings/ModelPromptEditor/ModelPromptEditor';
import {workspaceState} from '@tests/fixtures/workspace';
import {modelProfiles} from '@tests/fixtures/modelProfiles';
import {syntheticPromptBudget} from '@tests/fixtures/promptBudget';
import ru from '@/shared/i18n/ru.json';
const mocks = vi.hoisted(() => ({
    getModelPrompts: vi.fn(),
    modelProfiles: vi.fn(),
    saveModelPrompts: vi.fn(),
    estimatePromptBudget: vi.fn(),
}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: {
        models: {
            getModelPrompts: mocks.getModelPrompts,
            saveModelPrompts: mocks.saveModelPrompts,
            modelProfiles: mocks.modelProfiles,
            estimatePromptBudget: mocks.estimatePromptBudget,
        },
    },
}));
it('saves only the selected model additions and keeps API context limits in the connection', async () => {
    mocks.getModelPrompts.mockImplementation(async () => ({prompts: []}));
    mocks.modelProfiles.mockImplementation(async () => ({profiles: modelProfiles, presets: []}));
    mocks.saveModelPrompts.mockImplementation(async ({modelPromptSettings: body}) => body);
    mocks.estimatePromptBudget.mockImplementation(async ({promptBudgetRequest: body}) =>
        syntheticPromptBudget(body),
    );
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ModelPromptEditor
                    engine={workspaceState.engine}
                    defaultModel="qwen3:1.7b"
                    defaultProfileId=""
                    globalPrompt="Shared owner instructions"
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    fireEvent.change(await screen.findByRole('textbox', {name: ru.modelPrompts.instructions}), {
        target: {value: 'Additional concise wording'},
    });
    fireEvent.change(screen.getByRole('spinbutton', {name: ru.modelPrompts.contextWindow}), {
        target: {value: '16384'},
    });
    fireEvent.click(screen.getByRole('button', {name: ru.modelPrompts.save}));
    await waitFor(() =>
        expect(mocks.saveModelPrompts).toHaveBeenCalledWith({
            modelPromptSettings: expect.objectContaining({
                model: 'qwen3:1.7b',
                profileId: '',
                instructions: 'Additional concise wording',
                contextWindow: 16384,
            }),
        }),
    );
    expect(await screen.findByRole('button', {name: ru.saved})).toBeVisible();
    fireEvent.click(screen.getByRole('combobox', {name: ru.modelPrompts.model}));
    fireEvent.click(screen.getByRole('option', {name: 'Cloud text · API'}));
    expect(screen.getByRole('spinbutton', {name: ru.modelPrompts.contextWindow})).toBeDisabled();
    expect(screen.getByRole('textbox', {name: ru.modelPrompts.instructions})).toHaveValue('');
    fireEvent.click(screen.getByRole('combobox', {name: ru.modelPrompts.model}));
    fireEvent.click(
        screen.getByRole('option', {
            name: workspaceState.engine.models.find((model) => model.id === 'qwen3:1.7b')!.name,
        }),
    );
    expect(screen.getByRole('textbox', {name: ru.modelPrompts.instructions})).toHaveValue(
        'Additional concise wording',
    );
});
it('keeps preferences distinct for each model within one API connection', async () => {
    const profiles = structuredClone(modelProfiles);
    const record = {profileId: 'profile-primary', contextWindow: null, tokenEncoding: 'auto'};
    mocks.getModelPrompts.mockImplementation(async () => ({
        prompts: [
            {
                ...record,
                model: 'example-model',
                instructions: 'Instructions for the current model',
            },
            {...record, model: 'older-model', instructions: 'Instructions for the old model'},
        ],
    }));
    mocks.modelProfiles.mockImplementation(async () => ({profiles, presets: []}));
    mocks.estimatePromptBudget.mockImplementation(async ({promptBudgetRequest: body}) =>
        syntheticPromptBudget(body),
    );
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ModelPromptEditor
                    engine={workspaceState.engine}
                    defaultModel="qwen3:1.7b"
                    defaultProfileId="profile-primary"
                    globalPrompt="Shared owner instructions"
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    const instructions = await screen.findByRole('textbox', {name: ru.modelPrompts.instructions});
    await waitFor(() => expect(instructions).toHaveValue('Instructions for the current model'));
    profiles[0].model = 'new-model-without-preferences';
    act(() => window.dispatchEvent(new Event('aigram-model-profiles-changed')));
    await waitFor(() => expect(instructions).toHaveValue(''));
    fireEvent.change(instructions, {target: {value: 'A separate new draft'}});
    profiles[0].model = 'older-model';
    act(() => window.dispatchEvent(new Event('aigram-model-profiles-changed')));
    await waitFor(() => expect(instructions).toHaveValue('Instructions for the old model'));
    profiles[0].model = 'new-model-without-preferences';
    act(() => window.dispatchEvent(new Event('aigram-model-profiles-changed')));
    await waitFor(() => expect(instructions).toHaveValue('A separate new draft'));
});
