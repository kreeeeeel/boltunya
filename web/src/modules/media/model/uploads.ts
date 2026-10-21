import type {components} from '@/shared/api/schema';

export type UploadedFile = components['schemas']['UploadedFile'];
export type AttachmentKind = 'media' | 'document';
export const MAX_ATTACHMENTS = 10;
export const MAX_UPLOAD_BYTES = 100 * 1024 * 1024;
export const MAX_PHOTO_BYTES = 10 * 1024 * 1024;
export const MAX_BATCH_BYTES = 500 * 1024 * 1024;

export function validAttachments(
    files: File[],
    kind: AttachmentKind,
): 'tooMany' | 'tooLarge' | 'unsupported' | '' {
    if (!files.length || files.length > MAX_ATTACHMENTS) {
        return 'tooMany';
    }
    if (
        files.reduce((total, file) => total + file.size, 0) > MAX_BATCH_BYTES ||
        files.some(
            (file) =>
                file.size > MAX_UPLOAD_BYTES ||
                file.size === 0 ||
                (kind === 'media' && file.type.startsWith('image/') && file.size > MAX_PHOTO_BYTES),
        )
    ) {
        return 'tooLarge';
    }
    if (
        kind === 'media' &&
        files.some(
            (file) =>
                !['image/jpeg', 'image/png', 'image/webp', 'video/mp4', 'video/webm'].includes(file.type),
        )
    ) {
        return 'unsupported';
    }
    return '';
}

export function uploadFile(
    file: File,
    signal: AbortSignal,
    progress: (loaded: number) => void,
): Promise<UploadedFile> {
    return new Promise((resolve, reject) => {
        const request = new XMLHttpRequest();
        request.open('POST', '/api/uploads');
        request.withCredentials = true;
        request.setRequestHeader('Content-Type', file.type || 'application/octet-stream');
        request.setRequestHeader('X-File-Name', encodeURIComponent(file.name));
        const abort = () => request.abort();
        signal.addEventListener('abort', abort, {once: true});
        request.onloadend = () => signal.removeEventListener('abort', abort);
        request.upload.onprogress = (event) => progress(event.loaded);
        request.onerror = () => reject(new Error('Upload connection failed'));
        request.onabort = () => reject(new DOMException('Upload cancelled', 'AbortError'));
        request.onload = () => {
            try {
                const result = JSON.parse(request.responseText);
                if (request.status < 200 || request.status >= 300) {
                    throw new Error(result.message ?? `HTTP ${request.status}`);
                }
                if (
                    typeof result.id !== 'string' ||
                    typeof result.fileName !== 'string' ||
                    typeof result.size !== 'number'
                ) {
                    throw new Error('Invalid upload response');
                }
                resolve(result as UploadedFile);
            } catch (cause) {
                reject(cause);
            }
        };
        if (signal.aborted) {
            reject(new DOMException('Upload cancelled', 'AbortError'));
            return;
        }
        request.send(file);
    });
}
