package com.hydrophysics.simulation;

import net.minecraft.util.math.Vec3d;

/**
 * A single SPH (Smoothed Particle Hydrodynamics) fluid particle.
 *
 * <p>Each particle represents a small parcel of fluid and carries all
 * physical state required by the SPH solver.  The solver itself lives in
 * {@link FluidSimulator}; this class is purely a data holder.
 *
 * <h2>Field categories</h2>
 * <ul>
 *   <li><b>Persistent</b> – {@code position}, {@code velocity}, {@code mass}.
 *       These must be saved/restored if particle state is serialised across
 *       sessions.</li>
 *   <li><b>Derived</b> – {@code density}, {@code pressure}, {@code acceleration}.
 *       Recomputed from scratch every simulation step; do not rely on them
 *       between steps and do not persist them.</li>
 * </ul>
 */
public final class FluidParticle {

    // -------------------------------------------------------------------------
    // Persistent state
    // -------------------------------------------------------------------------

    /**
     * World-space position in metres.  One unit equals one Minecraft block.
     * Updated each step by the symplectic Euler integrator.
     */
    public Vec3d position;

    /**
     * Current velocity in m/s.
     * Updated each step after forces are accumulated.
     */
    public Vec3d velocity;

    /**
     * Rest mass of this particle in kg.
     *
     * <p>Kept per-particle (rather than a global constant) so future systems
     * can mix particle sizes or fluid types within the same simulation.
     * A typical value for a small water parcel is {@code 0.02f} kg.
     */
    public final float mass;

    // -------------------------------------------------------------------------
    // Derived quantities  (written each step, not persisted)
    // -------------------------------------------------------------------------

    /**
     * Local fluid density ρᵢ in kg/m³, estimated by summing the Poly6 kernel
     * contribution of every neighbouring particle.
     *
     * <p>Clamped to a small positive value ({@code 1e-4f}) to prevent
     * division-by-zero in subsequent pressure and force calculations.
     */
    public float density;

    /**
     * Local pressure pᵢ in Pa, derived from density via the ideal-gas law:
     *
     * <pre>  pᵢ = k · (ρᵢ − ρ₀)</pre>
     *
     * where {@code k} is the stiffness constant and {@code ρ₀} the rest density.
     * Negative values are allowed and produce a mild cohesive pull at surfaces.
     */
    public float pressure;

    /**
     * Net acceleration in m/s² for the current step, written by the force-
     * accumulation pass and consumed by the integration pass.
     *
     * <p>Package-private — only {@link FluidSimulator} should read or write
     * this field.
     */
    Vec3d acceleration = Vec3d.ZERO;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Create a new particle at rest.
     *
     * @param position initial world-space position (one unit = one block)
     * @param mass     particle mass in kg (e.g. {@code 0.02f})
     */
    public FluidParticle(Vec3d position, float mass) {
        if (mass <= 0f) throw new IllegalArgumentException("mass must be positive");
        this.position = position;
        this.velocity = Vec3d.ZERO;
        this.mass     = mass;
    }

    /**
     * Create a new particle with an initial velocity.
     *
     * @param position        initial world-space position
     * @param initialVelocity initial velocity in m/s
     * @param mass            particle mass in kg
     */
    public FluidParticle(Vec3d position, Vec3d initialVelocity, float mass) {
        this(position, mass);
        this.velocity = initialVelocity;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Kinetic energy of this particle (½mv²), useful for diagnostics. */
    public double kineticEnergy() {
        double v2 = velocity.lengthSquared();
        return 0.5 * mass * v2;
    }

    @Override
    public String toString() {
        return String.format(
                "FluidParticle{pos=(%.2f,%.2f,%.2f) vel=(%.2f,%.2f,%.2f) ρ=%.1f p=%.1f}",
                position.x, position.y, position.z,
                velocity.x, velocity.y, velocity.z,
                density, pressure);
    }
}
