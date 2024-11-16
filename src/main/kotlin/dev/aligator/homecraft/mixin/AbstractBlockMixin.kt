package dev.aligator.homecraft.mixin

import dev.aligator.homecraft.ComperatorValueCallback
import dev.aligator.homecraft.isControlBlock
import net.minecraft.block.AbstractBlock
import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.BlockView
import net.minecraft.world.World
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(AbstractBlock::class)
abstract class AbstractBlockMixin {

    // TODO:Emitting power makes only sense if I use a special block only for output.
    //       Otherwise it always interacts with itself.
    //
    //       For now only use comperators. This decouples reading and writing the value nicely.
    @Inject(method = ["getStrongRedstonePower"], at = [At("HEAD")], cancellable = true)
    fun getStrongRedstonePower(state: BlockState, world: BlockView, pos: BlockPos, direction: Direction,  cir: CallbackInfoReturnable<Int>) {
//        val block = (this as AbstractBlock)
//        if (isControlBlock(block)) {
//            val world = world.getBlockEntity(pos)?.world ?: return
//
//            val result = ComperatorValueCallback.EVENT.invoker().onGetComperatorValue(state, world, pos)
//            if (result > 0) {
//                cir.returnValue = 15
//            } else {
//                cir.returnValue = 0
//            }
//        }
    }

    @Inject(method = ["getWeakRedstonePower"], at = [At("HEAD")], cancellable = true)
    fun getWeakRedstonePower(state: BlockState, world: BlockView, pos: BlockPos, direction: Direction,  cir: CallbackInfoReturnable<Int>) {
//        val block = (this as AbstractBlock)
//        if (isControlBlock(block)) {
//            val world = world.getBlockEntity(pos)?.world ?: return
//
//            val result = ComperatorValueCallback.EVENT.invoker().onGetComperatorValue(state, world, pos)
//            cir.returnValue = result
//        }
    }

  @Inject(
          method =
                  [
                          "getComparatorOutput(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)I"],
          at = [At("HEAD")],
          cancellable = true
  )
  fun getComparatorOutput(
          state: BlockState,
          world: World,
          pos: BlockPos,
          cir: CallbackInfoReturnable<Int>
  ) {
    val block = (this as AbstractBlock)
    if (isControlBlock(block)) {
      val result = ComperatorValueCallback.EVENT.invoker().onGetComperatorValue(state, world, pos)
      cir.returnValue = result
    }
  }

  @Inject(method = ["hasComparatorOutput"], at = [At("HEAD")], cancellable = true)
  fun hasComparatorOutput(cir: CallbackInfoReturnable<Boolean>) {
    val block = (this as AbstractBlock)
    if (isControlBlock(block)) {
      cir.returnValue = true
    }
  }
}
