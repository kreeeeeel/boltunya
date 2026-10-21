import {Suspense} from 'react';
import {MediaViewerPage} from '@/modules/media/MediaViewer/MediaViewerPage';

export default function Page() {
    return (
        <Suspense>
            <MediaViewerPage />
        </Suspense>
    );
}
