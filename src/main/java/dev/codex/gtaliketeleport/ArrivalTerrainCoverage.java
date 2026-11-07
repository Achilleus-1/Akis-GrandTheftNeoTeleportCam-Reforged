package dev.codex.gtaliketeleport;

/** The normal circular view, and its interior with enough data to build meshes. */
final class ArrivalTerrainCoverage {
    private ArrivalTerrainCoverage() {}

    static boolean contains(int dx, int dz, int radius) {
        long x = Math.max(0L, Math.abs((long) dx) - 1);
        long z = Math.max(0L, Math.abs((long) dz) - 1);
        return x * x + z * z < (long) radius * radius;
    }

    static boolean hasNeighborCoverage(int dx, int dz, int radius) {
        for (int z = -1; z <= 1; ++z) {
            for (int x = -1; x <= 1; ++x) {
                if (!contains(dx + x, dz + z, radius)) return false;
            }
        }
        return true;
    }

    static boolean prepare(int centerX, int centerZ, int playerX, int playerZ, int radius, ChunkProbe probe) {
        boolean complete = probe.isLoaded(centerX, centerZ);
        for (int dz = -radius; dz <= radius; ++dz) {
            for (int dx = -radius; dx <= radius; ++dx) {
                int x = centerX + dx;
                int z = centerZ + dz;
                if (!contains(dx, dz, radius) || !contains(x - playerX, z - playerZ, radius)) continue;
                if (!probe.isLoaded(x, z)) {
                    complete = false;
                    continue;
                }
                if (hasNeighborCoverage(dx, dz, radius)
                        && hasNeighborCoverage(x - playerX, z - playerZ, radius)
                        && !probe.prepareChunk(x, z)) complete = false;
            }
        }
        return complete;
    }

    interface ChunkProbe {
        boolean isLoaded(int x, int z);
        boolean prepareChunk(int x, int z);
    }
}
