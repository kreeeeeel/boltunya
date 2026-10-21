import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {expect, it, vi} from 'vitest';
import {AttachmentDialog} from '@/modules/media/AttachmentDialog/AttachmentDialog';
import ru from '@/shared/i18n/ru.json';
import labels from '@/modules/media/i18n/ru.json';
const mocks = vi.hoisted(() => ({sendAttachments: vi.fn(), deleteUpload: vi.fn(), uploadFile: vi.fn()}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: {
        media: {
            deleteUpload: mocks.deleteUpload,
        },
        chats: {
            sendAttachments: mocks.sendAttachments,
        },
    },
}));
vi.mock('@/modules/media/model/uploads', async (original) => ({
    ...(await original<typeof import('@/modules/media/model/uploads')>()),
    uploadFile: mocks.uploadFile,
}));
it('does not delete files in flight when navigating away after submission', async () => {
    const pending = Promise.withResolvers<{ok: boolean}>();
    mocks.sendAttachments.mockReturnValue(pending.promise);
    mocks.deleteUpload.mockResolvedValue({ok: true});
    mocks.uploadFile.mockResolvedValue({
        id: 'upload-1',
        fileName: 'report.pdf',
        mimeType: 'application/pdf',
        size: 4,
    });
    const view = render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <AttachmentDialog
                    chatId="41"
                    kind="document"
                    selection={[
                        {file: new File(['test'], 'report.pdf', {type: 'application/pdf'}), preview: ''},
                    ]}
                    onClose={vi.fn()}
                    onSent={async () => {}}
                />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    fireEvent.click(screen.getByRole('button', {name: labels.send}));
    await waitFor(() => expect(mocks.sendAttachments).toHaveBeenCalledOnce());
    view.unmount();
    expect(mocks.deleteUpload).not.toHaveBeenCalled();
    pending.resolve({ok: true});
});
