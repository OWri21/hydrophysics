package com.hydrophysics.util;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * General-purpose utility methods shared across HydroPhysics systems.
 *
 * <p>Keep this class strictly stateless and free of side-effects so it can be
 * called safely from any context (server tick, Mixin, event handler).
 */
public final class HydroUtil {

    private HydroUtil() {}

    /**
     * Returns the number of consecutive fluid blocks directly above {@code pos}
     * (i.e. the water column height at that position).
     *
     * @param world the world to query
     * @param pos   the position to measure from (exclusive – does not count pos itself)
     * @return column height in blocks, or {@code 0} if the block directly above is not fluid
     */
    public static int getFluidColumnHeight(World world, BlockPos pos) {
        int height = 0;
        BlockPos.Mutable cursor = pos.mutableCopy().move(0, 1, 0);
        while (!world.getFluidState(cursor).isEmpty()) {
            height++;
            cursor.move(0, 1, 0);
        }
        return height;
    }

    /**
     * Linearly interpolates between {@code a} and {@code b} by factor {@code t}.
     *
     * @param a start value
     * @param b end value
     * @param t interpolation factor in [0, 1]
     */
    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
