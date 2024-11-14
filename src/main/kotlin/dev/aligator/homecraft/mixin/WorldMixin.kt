package dev.aligator.homecraft.mixin

import dev.aligator.homecraft.ExplosionCallback
import net.minecraft.entity.Entity
import net.minecraft.entity.damage.DamageSource
import net.minecraft.particle.ParticleEffect
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.sound.SoundEvent
import net.minecraft.world.World
import net.minecraft.world.explosion.Explosion
import net.minecraft.world.explosion.ExplosionBehavior
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable


@Mixin(World::class)
abstract class WorldMixin {

    @Inject(
        method = ["createExplosion(Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/damage/DamageSource;Lnet/minecraft/world/explosion/ExplosionBehavior;DDDFZLnet/minecraft/world/World\$ExplosionSourceType;ZLnet/minecraft/particle/ParticleEffect;Lnet/minecraft/particle/ParticleEffect;Lnet/minecraft/registry/entry/RegistryEntry;)Lnet/minecraft/world/explosion/Explosion;"],
        at = [At("RETURN")],
    )
    private fun onCreateExplosion(
        entity: Entity?,
        damageSource: DamageSource?,
        behavior: ExplosionBehavior?,
        x: Double,
        y: Double,
        z: Double,
        power: Float,
        createFire: Boolean,
        explosionSourceType: World.ExplosionSourceType,
        particles: Boolean,
        particle: ParticleEffect?,
        emitterParticle: ParticleEffect?,
        soundEvent: RegistryEntry<SoundEvent>?,
        cir: CallbackInfoReturnable<Explosion>
    ) {
        val originalExplosion = cir.returnValue

        val world = (this as World)
        val result = ExplosionCallback.EVENT.invoker().onExplosion(world, originalExplosion)
        if (!result) {
            cir.cancel()
        }
    }

}