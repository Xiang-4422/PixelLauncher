# Ark Pixel Font 2026.07.20

- Upstream: https://github.com/TakWolf/ark-pixel-font
- Release: https://github.com/TakWolf/ark-pixel-font/releases/tag/2026.07.20
- Imported variants: `zh_cn`, 10/12/16px, proportional/monospaced BDF
- Runtime format: generated `manifest.json + glyphs.bin`; BDF files are build inputs only

The launcher loads only the selected Ark pack. Missing glyphs use the engine's
deterministic missing-glyph cell and never fall back to Fusion or another family.
