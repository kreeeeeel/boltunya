import {ipcRenderer} from 'electron';

ipcRenderer.on('updater:status', (_event, status: {message: string; version: string; progress?: number}) => {
    const text = document.querySelector('.status');
    const track = document.querySelector<HTMLElement>('.track');
    const fill = document.querySelector<HTMLElement>('.fill');
    const version = document.querySelector('.version');
    if (text) text.textContent = status.message;
    if (version) version.textContent = status.version;
    if (!track || !fill) return;
    const progress = typeof status.progress === 'number' ? Math.max(0, Math.min(100, status.progress)) : undefined;
    track.dataset.visible = String(progress !== undefined);
    if (progress === undefined) track.removeAttribute('aria-valuenow');
    else track.setAttribute('aria-valuenow', String(progress));
    fill.style.width = `${progress ?? 0}%`;
});
