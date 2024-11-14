package dev.aligator.homecraft.mixin

import dev.aligator.homecraft.CONTROL_BLOCK
import dev.aligator.homecraft.UpdateNeighborsCallback
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import net.minecraft.block.AbstractBlock
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.DoorBlock
import net.minecraft.entity.Entity
import net.minecraft.entity.damage.DamageSource
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.World
import net.minecraft.world.explosion.Explosion
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.At



@Mixin(ServerWorld::class)
abstract class ServerWorldMixin {
    @Inject(
        method = ["updateNeighborsAlways(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/Block;)V"],
        at = [At("HEAD")]
    )
    private fun onUpdateNeighborsAlways(pos: BlockPos, sourceBlock: Block, cir: CallbackInfo) {
        val world = (this as ServerWorld)

        if (world.getBlockState(pos).block === CONTROL_BLOCK) {
            val result = UpdateNeighborsCallback.EVENT.invoker().onUpdateNeighbors(world, pos)
            if (!result) {
                cir.cancel()
            }
        }
    }

    @Inject(
        method = ["updateNeighborsExcept(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/Block;Lnet/minecraft/util/math/Direction;)V"],
        at = [At("HEAD")]
    )
    private fun onUpdateNeighborsExcept(pos: BlockPos, sourceBlock: Block, direction: Direction?, cir: CallbackInfo) {
        val world = (this as ServerWorld)
        if (world.getBlockState(pos).block === CONTROL_BLOCK) {
            val result = UpdateNeighborsCallback.EVENT.invoker().onUpdateNeighbors(world, pos)
            if (!result) {
                cir.cancel()
            }
        }
    }

    @Inject(
        method = ["updateNeighbor(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/Block;Lnet/minecraft/util/math/BlockPos;)V"],
        at = [At("HEAD")]
    )
    private fun onUpdateNeighbor1(pos: BlockPos, sourceBlock: Block, sourcePos: BlockPos, cir: CallbackInfo) {
        val world = (this as ServerWorld)
        if (world.getBlockState(pos).block === CONTROL_BLOCK) {
            val result = UpdateNeighborsCallback.EVENT.invoker().onUpdateNeighbors(world, pos)
            if (!result) {
                cir.cancel()
            }
        }
    }

    @Inject(
        method = ["updateNeighbor(Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/Block;Lnet/minecraft/util/math/BlockPos;Z)V"],
        at = [At("HEAD")]
    )
    private fun onUpdateNeighbor2(state: BlockState, pos: BlockPos, sourceBlock: Block, sourcePos: BlockPos, notify: Boolean, cir: CallbackInfo) {
        val world = (this as ServerWorld)
        if (world.getBlockState(pos).block === CONTROL_BLOCK) {
            val result = UpdateNeighborsCallback.EVENT.invoker().onUpdateNeighbors(world, pos)
            if (!result) {
                cir.cancel()
            }
        }
    }

}