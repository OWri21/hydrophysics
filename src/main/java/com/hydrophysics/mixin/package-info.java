/**
 * Mixins for HydroPhysics.
 *
 * <p>All classes in this package must be listed in
 * {@code src/main/resources/hydrophysics.mixins.json}.
 *
 * <p>Planned mixin targets:
 * <ul>
 *   <li>{@code ExplosionMixin}    – intercept {@code Explosion#affectWorld}</li>
 *   <li>{@code FluidBlockMixin}   – extend vanilla flow scheduling</li>
 *   <li>{@code EntityMixin}       – apply pressure/current forces each tick</li>
 * </ul>
 */
package com.hydrophysics.mixin;
