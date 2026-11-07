# Changelog

## 1.0.0-neoforge.10 - 2026-11-07

- Check destination chunk coverage and prepare vanilla terrain geometry outside the overhead camera's view, including underground sections.
- Replace the five-frame release with an elapsed-time settling period; restart it when destination chunks arrive and check destination column lighting without waiting on unrelated global queues.
- Monitor loading during descent and pause the camera in place if destination data becomes unavailable again.
- Use a conservative fallback for replacement/LOD renderers instead of depending on their private APIs or waiting indefinitely for unrelated background rendering work.
- Keep the loading hold entirely client-side, respecting the server's advertised view distance. Press Escape during a loading hold to restore normal camera control.

## 1.0.0-neoforge.9 - 2026-11-02

- Hold the camera above the destination until surrounding chunks within the effective render distance are received, lighting finishes, and terrain rendering settles.
- Resume the existing zoom-in animation after loading, including cross-dimension teleports and distant-terrain setups.
- Preserve existing settings, internal IDs, resources, and animation stages.

## 1.0.0-neoforge.8 - 2026-10-11

- Shortened credits and legal documentation.
- Preserved license notices and matching source access.
- No gameplay changes.

## 1.0.0-neoforge.7 - 2026-10-11

- Clarified original-author credits, ownership of port contributions, and upstream license provenance.
- Added packaged distribution notices and clear unofficial Minecraft attribution.
- Restored upstream Codex and migration-reference credits and the reference license notice. Original restricted audio remains excluded.
- No gameplay changes.
