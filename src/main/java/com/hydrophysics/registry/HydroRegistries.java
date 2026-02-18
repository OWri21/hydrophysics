package com.hydrophysics.registry;

import com.hydrophysics.HydroPhysics;

/**
 * Aggregates all registry calls for HydroPhysics.
 *
 * <p>Add dedicated registration classes per category (blocks, items, block
 * entities, sounds, particles …) and call them from {@link #init()}.
 *
 * <pre>
 * Future structure:
 *   HydroBlocks.register()
 *   HydroItems.register()
 *   HydroBlockEntities.register()
 *   HydroSounds.register()
 *   HydroParticles.register()
 * </pre>
 */
public final class HydroRegistries {

    private HydroRegistries() {}

    /** Initialise every registry sub-system in dependency order. */
    public static void init() {
        HydroPhysics.LOGGER.debug("[HydroPhysics] Registering content…");
        // TODO: call individual register() methods as content is added.
    }
}
