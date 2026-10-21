import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor, within} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {ModelsPanel} from '@/modules/workspace/ModelsPanel/ModelsPanel';
import {workspaceState} from '@tests/fixtures/workspace';
import ru from '@/shared/i18n/ru.json';
import {modelProfiles} from '@tests/fixtures/modelProfiles';
const mocks = vi.hoisted(() => ({
    preview: vi.fn(),
    cancelModelDownload: vi.fn(),
    start: vi.fn(),
    restartEngine: vi.fn(),
    install: vi.fn(),
    pull: vi.fn(),
    estimatePromptBudget: vi.fn(),
    settings: vi.fn(),
    modelProfiles: vi.fn(async () => ({profiles: [] as typeof modelProfiles, presets: []})),
}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: {
        models: {
            preview: mocks.preview,
            cancelModelDownload: mocks.cancelModelDownload,
            start: mocks.start,
            restartEngine: mocks.restartEngine,
            install: mocks.install,
            pull: mocks.pull,
            modelProfiles: mocks.modelProfiles,
            estimatePromptBudget: mocks.estimatePromptBudget,
        },
        settings: {
            settings: mocks.settings,
        },
    },
}));
vi.mock('@/modules/settings/PromptBudgetNotice/PromptBudgetNotice', () => ({PromptBudgetNotice: () => null}));

it('renders an Ollama warmup failure as an icon title and description block', async () => {
    const engine = {...workspaceState.engine, error: 'Не удалось загрузить модель в память.'};
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ModelsPanel engine={engine} defaultModel="qwen3:1.7b" onRefresh={vi.fn()} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    const notice = screen.getByRole('alert', {name: ru.engineErrorTitle});
    expect(within(notice).getByText(engine.error)).toBeVisible();
    expect(notice.querySelector('svg')).not.toBeNull();
    await waitFor(() => expect(mocks.modelProfiles).toHaveBeenCalled());
});

it('restarts managed Ollama and keeps the action pending until refresh completes', async () => {
    let complete: () => void = () => undefined;
    mocks.restartEngine.mockReturnValue(
        new Promise<void>((resolve) => {
            complete = resolve;
        }),
    );
    const refresh = vi.fn(async () => {});
    const engine = {...workspaceState.engine, canRestart: true};
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ModelsPanel engine={engine} defaultModel="qwen3:1.7b" onRefresh={refresh} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    const button = screen.getByRole('button', {name: ru.restartEngine});
    fireEvent.click(button);
    expect(mocks.restartEngine).toHaveBeenCalledOnce();
    expect(button).toBeDisabled();
    expect(refresh).not.toHaveBeenCalled();
    complete();
    await waitFor(() => expect(refresh).toHaveBeenCalledOnce());
    await waitFor(() => expect(button).toBeEnabled());
});

it('explains why an external Ollama server cannot be restarted and hides the action offline', async () => {
    const renderPanel = (online: boolean) => (
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ModelsPanel
                    engine={{...workspaceState.engine, online, canRestart: false}}
                    defaultModel="qwen3:1.7b"
                    onRefresh={vi.fn()}
                />
            </ThemeProvider>
        </NextIntlClientProvider>
    );
    const view = render(renderPanel(true));
    expect(screen.getByRole('button', {name: ru.restartEngine})).toBeDisabled();
    expect(screen.getByRole('button', {name: ru.restartEngine})).toHaveAttribute(
        'title',
        ru.engineRestartExternal,
    );
    view.rerender(renderPanel(false));
    expect(screen.queryByRole('button', {name: ru.restartEngine})).toBeNull();
    await waitFor(() => expect(mocks.modelProfiles).toHaveBeenCalled());
});

it('shows a restart failure in a block and re-enables retry', async () => {
    mocks.restartEngine.mockRejectedValueOnce(new Error('Не удалось запустить Ollama.'));
    const refresh = vi.fn();
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ModelsPanel
                    engine={{...workspaceState.engine, canRestart: true}}
                    defaultModel="qwen3:1.7b"
                    onRefresh={refresh}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    const button = screen.getByRole('button', {name: ru.restartEngine});
    fireEvent.click(button);
    expect(await screen.findByRole('alert', {name: ru.engineErrorTitle})).toHaveTextContent(
        'Не удалось запустить Ollama.',
    );
    expect(button).toBeEnabled();
    expect(refresh).not.toHaveBeenCalled();
});
it('labels models that are restricted to manual drafts', () => {
    const engine = structuredClone(workspaceState.engine);
    engine.models[0].supportsAutomation = false;
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ModelsPanel engine={engine} defaultModel="gemma3:4b" onRefresh={vi.fn()} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    expect(screen.getByText(ru.modelDraftOnly)).toBeInTheDocument();
});
it('shows warmup inside the model button until the first benchmark completes', async () => {
    mocks.settings.mockResolvedValue({ok: true});
    let complete: (value: unknown) => void = () => undefined;
    mocks.preview.mockReturnValue(
        new Promise((resolve) => {
            complete = resolve;
        }),
    );
    const view = render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ModelsPanel engine={workspaceState.engine} defaultModel="qwen3:1.7b" onRefresh={vi.fn()} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    const card = view.container.querySelector('[data-model-id="qwen3:0.6b"]') as HTMLElement;
    fireEvent.click(within(card).getByRole('button', {name: ru.choose}));
    await waitFor(() => expect(mocks.preview).toHaveBeenCalled());
    expect(within(card).getByRole('button', {name: ru.modelWarming})).toBeVisible();
    complete({text: 'Привет', durationMs: 100, tokensPerSecond: 20});
    await waitFor(() => expect(within(card).queryByRole('button', {name: ru.modelWarming})).toBeNull());
});
it('keeps installed selected models visibly warming while Ollama is loading them', () => {
    const engine = {...workspaceState.engine, warming: 'qwen3:1.7b'};
    const view = render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ModelsPanel engine={engine} defaultModel="qwen3:1.7b" onRefresh={vi.fn()} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    const card = view.container.querySelector('[data-model-id="qwen3:1.7b"]') as HTMLElement;
    expect(within(card).getByRole('button', {name: ru.modelWarming})).toHaveAttribute('aria-busy', 'true');
    expect(within(card).queryByRole('button', {name: ru.chosen})).toBeNull();
});
it('searches model names and identifiers and restores the catalog after an empty result', async () => {
    const view = render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ModelsPanel engine={workspaceState.engine} defaultModel="qwen3:1.7b" onRefresh={vi.fn()} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    const rows = () => view.container.querySelectorAll('[data-qa="model-card"]');
    expect(rows()).toHaveLength(3);
    const search = screen.getByRole('textbox', {name: ru.modelSearch.label});
    fireEvent.change(search, {target: {value: 'gemma3:4b'}});
    expect(rows()).toHaveLength(1);
    expect(screen.getByText('Gemma 3 · 4B')).toBeInTheDocument();
    fireEvent.change(search, {target: {value: 'QWEN'}});
    expect(rows()).toHaveLength(2);
    fireEvent.change(search, {target: {value: 'nothing-matches'}});
    expect(await screen.findByText(ru.modelSearch.empty)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', {name: ru.modelSearch.clear}));
    expect(rows()).toHaveLength(3);
    expect(search).toHaveValue('');
});
it('searches API profiles together with local models and keeps one authoritative selection', async () => {
    mocks.modelProfiles.mockResolvedValue({profiles: modelProfiles, presets: []});
    render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ModelsPanel
                    engine={workspaceState.engine}
                    defaultModel="qwen3:1.7b"
                    defaultModelProfileId="profile-primary"
                    onRefresh={vi.fn()}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    expect(await screen.findByText('Cloud text')).toBeVisible();
    fireEvent.change(screen.getByRole('textbox', {name: ru.modelSearch.label}), {
        target: {value: 'Cloud text'},
    });
    expect(screen.getByText('Cloud text')).toBeVisible();
    expect(screen.queryByText(ru.modelSearch.empty)).toBeNull();
    expect(screen.getAllByRole('button', {name: ru.chosen})).toHaveLength(1);
});
it('filters image-capable installed and catalog models together with API profiles under the search field', async () => {
    mocks.modelProfiles.mockResolvedValue({profiles: modelProfiles, presets: []});
    const engine = structuredClone(workspaceState.engine);
    engine.models[2].supportsVision = true;
    const view = render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ModelsPanel engine={engine} defaultModel="qwen3:1.7b" onRefresh={vi.fn()} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    await screen.findByText('Photo model');
    fireEvent.click(screen.getByRole('tab', {name: ru.modelFilters.vision}));
    expect(view.container.querySelectorAll('[data-qa="model-card"]')).toHaveLength(1);
    expect(screen.getByText('Gemma 3 · 4B')).toBeVisible();
    expect(screen.getByText('Photo model')).toBeVisible();
    expect(screen.queryByText('Cloud text')).toBeNull();
    const local = view.container.querySelector('[data-model-id="gemma3:4b"]') as HTMLElement;
    const localVision = within(local).getByText(ru.modelProfiles.capability.vision).parentElement;
    expect(localVision).toHaveTextContent(ru.imagesSupported);
    expect(localVision).not.toHaveTextContent(ru.thinkingOn);
    const profile = view.container.querySelector('[data-profile-id="profile-vision"]') as HTMLElement;
    expect(within(profile).getByText(ru.modelProfiles.capability.vision).parentElement).toHaveTextContent(
        ru.imagesSupported,
    );
    expect(within(profile).getByText(ru.compareThinking).parentElement).toHaveTextContent(ru.thinkingOn);
    fireEvent.change(screen.getByRole('textbox', {name: ru.modelSearch.label}), {target: {value: 'Photo'}});
    expect(view.container.querySelectorAll('[data-qa="model-card"]')).toHaveLength(0);
    expect(screen.getByText('Photo model')).toBeVisible();
    fireEvent.change(screen.getByRole('textbox', {name: ru.modelSearch.label}), {target: {value: ''}});
    fireEvent.click(screen.getByRole('tab', {name: ru.modelFilters.generation}));
    expect(view.container.querySelectorAll('[data-qa="model-card"]')).toHaveLength(2);
    expect(screen.getByText('Cloud text')).toBeVisible();
    expect(screen.queryByText('Photo model')).toBeNull();
    const textModel = view.container.querySelector('[data-model-id="qwen3:1.7b"]') as HTMLElement;
    expect(within(textModel).getByText(ru.modelProfiles.capability.vision).parentElement).toHaveTextContent(
        ru.imagesUnsupported,
    );
    fireEvent.click(screen.getByRole('tab', {name: ru.modelFilters.all}));
    expect(view.container.querySelectorAll('[data-qa="model-card"]')).toHaveLength(3);
});
