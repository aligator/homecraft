package dev.aligator.homecraft

import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import net.minecraft.block.BlockState
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraft.world.explosion.Explosion


fun interface UpdateNeighborsCallback {
    fun onUpdateNeighbors(world: ServerWorld, pos: BlockPos): Boolean

    companion object {
        val EVENT: Event<UpdateNeighborsCallback> = EventFactory.createArrayBacked(UpdateNeighborsCallback::class.java) { listeners ->
            UpdateNeighborsCallback { world, pos,  ->
                for (listener in listeners) {
                    if (!listener.onUpdateNeighbors(world, pos)) {
                        return@UpdateNeighborsCallback false
                    }
                }
                true
            }
        }
    }
}


fun interface ExplosionCallback {
    fun onExplosion(world: World, explosion: Explosion): Boolean

    companion object {
        val EVENT: Event<ExplosionCallback> = EventFactory.createArrayBacked(ExplosionCallback::class.java) { listeners ->
            ExplosionCallback { world, explosion ->
                for (listener in listeners) {
                    if (!listener.onExplosion(world, explosion)) {
                        return@ExplosionCallback false
                    }
                }
                true
            }
        }
    }
}


fun interface ComperatorValueCallback {
    fun onGetComperatorValue(state: BlockState, world: World, pos: BlockPos): Int

    companion object {
        val EVENT: Event<ComperatorValueCallback> = EventFactory.createArrayBacked(ComperatorValueCallback::class.java) { listeners ->
            ComperatorValueCallback { state, world, pos ->
                for (listener in listeners) {
                    return@ComperatorValueCallback listener.onGetComperatorValue(state, world, pos)
                }

                return@ComperatorValueCallback 0
            }
        }
    }
}