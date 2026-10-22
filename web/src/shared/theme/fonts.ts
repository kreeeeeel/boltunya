import localFont from 'next/font/local';

export const uiFont = localFont({
    src: '../assets/fonts/GolosText.woff2',
    weight: '400 900',
    style: 'normal',
    variable: '--font-ui',
    display: 'swap',
    fallback: ['Segoe UI', 'Arial', 'sans-serif'],
});
