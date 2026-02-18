package com.hydrophysics.event;

import com.hydrophysics.HydroPhysics;

/**
 * Central hub for subscribing to Fabric API event callbacks.
 *
 * <p>Register callbacks here so that the full set of hooks is visible in one
 * place. Delegate the actual logic to the appropriate system class.
 *
 * <pre>
 * Planned hooks:
 *   ServerTickEvents.END_SERVER_TICK  – drive fluid simulation each tick
 *   ServerWorldEvents.LOAD            – attach per-world simulation state
 *   ExplosionEvents (custom)          – intercept / redirect explosion logic
 *   BlockEvent.PLACE / BREAK         – trigger erosion / flow recalculation
 * </pre>
 */
public final class HydroEvents {

    private HydroEvents() {}

    /** Subscribe all event callbacks. Called once during {@code onInitialize}. */
    public static void register() {
        HydroPhysics.LOGGER.debug("[HydroPhysics] Registering event listeners…");
        // TODO: register Fabric API event callbacks here.
    }
}
