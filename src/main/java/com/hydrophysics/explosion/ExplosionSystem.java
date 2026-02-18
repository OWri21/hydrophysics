package com.hydrophysics.explosion;

/**
 * Realistic explosion system for HydroPhysics.
 *
 * <p>Extends and overrides vanilla explosion behaviour via Mixins so that:
 * <ul>
 *   <li>Explosions <em>underwater</em> have a reduced air blast radius but
 *       produce a high-pressure shockwave through the water column.</li>
 *   <li>Water volumes are <em>displaced</em> rather than destroyed – water
 *       flows back into the cavity once pressure equalises.</li>
 *   <li>Shockwaves transmit damage and knockback to entities through water at
 *       physically plausible attenuation rates.</li>
 *   <li>Surface explosions create outward splash waves handled by
 *       {@link com.hydrophysics.fluid.FluidSystem}.</li>
 * </ul>
 *
 * <p>The system intercepts the vanilla {@code Explosion} class via a Mixin
 * injected at the start of {@code Explosion#affectWorld} and branches based on
 * whether the explosion origin is inside a fluid block.
 */
public final class ExplosionSystem {

    /**
     * Entry point called from the explosion Mixin.
     *
     * @param originX world X of the explosion centre
     * @param originY world Y of the explosion centre
     * @param originZ world Z of the explosion centre
     * @param power   vanilla explosion power value
     * @param isUnderwater {@code true} when the origin block is a fluid
     */
    public void handleExplosion(double originX, double originY, double originZ,
                                 float power, boolean isUnderwater) {
        // TODO: implement custom explosion handling.
    }
}
