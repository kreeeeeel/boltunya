import {afterEach, expect, it, vi} from 'vitest';
import {MAX_UPLOAD_BYTES, uploadFile, validAttachments} from '@/modules/media/model/uploads';

afterEach(() => vi.unstubAllGlobals());

it('validates whole attachment batches before any network request', () => {
    const photo = new File(['image'], 'photo.jpg', {type: 'image/jpeg'});
    const video = new File(['video'], 'video.mp4', {type: 'video/mp4'});
    expect(validAttachments([photo, video], 'media')).toBe('');
    expect(validAttachments(Array(11).fill(photo), 'media')).toBe('tooMany');
    expect(validAttachments([new File(['pdf'], 'report.pdf', {type: 'application/pdf'})], 'media')).toBe(
        'unsupported',
    );
    expect(validAttachments([new File(['pdf'], 'report.pdf', {type: 'application/pdf'})], 'document')).toBe(
        '',
    );
    const large = new File(['x'], 'large.bin');
    Object.defineProperty(large, 'size', {value: MAX_UPLOAD_BYTES + 1});
    expect(validAttachments([large], 'document')).toBe('tooLarge');
    expect(validAttachments([new File([], 'empty.bin')], 'document')).toBe('tooLarge');
});

it('uses the generated upload operation while retaining native progress and cancellation', async () => {
    const completed = {id: 'upload-1', fileName: 'отчёт.pdf', size: 4, mimeType: 'application/pdf'};
    const request = {
        open: vi.fn(),
        setRequestHeader: vi.fn(),
        send: vi.fn(),
        abort: vi.fn(),
        upload: {onprogress: undefined as ((event: {loaded: number}) => void) | undefined},
        onload: undefined as (() => void) | undefined,
        onloadend: undefined as (() => void) | undefined,
        onabort: undefined as (() => void) | undefined,
        status: 200,
        responseText: JSON.stringify(completed),
    };
    request.abort.mockImplementation(() => {
        request.onabort?.();
        request.onloadend?.();
    });
    vi.stubGlobal(
        'XMLHttpRequest',
        vi.fn(function () {
            return request;
        }),
    );
    const file = new File(['test'], 'отчёт.pdf', {type: 'application/pdf'});
    const progress = vi.fn();
    const pending = uploadFile(file, new AbortController().signal, progress);
    await vi.waitFor(() => expect(request.send).toHaveBeenCalledWith(file));
    expect(request.open).toHaveBeenCalledWith('POST', '/api/uploads');
    expect(request.setRequestHeader).toHaveBeenCalledWith('X-File-Name', encodeURIComponent(file.name));
    expect(request.setRequestHeader).toHaveBeenCalledWith('Content-Type', file.type);
    request.upload.onprogress?.({loaded: 2});
    expect(progress).toHaveBeenCalledWith(2);
    request.onload?.();
    request.onloadend?.();
    expect(await pending).toEqual(completed);

    const controller = new AbortController();
    const cancelled = uploadFile(file, controller.signal, progress);
    await vi.waitFor(() => expect(request.send).toHaveBeenCalledTimes(2));
    controller.abort();
    await expect(cancelled).rejects.toMatchObject({name: 'AbortError'});
});
