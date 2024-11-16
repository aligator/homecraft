package dev.aligator.homecraft

import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.SignBlockEntity
import net.minecraft.block.entity.SignText
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.DyeColor
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class ControlBlock(world: World?, pos: BlockPos, entity: BlockEntity?, haEntityId: String = "") {
    constructor(world: World?, pos: BlockPos, haEntityId: String = "") : this(world, pos, world?.getBlockEntity(pos), haEntityId)

    private val world: World? = world

    val pos: BlockPos = pos

    private var haEntityId: String = ""

    init {
        if (world != null && !world.isClient) {
            if (entity is SignBlockEntity) {
                this.haEntityId = link(entity as SignBlockEntity, haEntityId)
            }
        }
    }

    fun getWorld(): ServerWorld? {
        if (world == null || world.isClient) {
            return null
        }
        return world as ServerWorld
    }

    fun isValid(): Boolean {
        return haEntityId.isNotEmpty()
    }

    fun getHAEnttyId(): String {
        return haEntityId
    }

    companion object {
        private fun link(entity: SignBlockEntity, haEntityId: String): String {

            // If a new entityId is passed - set up the sign instead of validating it
            if (haEntityId.isNotEmpty()) {
                val text = SignText(
                    arrayOf(
                        Text.literal("[homecraft]"),
                        Text.literal(haEntityId),
                        Text.literal(""),
                        Text.literal(""),
                    ),
                    arrayOf(
                        Text.literal(""),
                        Text.literal(""),
                        Text.literal(""),
                        Text.literal(""),
                    ), DyeColor.BLACK, false)

                entity.setText(text, false)
                // Make readonly
                entity.setWaxed(true)
                entity.markDirty()
            }

            var text = entity.getText(false)
            if (text.getMessage(0, false).string != "[homecraft]") {
                return ""
            }

            return text.getMessage(1, false).string
        }
    }
}