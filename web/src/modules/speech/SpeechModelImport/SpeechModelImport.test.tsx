import {ThemeProvider} from '@gravity-ui/uikit';
import {NextIntlClientProvider} from 'next-intl';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {beforeEach, expect, it, vi} from 'vitest';
import {SpeechModelImport} from '@/modules/speech/SpeechModelImport/SpeechModelImport';
import {MAX_UPLOAD_BYTES} from '@/modules/media/model/uploads';
import {speechState} from '@tests/fixtures/speech';
import ru from '@/shared/i18n/ru.json';

const mocks = vi.hoisted(() => ({uploadFile: vi.fn(), importSpeechModel: vi.fn(), deleteUpload: vi.fn()}));
vi.mock('@/modules/media/model/uploads', async (original) => ({
    ...(await original<typeof import('@/modules/media/model/uploads')>()),
    uploadFile: mocks.uploadFile,
}));
vi.mock('@/shared/api/client', async (original) => ({
    ...(await original<typeof import('@/shared/api/client')>()),
    api: {speech: {importSpeechModel: mocks.importSpeechModel}, media: {deleteUpload: mocks.deleteUpload}},
}));

beforeEach(() => {
    vi.clearAllMocks();
    mocks.deleteUpload.mockResolvedValue({ok: true});
});

function setup() {
    const imported = vi.fn();
    const view = render(
        <NextIntlClientProvider locale="ru" messages={ru}>
            <ThemeProvider>
                <SpeechModelImport onImported={imported} />
            </ThemeProvider>
        </NextIntlClientProvider>,
    );
    return {...view, imported};
}

it('rejects incompatible or oversized archives before uploading anything', () => {
    setup();
    const input = screen.getByLabelText(ru.voice.importArchive, {selector: 'input'});
    fireEvent.change(input, {target: {files: [new File(['fake'], 'voice.wav')]}});
    expect(screen.getByRole('alert')).toHaveTextContent(ru.voice.importUnsupported);
    const large = new File(['data'], 'voice.tar.bz2');
    Object.defineProperty(large, 'size', {value: MAX_UPLOAD_BYTES + 1});
    fireEvent.change(input, {target: {files: [large]}});
    expect(screen.getByRole('alert')).toHaveTextContent(ru.voice.importTooLarge);
    expect(mocks.uploadFile).not.toHaveBeenCalled();
    expect(mocks.importSpeechModel).not.toHaveBeenCalled();
});

it('uploads once, imports explicit metadata and refreshes the real catalog only after success', async () => {
    mocks.uploadFile.mockResolvedValue({id: 'upload-id', fileName: 'voice.tar.bz2', size: 4});
    mocks.importSpeechModel.mockResolvedValue(speechState);
    const view = setup();
    const file = new File(['data'], 'voice.tar.bz2');
    fireEvent.change(screen.getByLabelText(ru.voice.importArchive, {selector: 'input'}), {
        target: {files: [file]},
    });
    fireEvent.change(screen.getByRole('textbox', {name: ru.voice.importName}), {
        target: {value: 'Мой голос'},
    });
    fireEvent.change(screen.getByRole('textbox', {name: ru.voice.importLanguage}), {target: {value: 'ru'}});
    fireEvent.click(screen.getByRole('button', {name: ru.voice.importSubmit}));
    await waitFor(() => expect(view.imported).toHaveBeenCalledOnce());
    expect(mocks.uploadFile).toHaveBeenCalledWith(file, expect.any(AbortSignal), expect.any(Function));
    expect(mocks.importSpeechModel).toHaveBeenCalledExactlyOnceWith(
        {speechModelImportRequest: {uploadId: 'upload-id', title: 'Мой голос', language: 'ru'}},
        {signal: expect.any(AbortSignal)},
    );
    expect(screen.getByRole('status')).toHaveTextContent(ru.voice.importReady);
    view.unmount();
    expect(mocks.deleteUpload).not.toHaveBeenCalled();
});

it('cleans a staged upload after failure while preserving editable metadata', async () => {
    mocks.uploadFile.mockResolvedValue({id: 'failed-upload', fileName: 'voice.tar.bz2', size: 4});
    mocks.importSpeechModel.mockRejectedValue(new Error('Archive validation failed'));
    const view = setup();
    fireEvent.change(screen.getByLabelText(ru.voice.importArchive, {selector: 'input'}), {
        target: {files: [new File(['data'], 'voice.tar.bz2')]},
    });
    fireEvent.change(screen.getByRole('textbox', {name: ru.voice.importName}), {
        target: {value: 'Мой голос'},
    });
    fireEvent.click(screen.getByRole('button', {name: ru.voice.importSubmit}));
    expect(await screen.findByRole('alert')).toHaveTextContent('Archive validation failed');
    expect(mocks.deleteUpload).toHaveBeenCalledExactlyOnceWith({id: 'failed-upload'});
    expect(screen.getByRole('textbox', {name: ru.voice.importName})).toHaveValue('Мой голос');
    expect(view.imported).not.toHaveBeenCalled();
});

it('aborts an in-flight import and releases its upload when the settings panel unmounts', async () => {
    mocks.uploadFile.mockResolvedValue({id: 'pending-upload', fileName: 'voice.tar.bz2', size: 4});
    mocks.importSpeechModel.mockImplementation(() => new Promise(() => {}));
    const view = setup();
    fireEvent.change(screen.getByLabelText(ru.voice.importArchive, {selector: 'input'}), {
        target: {files: [new File(['data'], 'voice.tar.bz2')]},
    });
    fireEvent.change(screen.getByRole('textbox', {name: ru.voice.importName}), {
        target: {value: 'Мой голос'},
    });
    fireEvent.click(screen.getByRole('button', {name: ru.voice.importSubmit}));
    await waitFor(() => expect(mocks.importSpeechModel).toHaveBeenCalledOnce());
    const signal = mocks.importSpeechModel.mock.calls[0][1].signal as AbortSignal;
    view.unmount();
    expect(signal.aborted).toBe(true);
    expect(mocks.deleteUpload).toHaveBeenCalledExactlyOnceWith({id: 'pending-upload'});
});
