import type {SpeechAsset, SpeechState, SpeechDownload} from '@aigram/api-client';

export function speechInstallationProgress(
    asset: SpeechAsset,
    state?: SpeechState,
): SpeechDownload | undefined {
    const own = state?.downloads.find((download) => download.assetId === asset.id);
    if (!own || !['queued', 'downloading', 'extracting'].includes(own.phase) || !asset.dependencies.length) {
        return own;
    }
    const dependencies = asset.dependencies
        .flatMap((id) => state?.assets.find((candidate) => candidate.id === id) ?? [])
        .filter((dependency) => state?.downloads.some((download) => download.assetId === dependency.id));
    if (!dependencies.length) {
        return own;
    }
    const activeDependency = state?.downloads.find(
        (download) =>
            asset.dependencies.includes(download.assetId) &&
            ['downloading', 'extracting'].includes(download.phase),
    );
    if (own.phase === 'queued' && !activeDependency) {
        return own;
    }
    const dependencyBytes = dependencies.reduce((bytes, dependency) => {
        if (dependency.installed) {
            return bytes + dependency.sizeBytes;
        }
        const download = state?.downloads.find((candidate) => candidate.assetId === dependency.id);
        return bytes + Math.min(dependency.sizeBytes, download?.downloadedBytes ?? 0);
    }, 0);
    return {
        ...own,
        phase: own.phase === 'queued' ? 'downloading' : own.phase,
        totalBytes: dependencies.reduce((bytes, dependency) => bytes + dependency.sizeBytes, asset.sizeBytes),
        downloadedBytes: dependencyBytes + Math.min(asset.sizeBytes, own.downloadedBytes),
    };
}
