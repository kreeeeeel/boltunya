import localFont from 'next/font/local';

export const uiFont = localFont({
    src: [
        {path: '../assets/fonts/NotoSans-Variable.ttf', weight: '100 900', style: 'normal'},
        {path: '../assets/fonts/NotoSans-Italic-Variable.ttf', weight: '100 900', style: 'italic'},
    ],
    variable: '--font-ui',
    display: 'swap',
    fallback: ['Segoe UI', 'Arial', 'sans-serif'],
});
