package com.hydrophysics;

import com.hydrophysics.config.HydroConfig;
import com.hydrophysics.event.HydroEvents;
import com.hydrophysics.registry.HydroRegistries;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for the HydroPhysics mod.
 *
 * <p>Boot order:
 * <ol>
 *   <li>{@link HydroConfig}      – load / validate configuration values</li>
 *   <li>{@link HydroRegistries}  – register blocks, items, entities, sounds …</li>
 *   <li>{@link HydroEvents}      – subscribe to Fabric event callbacks</li>
 * </ol>
 */
public class HydroPhysics implements ModInitializer {

    public static final String MOD_ID = "hydrophysics";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[HydroPhysics] Initialising…");

        HydroConfig.init();
        HydroRegistries.init();
        HydroEvents.register();

        LOGGER.info("[HydroPhysics] Ready.");
    }
}
