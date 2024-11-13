package dev.aligator.homecraft

import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.block.*
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.player.BlockBreakingInfo
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.state.property.Properties
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.math.Direction
import java.util.logging.Logger

import net.minecraft.world.explosion.Explosion
import net.minecraft.world.World
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import kotlin.math.exp


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


class BlockUpdateHandler(
    private val ha: HomeAssistant, // Replace with your custom Home Assistant handler
//    private val mode: String,
    private val links: LinkStore // Replace with your custom LinkStore
) {
    private val logger: Logger = Logger.getLogger("Home Assistant Block Update Handler")

    init {
        // Register all event listeners
        registerEntityExplosionEvent()
        registerBlockExplodeEvent()
        registerBlockBreakEvent()
        registerPlayerInteractEvent()
     //   registerRedstoneEvent()
    }

    /**
     * Handles explosions caused by entities like Creeper, TNT, etc.
     */
    private fun registerEntityExplosionEvent() {
        ExplosionCallback.EVENT.register(ExplosionCallback { world, explosion ->
            if (world.isClient) {
                return@ExplosionCallback false
            }


            logger.info(explosion.toString())
            val entityType = explosion.entity?.type ?: return@ExplosionCallback false
            val affectedBlocks = explosion.affectedBlocks

            // Assuming `links` is an instance of your custom class that handles block removal
            links.removeMatchingBlocks(world as ServerWorld, affectedBlocks, "${entityType.name} blew it up")

            true
        })
    }

    /**
     * Handles unknown explosions that affect blocks directly.
     */
    private fun registerBlockExplodeEvent() {
        // Fabric doesn't have a direct equivalent, so we handle explosions indirectly, like above.
    }

    /**
     * Detects a block being broken by a player and removes it from Home Assistant links.
     */
    private fun registerBlockBreakEvent() {
        PlayerBlockBreakEvents.BEFORE.register(PlayerBlockBreakEvents.Before { world, player, pos, state, _ ->
            if (world.isClient) {
                return@Before false
            }
            val haEntity = links.getEntity(world as ServerWorld, pos)

            haEntity?.let {
                // Remove the block from links
                links.remove(BlockLocation(world, pos))
                player.sendMessage(Text.of("‘$haEntity’ unlinked from ${state.block.name.string}"), false)

                // Check and remove any attached blocks
                Direction.values().forEach { direction ->
                    if (isAttached(world.getBlockState(pos), direction)) {
                        val attachedPos = pos.offset(direction)
                        val attachedEntity = links.getEntity(world, attachedPos)
                        attachedEntity?.let {
                            links.remove(BlockLocation(world, attachedPos))
                            player.sendMessage(
                                Text.of("‘$attachedEntity’ unlinked from ${world.getBlockState(attachedPos).block.name.string}"),
                                false
                            )
                        }
                    }
                }
            }
            true
        })
    }

    /**
     * Detects player interaction events in "interact" mode.
     */
    private fun registerPlayerInteractEvent() {
        UseBlockCallback.EVENT.register(UseBlockCallback { player, world, hand, hitResult ->
//            if (mode != "interact" || !player.hasPermission("use.permission")) return@UseBlockCallback ActionResult.PASS
            if (world.isClient) {
                return@UseBlockCallback ActionResult.PASS
            }

            val blockPos = hitResult.blockPos
            val blockState = world.getBlockState(blockPos)
            val haEntity = links.getEntity(world as ServerWorld, blockPos)

            if (haEntity == null) {
                return@UseBlockCallback ActionResult.PASS
            }
            haEntity?.let {
                if (haEntity.contains("sensor.")) return@UseBlockCallback ActionResult.FAIL
                if (blockState.contains(Properties.POWERED)) {
                    val isPowered = world.isReceivingRedstonePower(blockPos)
                    ha.setState(haEntity, !isPowered)
                    logger.info("${player.name.string} set Home Assistant/$haEntity = ${if (!isPowered) "ON" else "OFF"}")
                } else if (blockState.block is DoorBlock) {
                    val isOpen = (blockState.get(DoorBlock.OPEN) == true)
                    ha.setState(haEntity, !isOpen)
                    logger.info("${player.name.string} set Home Assistant/$haEntity = ${if (!isOpen) "OPEN" else "CLOSED"}")
                }
            }
            ActionResult.SUCCESS
        })
    }

    /**
     * Detects redstone updates in "redstone" mode.
     *//*
    private fun registerRedstoneEvent() {
        ServerTickEvents.END_WORLD_TICK.register { world: ServerWorld ->
            if (mode != "redstone") return@register

            world.players.forEach { player ->
                world.blockEntities.forEach { blockEntity ->
                    val pos = blockEntity.pos
                    val linkType = links.getLinkType(pos)
                    if (linkType == LinkType.INPUT || linkType == LinkType.SYNC) {
                        val haEntity = links.getEntity(pos)
                        haEntity?.let {
                            val isPowered = world.isReceivingRedstonePower(pos)
                            ha.setState(haEntity, isPowered)
                        }
                    }
                }
            }
        }*/
   // }

    /**
     * Checks if there is an attached block (like a button or lever) at a specific face.
     */
    private fun isAttached(state: BlockState, direction: Direction): Boolean {
        return when (state.block) {
            is LeverBlock -> direction == Direction.UP
            is ButtonBlock -> direction == Direction.DOWN
            is WallSignBlock -> direction in listOf(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)
            else -> false
        }
    }
}
