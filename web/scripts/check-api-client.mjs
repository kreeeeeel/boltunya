import {access} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';

const source = new URL(
    '../../backend/contract/client/typescript/build/generated/openapi/src/',
    import.meta.url,
);
const required = ['index.ts', 'runtime.ts', 'models/index.ts', 'apis/ChatsApi.ts', 'apis/SystemApi.ts'];

try {
    await Promise.all(required.map((file) => access(new URL(file, source))));
} catch {
    throw new Error(
        `The generated Aigram API client is missing at ${fileURLToPath(source)}. ` +
            'Run ./gradlew :backend:contract:client:typescript:generateClient from the repository root before npm check or build.',
    );
}
