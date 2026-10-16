export type DesktopWindowAction = 'show' | 'minimize' | 'maximize' | 'close';

export interface DesktopWindowState {
    available: boolean;
    maximized: boolean;
    visible: boolean;
    minimized: boolean;
}

declare global {
    interface Window {
        boltunyaDesktop?: {
            state: () => Promise<DesktopWindowState>;
            sync: () => Promise<void>;
            quit: () => Promise<void>;
            action: (action: string) => Promise<void>;
            onState: (callback: (state: DesktopWindowState) => void) => () => void;
        };
        boltunyaTray?: {action: (action: string) => Promise<void>};
    }
}

export function desktopBridge() {
    return typeof window === 'undefined' ? undefined : window.boltunyaDesktop;
}
