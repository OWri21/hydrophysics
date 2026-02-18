package com.hydrophysics.physics;

/**
 * Core physics engine for HydroPhysics.
 *
 * <p>Responsible for coordinating per-tick simulation across all registered
 * physics sub-systems (fluid dynamics, rigid-body splash, pressure waves …).
 *
 * <p>Design intent:
 * <ul>
 *   <li>One {@code PhysicsEngine} instance per server world, stored in world
 *       state via a custom {@code PersistentState} attachment.</li>
 *   <li>Sub-systems ({@link com.hydrophysics.fluid.FluidSystem},
 *       {@link com.hydrophysics.explosion.ExplosionSystem}) are injected and
 *       called in a deterministic order each tick.</li>
 *   <li>All heavy work runs on the server thread; client-side is purely visual.</li>
 * </ul>
 */
public final class PhysicsEngine {

    // TODO: inject world reference and sub-system instances via constructor.

    /** Advance all registered sub-systems by one game tick. */
    public void tick() {
        // TODO: implement tick dispatch.
    }
}
