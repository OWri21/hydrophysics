package com.hydrophysics.fluid;

import com.hydrophysics.simulation.FluidParticle;
import com.hydrophysics.simulation.FluidSimulator;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

import java.util.List;

/**
 * Fluid / water system for HydroPhysics.
 *
 * <p>This class owns a {@link FluidSimulator} instance and acts as the bridge
 * between the Minecraft tick lifecycle (called from
 * {@link com.hydrophysics.physics.PhysicsEngine}) and the SPH solver.
 *
 * <p>Planned higher-level capabilities (built on top of the particle sim):
 * <ul>
 *   <li><b>Wave propagation</b> – surface disturbances radiate as damped waves.</li>
 *   <li><b>Erosion</b> – prolonged fast flow converts dirt/sand to gravel.</li>
 *   <li><b>Fluid mixing</b> – lava/water interactions, temperature modelling.</li>
 *   <li><b>Minecraft block integration</b> – spawning particles from vanilla
 *       water blocks, writing flow data back to block states.</li>
 * </ul>
 */
public final class FluidSystem {

    /** The SPH solver.  One instance per world / per PhysicsEngine. */
    private final FluidSimulator simulator = new FluidSimulator();

    // -------------------------------------------------------------------------
    // Tick entry point
    // -------------------------------------------------------------------------

    /**
     * Advance the fluid simulation by one server tick.
     *
     * <p>Called from {@link com.hydrophysics.physics.PhysicsEngine#tick} once
     * per server tick, passing the authoritative elapsed time so the internal
     * fixed-step accumulator can fire the correct number of SPH sub-steps.
     *
     * @param world     the server world (for chunk-loaded queries)
     * @param players   currently online players (define the active simulation zone)
     * @param deltaTime seconds elapsed since the last tick (typically 0.05 s)
     */
    public void tick(World world, List<? extends PlayerEntity> players, double deltaTime) {
        simulator.update(deltaTime, world, players);
    }

    // -------------------------------------------------------------------------
    // Particle management helpers
    // -------------------------------------------------------------------------

    /**
     * Spawn a new fluid particle at the given world-space position.
     *
     * @param position world position (one unit = one block)
     * @param mass     particle mass in kg; use {@link FluidSimulator#SMOOTHING_RADIUS}
     *                 as a sizing guide (denser packing → smaller mass per particle)
     * @return the newly created particle, already added to the simulator
     */
    public FluidParticle spawnParticle(net.minecraft.util.math.Vec3d position, float mass) {
        FluidParticle p = new FluidParticle(position, mass);
        simulator.addParticle(p);
        return p;
    }

    /**
     * Remove a particle from the simulation (e.g. when it leaves the world or
     * is absorbed by a block).
     *
     * @param p particle to remove
     */
    public void despawnParticle(FluidParticle p) {
        simulator.removeParticle(p);
    }

    // -------------------------------------------------------------------------
    // Access
    // -------------------------------------------------------------------------

    /**
     * Direct access to the underlying SPH simulator.
     *
     * <p>Prefer the higher-level methods on this class where possible; expose
     * the simulator for advanced use-cases (e.g. the explosion system injecting
     * velocity impulses directly into the particle field).
     */
    public FluidSimulator getSimulator() {
        return simulator;
    }
}
