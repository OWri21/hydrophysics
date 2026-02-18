package com.hydrophysics.simulation;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Uniform-grid spatial index for fast neighbour queries over a set of
 * {@link FluidParticle}s.
 *
 * <h2>Why not brute force?</h2>
 *
 * The naive approach — testing every (i, j) pair — is O(N²) per simulation
 * step.  For 5 000 active particles that is 25 million distance checks per
 * tick; entirely unacceptable at 20 TPS.
 *
 * <p>The spatial hash grid exploits the fact that SPH kernels have finite
 * support: particle {@code i} can only influence particle {@code j} when
 * {@code |rᵢ − rⱼ| < h} (the smoothing radius).  By mapping every particle
 * into a 3-D grid cell of side length {@code h}, a neighbour query only needs
 * to examine the (at most) 3³ = 27 cells whose union covers the sphere of
 * radius {@code h} around the query point.  Expected work per query is
 * O(k) where k ≪ N is the mean occupancy of those 27 cells.
 *
 * <h2>Hash strategy — packed-long keys, no modular collisions</h2>
 *
 * A common SPH trick is {@code hash(ix,iy,iz) = (ix*p1 ^ iy*p2 ^ iz*p3) % M}
 * with large primes.  This is fast but can produce <em>false-negative</em>
 * misses when two distinct cells collide to the same bucket.
 *
 * <p>Instead, this implementation packs the signed cell coordinates
 * {@code (ix, iy, iz)} directly into a {@code long} key (21 bits per axis →
 * range ±1 048 575, far exceeding any realistic Minecraft world) and uses
 * Java's {@link HashMap} for the backing store.  The only "hash" is Java's
 * own Long hash, which has zero spatial false-negatives.
 *
 * <pre>
 * Key layout (63 bits used):
 *   bits 62–42  ← ix  (21 bits, two's-complement via mask)
 *   bits 41–21  ← iy
 *   bits 20– 0  ← iz
 * </pre>
 *
 * <h2>Usage pattern (one simulation step)</h2>
 * <pre>{@code
 *   grid.clear();
 *   for (FluidParticle p : activeParticles) grid.insert(p);
 *   // ... then for each particle:
 *   List<FluidParticle> neighbours = grid.queryRadius(p.position, h);
 * }</pre>
 */
public final class SpatialHashGrid {

    /** Side length of each cubic cell.  Set equal to the SPH smoothing radius h. */
    private final float cellSize;

    /** Backing map: packed cell key → list of particles in that cell. */
    private final Map<Long, List<FluidParticle>> table = new HashMap<>();

    /**
     * @param cellSize side length of each grid cell in world units (blocks).
     *                 Must equal the SPH smoothing radius for correct results.
     * @throws IllegalArgumentException if {@code cellSize} is not positive
     */
    public SpatialHashGrid(float cellSize) {
        if (cellSize <= 0f) throw new IllegalArgumentException("cellSize must be positive, got: " + cellSize);
        this.cellSize = cellSize;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Insert a particle into the cell that contains its current position.
     *
     * <p>The grid does <em>not</em> track movement: if a particle moves after
     * insertion the index becomes stale.  Always call {@link #clear()} and
     * re-insert all active particles at the start of each simulation step.
     *
     * @param p particle to insert
     */
    public void insert(FluidParticle p) {
        table.computeIfAbsent(keyOf(p.position), k -> new ArrayList<>()).add(p);
    }

    /**
     * Return every particle within Euclidean distance {@code radius} of
     * {@code centre}.
     *
     * <p>Only cells whose axis-aligned bounding box overlaps the query sphere
     * are visited.  When {@code radius == cellSize} this covers exactly the
     * 3 × 3 × 3 = 27 neighbouring cells.
     *
     * <p>A final exact distance check filters any particles that are inside a
     * visited cell but geometrically outside the sphere (corner regions of the
     * AABB that extend beyond the sphere).  This means the method never
     * returns false positives.
     *
     * @param centre query point
     * @param radius search radius in world units; must be ≤ {@code cellSize}
     *               to limit the search to 27 cells
     * @return mutable list of matching particles (may be empty, never null)
     */
    public List<FluidParticle> queryRadius(Vec3d centre, float radius) {
        // Convert the sphere AABB corners into cell coordinates.
        int minX = cellCoord(centre.x - radius);
        int maxX = cellCoord(centre.x + radius);
        int minY = cellCoord(centre.y - radius);
        int maxY = cellCoord(centre.y + radius);
        int minZ = cellCoord(centre.z - radius);
        int maxZ = cellCoord(centre.z + radius);

        double r2 = (double) radius * radius;
        List<FluidParticle> result = new ArrayList<>();

        for (int ix = minX; ix <= maxX; ix++) {
            for (int iy = minY; iy <= maxY; iy++) {
                for (int iz = minZ; iz <= maxZ; iz++) {
                    List<FluidParticle> cell = table.get(cellKey(ix, iy, iz));
                    if (cell == null) continue;

                    for (FluidParticle p : cell) {
                        // Exact distance check: reject particles in the cell's
                        // corner regions that lie outside the query sphere.
                        if (centre.squaredDistanceTo(p.position) <= r2) {
                            result.add(p);
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * Remove all particles from the grid.
     *
     * <p>Call this at the start of every simulation step before re-inserting
     * updated particle positions.  Reusing the same grid instance and clearing
     * it is faster than allocating a fresh one each step.
     */
    public void clear() {
        table.clear();
    }

    // -------------------------------------------------------------------------
    // Diagnostic helpers
    // -------------------------------------------------------------------------

    /** Number of non-empty cells currently in the grid. */
    public int cellCount() {
        return table.size();
    }

    /** Total number of particle references stored across all cells. */
    public int storedParticleCount() {
        return table.values().stream().mapToInt(List::size).sum();
    }

    /** Mean particles per occupied cell (useful for tuning {@code cellSize}). */
    public double meanOccupancy() {
        int c = cellCount();
        return c == 0 ? 0.0 : (double) storedParticleCount() / c;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Map a continuous world coordinate to a signed integer cell index.
     *
     * <p>{@link Math#floor} is used (not truncation) so negative coordinates
     * snap to the correct cell: e.g. position {@code −0.1} belongs to cell
     * {@code −1}, not cell {@code 0}.
     */
    private int cellCoord(double v) {
        return (int) Math.floor(v / cellSize);
    }

    /** Convenience: compute the key for a world-space position. */
    private long keyOf(Vec3d pos) {
        return cellKey(cellCoord(pos.x), cellCoord(pos.y), cellCoord(pos.z));
    }

    /**
     * Pack three signed cell coordinates into a single {@code long}.
     *
     * <p>Each coordinate is masked to 21 bits before packing, which:
     * <ul>
     *   <li>Handles negative values correctly (two's-complement representation
     *       is preserved in the low 21 bits).</li>
     *   <li>Gives an unambiguous unique key for positions within ±1 048 575
     *       blocks per axis — well beyond the Minecraft world border.</li>
     * </ul>
     *
     * <pre>
     * Bit layout:
     *   bits 62–42  →  ix & 0x1F_FFFF
     *   bits 41–21  →  iy & 0x1F_FFFF
     *   bits 20– 0  →  iz & 0x1F_FFFF
     * </pre>
     */
    private static long cellKey(int ix, int iy, int iz) {
        long x = ix & 0x1F_FFFFL;
        long y = iy & 0x1F_FFFFL;
        long z = iz & 0x1F_FFFFL;
        return (x << 42) | (y << 21) | z;
    }
}
