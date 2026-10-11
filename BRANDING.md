# Aki's Grand Theft Neo Teleport Cam Reforged branding

Artwork by Achilleus. The supplied cover is the identity for this mod.

| Asset | Use |
| --- | --- |
| [Original cover](docs/cover.png) | Unmodified 1024 x 1024 PNG with original transparency |
| [Cover preview](docs/cover-preview.png) | Identical transparent original, retained for existing image links; the README uses `docs/cover.png` |
| [Mod icon](src/main/resources/icon.png) | 512 x 512 PNG for the NeoForge Mods screen, Modrinth, CurseForge, and launcher listings |
| [Resource-pack icon](src/main/resources/pack.png) | Matching 512 x 512 icon packaged in the mod JAR |
| [Social preview](docs/social-preview.png) | 1280 x 640 PNG for GitHub repository Settings > General > Social preview |

`META-INF/neoforge.mods.toml` selects `icon.png`. Gradle includes both packaged icons in every build. Bundled resource packs, where present, use the same mod artwork.

All PNG exports preserve transparency and the full composition. No background is added to the icons, covers, or social preview. The original cover and cover preview are byte-identical to the supplied artwork. Use the same transparent mod icon for external project avatars and the cover for promotional artwork. GitHub's social preview must be uploaded separately in repository settings.

Product of [Achilleus](https://linktr.ee/achilleus_). Upstream authors and licenses remain credited in the README and packaged notices.
