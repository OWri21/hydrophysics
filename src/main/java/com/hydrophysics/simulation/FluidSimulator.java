package com.hydrophysics.simulation;

import com.hydrophysics.HydroPhysics;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Smoothed Particle Hydrodynamics (SPH) fluid simulator.
 *
 * <h2>Background — what is SPH?</h2>
 *
 * SPH is a mesh-free Lagrangian method where the fluid is represented by a
 * set of moving particles.  Physical quantities at any point in space are
 * estimated by <em>smoothing</em> — computing a weighted average over nearby
 * particles using a radially-symmetric kernel function W(r, h):
 *
 * <pre>  A(x) = Σⱼ mⱼ/ρⱼ · Aⱼ · W(|x − xⱼ|, h)</pre>
 *
 * where {@code h} is the <em>smoothing radius</em> (kernel support), {@code m}
 * is mass and {@code ρ} is density.  The method naturally handles free surfaces
 * and large deformations — ideal for splashing water.
 *
 * <h2>Implemented forces</h2>
 * <ol>
 *   <li><b>Density + pressure</b> via the Poly6 kernel (Müller et al. 2003)</li>
 *   <li><b>Pressure gradient force</b> via the Spiky kernel gradient</li>
 *   <li><b>Viscosity</b> via the Viscosity kernel Laplacian</li>
 *   <li><b>Gravity</b> — constant downward acceleration</li>
 * </ol>
 *
 * <h2>References</h2>
 * Müller, Charypar, Gross — "Particle-Based Fluid Simulation for Interactive
 * Applications", SCA 2003.
 *
 * <h2>Performance</h2>
 * Neighbour queries are O(k) per particle (not O(N)) thanks to the
 * {@link SpatialHashGrid}.  Particles outside loaded chunks or farther than
 * {@value ACTIVE_RADIUS} blocks from any player are frozen each step.
 * The simulation runs on a fixed sub-step of {@value FIXED_DT_SECONDS} s,
 * independent of the server tick rate, using a time accumulator.
 */
public final class FluidSimulator {

    // =========================================================================
    // SPH tuning parameters
    // =========================================================================

    /**
     * Smoothing radius {@code h} in metres (one unit ≈ one Minecraft block).
     *
     * <p>Particles whose centres are more than {@code h} apart do not interact.
     * Larger values smooth more neighbours → more stable but lower-resolution.
     * This also sets the cell size used by the {@link SpatialHashGrid}.
     */
    public static final float SMOOTHING_RADIUS = 1.0f;

    /**
     * Rest density {@code ρ₀} in kg/m³.
     *
     * <p>Water is ~1 000 kg/m³.  The simulation tries to keep particle
     * density near this value; deviations create a restoring pressure force.
     */
    private static final float REST_DENSITY = 1_000.0f;

    /**
     * Stiffness constant {@code k} (Pa·m³/kg).
     *
     * <p>Appears in the ideal-gas equation of state:  p = k·(ρ − ρ₀).
     * Higher k → stiffer, less compressible fluid, but requires a smaller
     * {@link #FIXED_DT_SECONDS} for numerical stability.  2000 is a common
     * starting point for interactive simulations.
     */
    private static final float GAS_CONSTANT = 2_000.0f;

    /**
     * Dynamic viscosity {@code μ} in Pa·s.
     *
     * <p>Water ≈ 0.001 Pa·s, but SPH needs higher values (~100–500) to damp
     * numerical noise caused by the discrete particle representation.
     * Increase if the simulation becomes explosive; decrease for thinner fluid.
     */
    private static final float VISCOSITY = 250.0f;

    /**
     * Gravitational acceleration in m/s².  Negative = downward.
     *
     * <p>Applied directly as a constant body force; no kernel required.
     */
    private static final float GRAVITY = -9.81f;

    /**
     * Fixed simulation sub-step in seconds.
     *
     * <p>The simulator accumulates real elapsed time and fires one SPH step
     * per {@code FIXED_DT_SECONDS} regardless of the server tick rate.
     * Keep ≤ 0.02 s (50 Hz).  Decrease if the simulation diverges.
     */
    private static final double FIXED_DT_SECONDS = 0.016;  // ~60 Hz

    /**
     * Horizontal radius (in blocks) around each player within which particles
     * are actively simulated.  Particles beyond this distance are frozen —
     * their state is preserved but they incur zero CPU cost.
     */
    private static final int ACTIVE_RADIUS = 32;

    // =========================================================================
    // Pre-computed kernel constants for h = SMOOTHING_RADIUS
    //
    // These are derived analytically and baked at class-load time so the hot
    // simulation loop does no repeated division or Math.pow calls.
    // =========================================================================

    /** h, h², h⁶, h⁹ — used repeatedly in kernel formulas. */
    private static final double H  = SMOOTHING_RADIUS;
    private static final double H2 = H  * H;
    private static final double H6 = H2 * H2 * H2;
    private static final double H9 = H6 * H2 * H;

    /**
     * Poly6 kernel normalisation factor:  315 / (64π h⁹).
     *
     * <h3>Why Poly6 for density?</h3>
     * The full kernel is:
     * <pre>
     *   W_poly6(r, h) = (315 / 64πh⁹) · (h² − r²)³   for r ≤ h
     *                 = 0                               otherwise
     * </pre>
     * The {@code (h² − r²)³} form uses only {@code r²} — no square root needed
     * — making it very cheap to evaluate.  It is smooth everywhere including
     * the centre ({@code r=0}), which is fine for density estimation.
     *
     * <p>Its gradient, however, goes to zero at {@code r=0}, causing particles
     * to clump.  That is why we switch to the Spiky kernel for pressure.
     */
    private static final double POLY6_COEFF = 315.0 / (64.0 * Math.PI * H9);

    /**
     * Spiky kernel gradient magnitude factor:  −45 / (π h⁶).
     *
     * <h3>Why Spiky for pressure?</h3>
     * The gradient of the Spiky kernel:
     * <pre>
     *   ∇W_spiky(r, h) = (−45 / πh⁶) · (h − r)² · r̂   for r &lt; h
     * </pre>
     * Unlike Poly6, the Spiky gradient is non-zero all the way down to
     * {@code r → 0}, so particles that come very close to each other still
     * feel a strong repulsion and don't collapse into a singularity.
     *
     * <p>The constant is negative because the gradient points toward the
     * neighbour (decreasing r), giving a repulsive force when positive pressure
     * acts along the outward direction.
     */
    private static final double SPIKY_GRAD_COEFF = -45.0 / (Math.PI * H6);

    /**
     * Viscosity kernel Laplacian factor:  45 / (π h⁶).
     *
     * <h3>Why a separate kernel for viscosity?</h3>
     * The Laplacian of the Poly6 kernel can be negative near {@code r=0},
     * which would produce a viscosity force that accelerates particles —
     * physically wrong and numerically destabilising.
     *
     * <p>The Viscosity kernel:
     * <pre>
     *   ∇²W_visc(r, h) = (45 / πh⁶) · (h − r)   for r ≤ h
     * </pre>
     * is always non-negative, guaranteeing that viscosity only ever damps
     * velocity differences (Newton's law of viscosity: stress ∝ strain rate).
     */
    private static final double VISC_LAP_COEFF = 45.0 / (Math.PI * H6);

    // =========================================================================
    // Simulator state
    // =========================================================================

    /** All particles managed by this simulator, active or frozen. */
    private final List<FluidParticle> particles = new ArrayList<>();

    /** Spatial index; rebuilt from scratch at the start of every step. */
    private final SpatialHashGrid grid = new SpatialHashGrid(SMOOTHING_RADIUS);

    /**
     * Leftover simulated time carried from the previous {@link #update} call.
     * Allows fixed-step integration independent of variable server tick timing.
     */
    private double timeAccumulator = 0.0;

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Add a particle to the simulation.
     *
     * @param p particle to add; must not be {@code null}
     */
    public void addParticle(FluidParticle p) {
        particles.add(p);
    }

    /**
     * Remove a particle from the simulation.
     *
     * @param p particle to remove
     */
    public void removeParticle(FluidParticle p) {
        particles.remove(p);
    }

    /** Unmodifiable view of all particles (active + frozen). */
    public List<FluidParticle> getParticles() {
        return Collections.unmodifiableList(particles);
    }

    /** Number of particles currently tracked (active + frozen). */
    public int particleCount() {
        return particles.size();
    }

    /**
     * Advance the simulation by {@code deltaTime} seconds.
     *
     * <p>Should be called once per server tick from
     * {@link com.hydrophysics.fluid.FluidSystem}.  The internal accumulator
     * fires as many fixed-step SPH steps as {@code deltaTime} permits, then
     * stores any remainder for the next call.
     *
     * @param deltaTime seconds elapsed since the last call (typically 0.05 s
     *                  at 20 TPS, but may vary under server load)
     * @param world     server world — used for chunk-loaded checks only
     * @param players   players whose surrounding area is considered active;
     *                  may be a {@code List<ServerPlayerEntity>}
     */
    public void update(double deltaTime, World world, List<? extends PlayerEntity> players) {
        timeAccumulator += deltaTime;

        // Fire as many fixed sub-steps as time allows.
        // Cap at 3 sub-steps per game tick to prevent a "spiral of death"
        // when the server is under load and deltaTime spikes.
        int stepsBudget = 3;
        while (timeAccumulator >= FIXED_DT_SECONDS && stepsBudget-- > 0) {
            step(world, players);
            timeAccumulator -= FIXED_DT_SECONDS;
        }

        // If we burned through the budget, drain the accumulator so stale
        // time doesn't pile up.
        if (stepsBudget <= 0) {
            timeAccumulator = 0.0;
        }
    }

    // =========================================================================
    // Simulation step
    // =========================================================================

    /**
     * One complete SPH step:
     * <ol>
     *   <li>Select active particles (near player, in loaded chunks).</li>
     *   <li>Rebuild the spatial hash grid.</li>
     *   <li>Query and cache each particle's neighbour list once (shared across
     *       density and force passes to halve the number of grid queries).</li>
     *   <li>Density + pressure pass.</li>
     *   <li>Force accumulation pass.</li>
     *   <li>Symplectic Euler integration pass.</li>
     * </ol>
     */
    private void step(World world, List<? extends PlayerEntity> players) {
        List<FluidParticle> active = collectActiveParticles(world, players);
        if (active.isEmpty()) return;

        // 1. Rebuild spatial index with the current particle positions.
        grid.clear();
        for (FluidParticle p : active) {
            grid.insert(p);
        }

        // 2. Query neighbours once per particle and cache the result.
        //    Both the density pass and the force pass iterate the same lists,
        //    cutting the number of spatial hash queries in half.
        List<List<FluidParticle>> neighbourCache = new ArrayList<>(active.size());
        for (FluidParticle p : active) {
            neighbourCache.add(grid.queryRadius(p.position, SMOOTHING_RADIUS));
        }

        // 3. Density + pressure (writes p.density and p.pressure).
        for (int i = 0; i < active.size(); i++) {
            computeDensityAndPressure(active.get(i), neighbourCache.get(i));
        }

        // 4. Force accumulation (writes p.acceleration).
        for (int i = 0; i < active.size(); i++) {
            accumulateForces(active.get(i), neighbourCache.get(i));
        }

        // 5. Symplectic Euler integration (updates p.velocity then p.position).
        for (FluidParticle p : active) {
            integrate(p);
        }
    }

    // =========================================================================
    // Pass 1 — density and pressure
    // =========================================================================

    /**
     * Compute the SPH density estimate at particle {@code pi} using the
     * <b>Poly6 kernel</b>:
     *
     * <pre>
     *   ρᵢ = Σⱼ mⱼ · W_poly6(|rᵢ − rⱼ|, h)
     *
     *   W_poly6(r, h) = POLY6_COEFF · (h² − r²)³   for r ≤ h
     *                 = 0                            otherwise
     * </pre>
     *
     * Using {@code r²} avoids computing a square root; the kernel is evaluated
     * in squared distance space throughout the inner loop.
     *
     * <p>Then derive pressure from the ideal-gas equation of state:
     * <pre>
     *   pᵢ = k · (ρᵢ − ρ₀)
     * </pre>
     * Positive pressure means the fluid is denser than rest (compressed →
     * repulsion); negative pressure means it is below rest density (rarefied →
     * mild cohesion at free surfaces).
     *
     * @param pi        particle whose density/pressure to compute
     * @param neighbours cached neighbour list from the spatial grid (includes pi itself)
     */
    private void computeDensityAndPressure(FluidParticle pi, List<FluidParticle> neighbours) {
        double density = 0.0;

        for (FluidParticle pj : neighbours) {
            double r2 = pi.position.squaredDistanceTo(pj.position);

            // Only interact within the smoothing radius; r² < h² is cheaper than r < h.
            if (r2 >= H2) continue;

            // Poly6 kernel value:  COEFF · (h² − r²)³
            // We stay in squared-distance space — no sqrt needed here.
            double diff = H2 - r2;
            density += pj.mass * POLY6_COEFF * diff * diff * diff;
        }

        // Clamp to a small positive floor to prevent division-by-zero in the
        // force pass.  1e-4 kg/m³ is negligible relative to rest density 1000.
        pi.density  = (float) Math.max(density, 1e-4);

        // Ideal-gas law: p = k · (ρ − ρ₀)
        pi.pressure = GAS_CONSTANT * (pi.density - REST_DENSITY);
    }

    // =========================================================================
    // Pass 2 — force accumulation
    // =========================================================================

    /**
     * Accumulate all forces acting on particle {@code pi} and store the
     * resulting acceleration in {@code pi.acceleration}.
     *
     * <h3>Pressure force — Spiky kernel gradient</h3>
     *
     * The gradient of the Spiky kernel in the direction from neighbour j to i:
     * <pre>
     *   ∇W_spiky(r, h) = SPIKY_GRAD_COEFF · (h − r)² · r̂
     * </pre>
     *
     * Pressure force on particle i (symmetric formulation that satisfies
     * Newton's third law approximately):
     * <pre>
     *   fᵢ_pressure = −Σⱼ mⱼ · (pᵢ + pⱼ) / (2ρⱼ) · ∇W_spiky(rᵢ − rⱼ, h)
     * </pre>
     *
     * The outer negative sign and the negative {@code SPIKY_GRAD_COEFF} cancel
     * out: when the averaged pressure {@code (pᵢ + pⱼ)/2} is positive (fluid
     * compressed), the net force pushes particle i <em>away</em> from j
     * (repulsion) — physically correct.
     *
     * <h3>Viscosity force — Viscosity kernel Laplacian</h3>
     *
     * <pre>
     *   ∇²W_visc(r, h) = VISC_LAP_COEFF · (h − r)
     *
     *   fᵢ_viscosity = μ · Σⱼ mⱼ · (vⱼ − vᵢ) / ρⱼ · ∇²W_visc(r, h)
     * </pre>
     *
     * This smooths velocity differences between neighbouring particles.
     * Because {@code ∇²W_visc ≥ 0} for all {@code r ≤ h}, the force always
     * pulls {@code vᵢ} toward the local average velocity (damping, never
     * amplification).
     *
     * <h3>Gravity</h3>
     *
     * A constant body acceleration {@code g = (0, −9.81, 0)} m/s² applied
     * equally to all particles.  Because gravity produces the same acceleration
     * regardless of density, we add it directly to the final acceleration
     * rather than multiplying by density and dividing again.
     *
     * <h3>Acceleration</h3>
     *
     * Newton's second law in SPH form:
     * <pre>
     *   aᵢ = (fᵢ_pressure + fᵢ_viscosity) / ρᵢ + g
     * </pre>
     *
     * @param pi        particle to update
     * @param neighbours cached neighbour list (must NOT include pi itself in
     *                   the skip check; we guard with a reference comparison)
     */
    private void accumulateForces(FluidParticle pi, List<FluidParticle> neighbours) {
        // Accumulate force components as doubles to avoid float precision loss
        // over many additions.
        double fpx = 0.0, fpy = 0.0, fpz = 0.0;  // pressure force
        double fvx = 0.0, fvy = 0.0, fvz = 0.0;  // viscosity force

        for (FluidParticle pj : neighbours) {
            // Skip self-interaction — a particle exerts no force on itself.
            if (pj == pi) continue;

            double dx = pi.position.x - pj.position.x;
            double dy = pi.position.y - pj.position.y;
            double dz = pi.position.z - pj.position.z;
            double r  = Math.sqrt(dx * dx + dy * dy + dz * dz);

            // Skip particles outside the smoothing radius or coincident ones
            // (r == 0 would cause division-by-zero when computing the unit vector).
            if (r <= 0.0 || r >= H) continue;

            // Unit vector r̂ pointing from pj to pi (outward direction).
            double invR = 1.0 / r;
            double nx = dx * invR;
            double ny = dy * invR;
            double nz = dz * invR;

            // -----------------------------------------------------------------
            // Pressure force  (Spiky kernel gradient)
            //
            //   fᵢ_pressure += −mⱼ · (pᵢ + pⱼ)/(2ρⱼ) · ∇W_spiky
            //
            // ∇W_spiky = SPIKY_GRAD_COEFF · (h−r)² · r̂
            // SPIKY_GRAD_COEFF < 0, but the leading minus sign in the formula
            // makes the net contribution positive (repulsive) when pressure > 0.
            // -----------------------------------------------------------------
            double spikyGrad = SPIKY_GRAD_COEFF * (H - r) * (H - r);  // ≤ 0
            // net factor along r̂:  −mⱼ · pressure_avg · spikyGrad
            // Two negatives (formula minus + negative coeff) → positive for p > 0.
            double pressureFactor = -pj.mass
                    * ((pi.pressure + pj.pressure) / (2.0 * pj.density))
                    * spikyGrad;
            fpx += pressureFactor * nx;
            fpy += pressureFactor * ny;
            fpz += pressureFactor * nz;

            // -----------------------------------------------------------------
            // Viscosity force  (Viscosity kernel Laplacian)
            //
            //   fᵢ_viscosity += mⱼ · (vⱼ − vᵢ)/ρⱼ · ∇²W_visc
            //
            // ∇²W_visc = VISC_LAP_COEFF · (h − r) ≥ 0 for r ≤ h.
            // The (vⱼ − vᵢ) term means the force pushes vᵢ toward vⱼ
            // (velocity smoothing / momentum diffusion).
            // -----------------------------------------------------------------
            double viscLap    = VISC_LAP_COEFF * (H - r);   // ≥ 0
            double viscFactor = pj.mass * viscLap / pj.density;
            fvx += (pj.velocity.x - pi.velocity.x) * viscFactor;
            fvy += (pj.velocity.y - pi.velocity.y) * viscFactor;
            fvz += (pj.velocity.z - pi.velocity.z) * viscFactor;
        }

        // Scale viscosity forces by μ.
        fvx *= VISCOSITY;
        fvy *= VISCOSITY;
        fvz *= VISCOSITY;

        // -----------------------------------------------------------------
        // Gravity
        //
        // f_gravity = ρᵢ · g  →  a_gravity = g  (density cancels with 1/ρ)
        // Add directly to the final acceleration; avoids a multiply/divide pair.
        // -----------------------------------------------------------------

        // Acceleration  a = (f_pressure + f_viscosity) / ρᵢ + g
        double invDensity = 1.0 / pi.density;
        pi.acceleration = new Vec3d(
                (fpx + fvx) * invDensity,
                (fpy + fvy) * invDensity + GRAVITY,   // +GRAVITY because a_grav = g
                (fpz + fvz) * invDensity
        );
    }

    // =========================================================================
    // Pass 3 — symplectic Euler integration
    // =========================================================================

    /**
     * Integrate velocity and position using symplectic (semi-implicit) Euler:
     *
     * <pre>
     *   vᵢ(t + dt) = vᵢ(t) + aᵢ · dt          ← update velocity first
     *   xᵢ(t + dt) = xᵢ(t) + vᵢ(t + dt) · dt  ← use the NEW velocity
     * </pre>
     *
     * <p>Symplectic Euler is preferred over explicit Euler for SPH because it
     * is a <em>symplectic integrator</em>: it conserves a slightly modified
     * energy (shadow Hamiltonian) rather than letting energy drift unboundedly.
     * This gives better long-term stability without the expense of Runge-Kutta.
     *
     * @param p particle to integrate
     */
    private void integrate(FluidParticle p) {
        // Velocity update (explicit step).
        Vec3d newVelocity = p.velocity.add(p.acceleration.multiply(FIXED_DT_SECONDS));
        p.velocity = newVelocity;

        // Position update using the already-updated velocity (the "symplectic" part).
        p.position = p.position.add(newVelocity.multiply(FIXED_DT_SECONDS));
    }

    // =========================================================================
    // Active particle selection
    // =========================================================================

    /**
     * Collect all particles that are:
     * <ol>
     *   <li>Within {@value ACTIVE_RADIUS} blocks (Manhattan-approximate,
     *       Euclidean-squared check) of at least one online player, <em>and</em></li>
     *   <li>Located in a currently-loaded chunk.</li>
     * </ol>
     *
     * <p>Particles that fail either criterion are left frozen — their position
     * and velocity are unchanged this step but their memory cost is negligible.
     * This avoids simulating water that nobody can see and prevents loading new
     * chunks as a side effect of physics queries.
     *
     * @param world   the server world
     * @param players online players whose surroundings are active
     * @return mutable list of active particles for this step; may be empty
     */
    private List<FluidParticle> collectActiveParticles(
            World world, List<? extends PlayerEntity> players) {

        if (players.isEmpty()) return List.of();

        double rangeSquared = (double) ACTIVE_RADIUS * ACTIVE_RADIUS;
        List<FluidParticle> active = new ArrayList<>();

        for (FluidParticle p : particles) {
            // Chunk-loaded check: convert to chunk coordinates (>> 4 = divide by 16).
            int chunkX = (int) Math.floor(p.position.x) >> 4;
            int chunkZ = (int) Math.floor(p.position.z) >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)) continue;

            // Near at least one player?
            for (PlayerEntity player : players) {
                if (p.position.squaredDistanceTo(player.getPos()) <= rangeSquared) {
                    active.add(p);
                    break;  // no need to check remaining players
                }
            }
        }

        return active;
    }

    // =========================================================================
    // Diagnostics
    // =========================================================================

    /**
     * Log a one-line status summary.  Call occasionally (e.g. every 100 ticks)
     * to monitor simulation health without flooding logs.
     */
    public void logDiagnostics() {
        HydroPhysics.LOGGER.debug(
                "[HydroPhysics] FluidSimulator: {} total particles | {} grid cells | accumulator={:.4f}s",
                particles.size(), grid.cellCount(), timeAccumulator);
    }
}
