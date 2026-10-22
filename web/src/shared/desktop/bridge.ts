export type DesktopWindowAction = 'show' | 'minimize' | 'maximize' | 'close' | 'fullscreen';

export interface DesktopWindowState {
    available: boolean;
    maximized: boolean;
    visible: boolean;
    minimized: boolean;
    fullscreen?: boolean;
    role?: 'main';
}

export interface DesktopBridge {
    state: () => Promise<DesktopWindowState>;
    sync: () => Promise<void>;
    quit: () => Promise<void>;
    action: (action: string) => Promise<void>;
    openStorageLocation: (id: string) => Promise<void>;
    onState: (callback: (state: DesktopWindowState) => void) => () => void;
}

declare global {
    interface Window {
        aigramDesktop?: DesktopBridge;
        boltunyaDesktop?: DesktopBridge;
        aigramTray?: {action: (action: string) => Promise<void>};
        boltunyaTray?: {action: (action: string) => Promise<void>};
    }
}

export function desktopBridge() {
    return typeof window === 'undefined' ? undefined : (window.aigramDesktop ?? window.boltunyaDesktop);
}

export function trayBridge() {
    return typeof window === 'undefined' ? undefined : (window.aigramTray ?? window.boltunyaTray);
}
