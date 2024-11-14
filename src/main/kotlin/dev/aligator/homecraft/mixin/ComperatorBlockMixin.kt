package dev.aligator.homecraft.mixin


import net.minecraft.block.BlockState
import net.minecraft.block.ComparatorBlock
import net.minecraft.entity.decoration.ItemFrameEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.World
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(ComparatorBlock::class)
abstract class ComparatorBlockMixin {

    @Inject(method = ["getPower"], at = [At("HEAD")], cancellable = true)
    protected fun getPower(world: World, pos: BlockPos, state: BlockState, cir: CallbackInfoReturnable<Int>) {
//        var power = superGetPower(world, pos, state) // Anruf der Methode des übergeordneten Blocks
//        val direction = state.get(ComparatorBlock.FACING) as Direction // Richtung aus BlockState holen
//        var blockPos = pos.offset(direction)
//        var blockState = world.getBlockState(blockPos)
//
//        if (blockState.hasComparatorOutput()) {
//            power = blockState.getComparatorOutput(world, blockPos)
//        }
//
//        cir.returnValue = power
    }
}