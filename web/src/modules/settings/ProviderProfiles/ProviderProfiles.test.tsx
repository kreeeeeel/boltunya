import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen} from '@testing-library/react';
import {beforeEach, expect, it, vi} from 'vitest';

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
vi.mock('@/modules/settings/model/useModelProfiles', () => ({useModelProfiles: () => report}));

function renderProfiles() {
    return render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <ProviderProfiles onRefresh={vi.fn()} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
}

beforeEach(() => {
    report.profiles = [];
    report.presets = [providerPreset];
    report.error = '';
    report.loading = false;
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
