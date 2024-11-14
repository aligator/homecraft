package dev.aligator.homecraft.mixin

import dev.aligator.homecraft.ComperatorValueCallback
import dev.aligator.homecraft.ExplosionCallback
import net.minecraft.block.AbstractBlock
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import kotlin.random.Random

@Mixin(AbstractBlock::class)
abstract class BlockMixin {

    @Inject(method = ["getComparatorOutput(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)I"], at = [At("HEAD")], cancellable = true)
    fun getComparatorOutput(state: BlockState, world: World, pos: BlockPos, cir: CallbackInfoReturnable<Int>) {
        // Hier können Sie Ihre eigene Logik einfügen, bevor die ursprüngliche Methode ausgeführt wird.
        println("Get comparator output for state: $state at position: $pos")
        val block = (this as AbstractBlock)

        if (block === Blocks.BLACK_WOOL) {
            val result = ComperatorValueCallback.EVENT.invoker().onGetComperatorValue(state, world, pos)
            println("block $block. $result")
            cir.returnValue = result
        }
    }

    @Inject(method = ["hasComparatorOutput"], at = [At("HEAD")], cancellable = true)
    fun hasComparatorOutput(cir: CallbackInfoReturnable<Boolean>) {
        // Hier können Sie Ihre eigene Logik einfügen, bevor die ursprüngliche Methode ausgeführt wird.
        println("Check if block has comparator output")
        val block = (this as AbstractBlock)
        if (block === Blocks.BLACK_WOOL) {
            cir.returnValue = true
        }
    }
}
