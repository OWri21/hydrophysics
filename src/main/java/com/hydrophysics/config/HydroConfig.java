package com.hydrophysics.config;

import com.hydrophysics.HydroPhysics;

/**
 * Central configuration for HydroPhysics.
 *
 * <p>Holds tunable parameters for water physics and explosion behaviour.
 * Values can later be wired to a config file (e.g. via Cloth Config or a
 * hand-rolled TOML loader) without changing call sites elsewhere in the mod.
 */
public final class HydroConfig {

    // -------------------------------------------------------------------------
    // Fluid / water physics
    // -------------------------------------------------------------------------

    /** Maximum simulation ticks per fluid cell per game tick. */
    public static int fluidSimStepsPerTick = 2;

    /** Whether realistic water pressure is applied (depth-based force). */
    public static boolean enablePressureSimulation = true;

    /** Whether water carves / erodes soft terrain over time. */
    public static boolean enableErosion = false;

    // -------------------------------------------------------------------------
    // Explosion physics
    // -------------------------------------------------------------------------

    /** Whether explosions displace water volumes instead of voiding them. */
    public static boolean explosionDisplacesWater = true;

    /** Multiplier applied to blast radius when the explosion is underwater. */
    public static float underwaterBlastRadiusMultiplier = 0.5f;

    /** Whether shockwaves propagate through water columns. */
    public static boolean enableUnderwaterShockwaves = true;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    private HydroConfig() {}

    /** Called once at mod initialisation to load / validate all values. */
    public static void init() {
        HydroPhysics.LOGGER.info("[HydroPhysics] Config loaded (defaults active – persistent config not yet implemented).");
        // TODO: deserialise from a config file here.
    }
}
