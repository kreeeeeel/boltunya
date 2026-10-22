import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {afterEach, beforeEach, expect, it, vi} from 'vitest';

import {ProviderProfiles} from '@/modules/settings/ProviderProfiles/ProviderProfiles';
import type {ModelProfile, ModelProviderPreset} from '@/modules/settings/model/useModelProfiles';
import {modelProfiles, providerPreset} from '@tests/fixtures/modelProfiles';
import ru from '@/shared/i18n/ru.json';

const report = vi.hoisted(() => ({
    profiles: [] as ModelProfile[],
    presets: [] as ModelProviderPreset[],
    error: '',
    loading: false,
    refresh: vi.fn(),
}));
const mutations = vi.hoisted(() => ({updateModelProfile: vi.fn(), deleteModelProfile: vi.fn()}));
vi.mock('@/modules/settings/model/useModelProfiles', () => ({useModelProfiles: () => report}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: {models: mutations},
}));

function renderProfiles(onRefresh = vi.fn(async () => {})) {
    return render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ProviderProfiles onRefresh={onRefresh} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
}

beforeEach(() => {
    vi.clearAllMocks();
    report.profiles = [];
    report.presets = [providerPreset];
    report.error = '';
    report.loading = false;
    report.refresh.mockResolvedValue(undefined);
});

afterEach(() => {
    vi.restoreAllMocks();
});

it('replaces the server introduction with one illustrated empty state and opens the connection editor', () => {
    const view = renderProfiles();
    expect(view.container.querySelector('[data-qa="model-connections-empty"]')).toBeVisible();
    expect(view.container.querySelector('[data-qa="settings-content-providers"]')).toBeNull();
    expect(screen.getByRole('button', {name: ru.modelProfiles.create})).toBeEnabled();
    fireEvent.click(screen.getByRole('button', {name: ru.modelProfiles.create}));
    expect(view.container.querySelector('[data-qa="model-connections-empty"]')).toBeNull();
    expect(screen.getByRole('textbox', {name: ru.modelProfiles.name})).toBeVisible();
});

it('keeps existing connections available without an empty-state call to action', () => {
    report.profiles = structuredClone(modelProfiles);
    const view = renderProfiles();
    expect(view.container.querySelector('[data-qa="model-connections-empty"]')).toBeNull();
    expect(screen.getByRole('button', {name: (name) => name.includes('Cloud text')})).toBeVisible();
});

it('shows a load failure instead of claiming there are no connections', () => {
    report.error = 'Connection list unavailable';
    const view = renderProfiles();
    expect(view.container.querySelector('[data-qa="model-connections-empty"]')).toBeNull();
    expect(screen.getByRole('alert')).toHaveTextContent(report.error);
});

it.each(['save', 'delete'] as const)(
    'uses one shared refresh after successful %s without a duplicate catalog broadcast',
    async (action) => {
        report.profiles = structuredClone(modelProfiles);
        mutations.updateModelProfile.mockResolvedValue(modelProfiles[0]);
        mutations.deleteModelProfile.mockResolvedValue({ok: true});
        const onRefresh = vi.fn(async () => {});
        const broadcast = vi.spyOn(window, 'dispatchEvent');
        renderProfiles(onRefresh);
        fireEvent.click(screen.getByRole('button', {name: (name) => name.includes('Cloud text')}));
        fireEvent.click(
            screen.getByRole('button', {name: action === 'save' ? ru.save : ru.modelProfiles.delete}),
        );
        await waitFor(() => expect(onRefresh).toHaveBeenCalledTimes(1));
        expect(
            action === 'save' ? mutations.updateModelProfile : mutations.deleteModelProfile,
        ).toHaveBeenCalledTimes(1);
        expect(report.refresh).toHaveBeenCalledTimes(1);
        expect(
            broadcast.mock.calls.filter(([event]) => event.type === 'aigram-model-profiles-changed'),
        ).toHaveLength(0);
        if (action === 'save') {
            expect(screen.getByRole('textbox', {name: ru.modelProfiles.name})).toHaveValue('Cloud text');
            expect(screen.getByRole('status')).toHaveTextContent(ru.saved);
        }
    },
);

it('keeps the local draft and mutation error when saving fails without refreshing other consumers', async () => {
    report.profiles = structuredClone(modelProfiles);
    mutations.updateModelProfile.mockRejectedValueOnce(new Error('Synthetic save failed'));
    renderProfiles();
    fireEvent.click(screen.getByRole('button', {name: (name) => name.includes('Cloud text')}));
    const name = screen.getByRole('textbox', {name: ru.modelProfiles.name});
    fireEvent.change(name, {target: {value: 'Unsaved synthetic title'}});
    fireEvent.click(screen.getByRole('button', {name: ru.save}));
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Synthetic save failed'));
    expect(name).toHaveValue('Unsaved synthetic title');
    expect(report.refresh).not.toHaveBeenCalled();
});
