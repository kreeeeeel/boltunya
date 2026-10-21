import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {beforeEach, expect, it, vi} from 'vitest';
import type {components} from '@/shared/api/schema';
import {ModelRoutingSettings} from '@/modules/settings/ModelRoutingSettings/ModelRoutingSettings';
import ru from '@/shared/i18n/ru.json';
import {workspaceState} from '@tests/fixtures/workspace';
import {modelProfiles} from '@tests/fixtures/modelProfiles';

const mocks = vi.hoisted(() => ({GET: vi.fn(), PUT: vi.fn()}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: mocks,
}));

const initial: components['schemas']['ModelTaskRouting'] = {
    enabled: true,
    parallelIndependentTasks: false,
    replyProfileId: '',
    memoryProfileId: '',
    researchProfileId: '',
    visionProfileId: '',
};

beforeEach(() => {
    let saved = structuredClone(initial);
    mocks.GET.mockReset().mockImplementation(async (path: string) => ({
        data: path === '/api/model-routing' ? structuredClone(saved) : {profiles: modelProfiles, presets: []},
        response: new Response(),
    }));
    mocks.PUT.mockReset().mockImplementation(async (_path, {body}) => {
        saved = structuredClone(body);
        return {data: saved, response: new Response()};
    });
});

function mount() {
    const engine = structuredClone(workspaceState.engine);
    engine.models[2] = {...engine.models[2], installed: true, supportsVision: true};
    return render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ModelRoutingSettings engine={engine} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
}

it('offers installed Ollama models and API profiles for each task and persists a local assignment', async () => {
    const view = mount();
    fireEvent.click(await screen.findByRole('combobox', {name: ru.modelRouting.tasks.replyProfileId}));
    fireEvent.click(await screen.findByRole('option', {name: 'Ollama · Qwen 3 · 1.7B'}));
    fireEvent.click(screen.getByRole('button', {name: ru.save}));
    await waitFor(() =>
        expect(mocks.PUT).toHaveBeenCalledWith('/api/model-routing', {
            body: expect.objectContaining({replyModel: 'qwen3:1.7b', replyProfileId: ''}),
        }),
    );
    view.unmount();
    mount();
    expect(
        await screen.findByRole('combobox', {name: ru.modelRouting.tasks.replyProfileId}),
    ).toHaveTextContent('Qwen 3 · 1.7B');
    fireEvent.click(screen.getByRole('combobox', {name: ru.modelRouting.tasks.replyProfileId}));
    fireEvent.click(await screen.findByRole('option', {name: 'Cloud text · example-model'}));
    fireEvent.click(screen.getByRole('button', {name: ru.save}));
    await waitFor(() =>
        expect(mocks.PUT).toHaveBeenLastCalledWith('/api/model-routing', {
            body: expect.objectContaining({replyModel: '', replyProfileId: 'profile-primary'}),
        }),
    );
});

it('only offers vision-capable installed models and API profiles for image analysis', async () => {
    mount();
    fireEvent.click(await screen.findByRole('combobox', {name: ru.modelRouting.tasks.visionProfileId}));
    expect(await screen.findByRole('option', {name: 'Ollama · Gemma 3 · 4B'})).toBeVisible();
    expect(screen.getByRole('option', {name: 'Photo model · photo-model'})).toBeVisible();
    expect(screen.queryByRole('option', {name: 'Ollama · Qwen 3 · 1.7B'})).toBeNull();
    expect(screen.queryByRole('option', {name: 'Cloud text · example-model'})).toBeNull();
    fireEvent.click(screen.getByRole('option', {name: 'Ollama · Gemma 3 · 4B'}));
    fireEvent.click(screen.getByRole('button', {name: ru.save}));
    await waitFor(() =>
        expect(mocks.PUT).toHaveBeenCalledWith('/api/model-routing', {
            body: expect.objectContaining({visionModel: 'gemma3:4b', visionProfileId: ''}),
        }),
    );
});
