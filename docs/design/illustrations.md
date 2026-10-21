# Aigram artwork

The visual identity uses folded mint and blue ribbons to form an **A**, with a white conversation bubble as its crossbar. The mark and all three illustrations are original vector artwork maintained as SVG in the repository.

| Asset | Purpose |
| --- | --- |
| `web/public/assets/brand/aigram.svg` | Application mark, window bar and favicon |
| `web/public/assets/illustrations/welcome.svg` | Connected conversations on the sign-in screen |
| `web/public/assets/illustrations/conversations.svg` | Empty conversation selection |
| `web/public/assets/illustrations/activity.svg` | Empty activity list |

Transparent PNG illustrations keep the same filenames as their SVG sources for existing components. `node scripts/render-illustrations.cjs` regenerates them at 1254 × 1254 pixels using Sharp. Change the SVG sources rather than editing generated pixels.

`scripts/create-icon.ps1` renders the application mark to a 512-pixel PNG and a Windows ICO containing 16, 24, 32, 48, 64, 128 and 256-pixel images. Both the desktop icon and the updater therefore use the same source artwork. No browser, external image service or image-generation model is required to rebuild these assets.
