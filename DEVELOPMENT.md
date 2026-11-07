# Development

Install JDK 21. From the repository root run `./gradlew build` (Windows:
`gradlew.bat build`). The committed wrapper selects the project Gradle version.
Run `python tools/check_repository.py` with Python 3.12 for source/privacy checks.

The existing Minecraft/NeoForge workflow builds the mod; the repository workflow
checks source and metadata. Test behavior in the matching Minecraft and NeoForge
versions declared in `gradle.properties`. A successful build does not verify
in-game behavior. Preserve the existing mod license and upstream attribution.

## Arrival loading checks

`gradlew.bat build` also runs the dependency-free arrival readiness regression suite.
It covers high/low FPS, interrupted loading, unbuilt offscreen terrain, server view
distances from 1 to 32 chunks, player movement, renderer fallback, and pausing during
descent without rewinding its timeline.

The hold checks normal chunk data and lighting client-side. With the vanilla
renderer it prepares nonempty destination sections asynchronously, eight per frame,
including sections outside the overhead view. Geometry at the outer edge requires
neighboring data that a server may not send, so that edge is checked as support data
rather than forcing impossible mesh requests.
Once a received section has a mesh, normal block updates do not restart initial
loading readiness; a replacement chunk packet invalidates that prepared coverage.

Vanilla readiness must remain stable for 1.5 seconds and at least three rendered
frames. With additional mods, use a 3-second settling window. If the renderer never
reports idle, after 8 seconds of continuous data/lighting readiness its queue signal
can be ignored, followed by the full settling window. This grace period never
bypasses missing chunk data or unlit destination columns. New destination chunk packets
restart readiness. Routine light-only updates and unrelated global lighting queues
do not starve the hold. Third-party LOD terrain outside the server's normal view distance
has no universal completion API; compatibility mode is deliberately conservative.

Once descent begins, lost/replaced destination data pauses its animation clock.
Routine background work from replacement renderers does not repeatedly stop it.
Escape cancels an active loading hold. No new server payloads, tickets, or chunk
requests are used.
