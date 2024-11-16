package dev.aligator.homecraft

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import java.util.logging.Logger


class BlockUpdateHandler(
    private val ha: HomeAssistant, // Replace with your custom Home Assistant handler
    private val links: LinkStore // Replace with your custom LinkStore
) {
    private val logger: Logger = Logger.getLogger("Home Assistant Block Update Handler")

    init {
        // Register all event listeners
        registerComparatorEvent()
        registerBlockExplodeEvent()
        registerBlockBreakEvent()
        registerRedstoneEvent()
    }


    /**
     * Detects redstone updates.
     */
    private fun registerRedstoneEvent() {
        UpdateNeighborsCallback.EVENT.register(UpdateNeighborsCallback {world, pos ->

            // Get the linked entity if available
            val haEntity = links.getEntity(world, pos)
            if (haEntity != null) {
                val isPowered = world.isReceivingRedstonePower(pos)
                ha.setState(haEntity, isPowered)
            }

            true
        })
    }

    /**
     * Send a specific value to the comparator for a specific link.
     */
    private fun registerComparatorEvent() {
        ComperatorValueCallback.EVENT.register(ComperatorValueCallback { state, world, pos ->
            if (world.isClient) {
                return@ComperatorValueCallback 0
            }
            var world = world as ServerWorld

            val entity = links.getEntity(world, pos)
            if (ha.lastState(entity) == "on") {
                return@ComperatorValueCallback 15
            } else
            {
                return@ComperatorValueCallback 0
            }

        })
    }

    /**
     * Handles explosions caused by entities like Creeper, TNT, etc.
     */
    private fun registerBlockExplodeEvent() {
        // TODO: maybe use a mixin more directly to create a block remove event, to catch all possibilities (e.g. endermen, tnt, ...)

        ExplosionCallback.EVENT.register(ExplosionCallback { world, explosion ->
            if (world.isClient) {
                return@ExplosionCallback false
            }

            logger.info(explosion.toString())
            val entityType = explosion.entity?.type ?: return@ExplosionCallback false
            val affectedBlocks = explosion.affectedBlocks

            links.removeMatchingBlocks(world as ServerWorld, affectedBlocks, "${entityType.name} blew it up")

            true
        })
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
            }
            true
        })
    }

}
