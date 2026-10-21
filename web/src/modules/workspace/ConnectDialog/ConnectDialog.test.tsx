import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen} from '@testing-library/react';
import {describe, expect, it, vi} from 'vitest';

import {ConnectDialog} from '@/modules/workspace/ConnectDialog/ConnectDialog';
import ru from '@/shared/i18n/ru.json';

describe('Telegram login', () => {
    it('shows the key guide and rejects invalid credentials without a request', async () => {
        const refresh = vi.fn();
        render(
            <NextIntlClientProvider locale="ru" messages={ru}>
                <ThemeProvider>
                    <ConnectDialog
                        onClose={vi.fn()}
                        onRefresh={refresh}
                        status={{state: 'disconnected', name: '', prompt: '', error: ''}}
                    />
                </ThemeProvider>
            </NextIntlClientProvider>,
        );
        expect(screen.getByRole('link', {name: (name) => name.includes('my.telegram.org')})).toHaveAttribute(
            'href',
            'https://my.telegram.org/apps',
        );
        fireEvent.click(screen.getByRole('button', {name: 'Продолжить'}));
        expect(await screen.findByText(ru.credentialsInvalid)).toBeInTheDocument();
        expect(refresh).not.toHaveBeenCalled();
    });
    it('asks for the cloud password at the password step', () => {
        render(
            <NextIntlClientProvider locale="ru" messages={ru}>
                <ThemeProvider>
                    <ConnectDialog
                        onClose={vi.fn()}
                        onRefresh={vi.fn()}
                        status={{state: 'password', name: '', prompt: '', error: ''}}
                    />
                </ThemeProvider>
            </NextIntlClientProvider>,
        );
        expect(screen.getByLabelText(ru.password)).toHaveAttribute('type', 'password');
        expect(screen.queryByLabelText(ru.apiHash)).not.toBeInTheDocument();
    });
});
