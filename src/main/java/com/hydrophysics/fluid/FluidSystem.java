package com.hydrophysics.fluid;

/**
 * Realistic fluid / water simulation system.
 *
 * <p>Planned capabilities:
 * <ul>
 *   <li><b>Pressure simulation</b> – water at depth exerts force on blocks and
 *       entities proportional to the column height above it.</li>
 *   <li><b>Flow velocity</b> – tracks directional flow speed per cell, enabling
 *       currents that push entities and items.</li>
 *   <li><b>Wave propagation</b> – surface disturbances (splash, explosion)
 *       radiate outward as damped sine waves.</li>
 *   <li><b>Erosion</b> – prolonged fast flow gradually converts dirt/sand
 *       neighbours into gravel or air.</li>
 *   <li><b>Fluid mixing</b> – lava/water interactions, temperature modelling.</li>
 * </ul>
 *
 * <p>The system operates on a chunk-local cell grid and hooks into Minecraft's
 * existing fluid tick scheduler via Mixins where necessary.
 */
public final class FluidSystem {

    // TODO: store per-world simulation grid here.

    /** Run one simulation step for the owning world. */
    public void tick() {
        // TODO: implement fluid step.
    }
}
