import type * as Contract from '@aigram/api-client';
import {api} from '@/shared/api/client';
import {apiConfiguration} from '@/shared/api/configuration';
export type UploadedFile = Contract.UploadedFile;
export const MAX_UPLOAD_BYTES = 100 * 1024 * 1024;
export async function uploadFile(
    file: File,
    signal: AbortSignal,
    progress: (loaded: number) => void,
): Promise<UploadedFile> {
    const options = await api.media.uploadFileRequestOpts({
        xFileName: encodeURIComponent(file.name),
        body: file,
    });
    return new Promise((resolve, reject) => {
        const request = new XMLHttpRequest();
        request.open(options.method, apiConfiguration.basePath + options.path);
        request.withCredentials = true;
        Object.entries({...options.headers, 'Content-Type': file.type || 'application/octet-stream'}).forEach(
            ([name, value]) => {
                request.setRequestHeader(name, value);
            },
        );
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
