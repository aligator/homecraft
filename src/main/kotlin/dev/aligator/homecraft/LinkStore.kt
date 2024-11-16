package dev.aligator.homecraft

import net.minecraft.registry.RegistryKey
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import org.apache.logging.log4j.LogManager
import java.io.*

/**
 * A class to manage the association between blocks in the Minecraft world and Home Assistant entities.
 */
class LinkStore() {

    private val logger = LogManager.getLogger("Home Assistant Link Store")
    private var blocks: MutableMap<BlockLocation, ControlBlock> = mutableMapOf()


    /**
     * Operate on all blocks assigned to a specific entity.
     *
     * @param haEntity The ID of the Home Assistant entity
     */
    fun linkedBlocks(haEntity: String, action: (ControlBlock) -> Unit) {
        blocks.values.filter { it.getHAEnttyId() == haEntity }.forEach(action)
    }

    fun getLink(world: ServerWorld, pos: BlockPos): ControlBlock? {
        return blocks[BlockLocation(world, pos)]
    }

    /**
     * Get the linked entity for a block at a specific location.
     *
     * @param world the world of the block
     * @param pos the position of the block
     * @return null if not linked or the string entity ID if linked
     */
    fun getEntity(world: ServerWorld, pos: BlockPos): String? {
        val location = BlockLocation(world, pos)
        val link = blocks[location] ?: return null
        return link.getHAEnttyId()
    }

    /**
     * Add a new link
     *
     * @param controlBlock the ControlBlock to link
     */
    fun add(controlBlock: ControlBlock) {
        val world = controlBlock.getWorld() ?: return
        if (world != null) {
            blocks[BlockLocation(world, controlBlock.pos)] = controlBlock
        }
    }

    /**
     * Remove a link by location.
     *
     * @param location The location of the linked block
     */
    fun remove(location: BlockLocation) {
        // TODO: make parameters more consistent
        blocks.remove(location)
    }

    /**
     * Unlink any linked blocks in a list, and log a reason why
     *
     * @param blockList a list of positions of blocks to search through.
     * @param reason a text description of why we are removing blocks (e.g. "explosion")
     */
    fun removeMatchingBlocks(world: ServerWorld, blockList: List<BlockPos>, reason: String) {
        for (pos in blockList) {
            val removed = blocks.remove(BlockLocation(world, pos))
            if (removed != null) {
                logger.info(
                    "ControlBlock was unlinked from '${removed.getHAEnttyId()}' because $reason"
                )
            }
        }
    }
}


data class BlockLocation(val worldId: RegistryKey<World>, val x: Int, val y: Int, val z: Int) : Serializable {

    /**
     * Constructor to initialize BlockLocation from a BlockPos and a World.
     */
    constructor(world: ServerWorld, pos: BlockPos) : this(
        worldId = world.registryKey,
        x = pos.x,
        y = pos.y,
        z = pos.z
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BlockLocation) return false

        return worldId.toString() == other.worldId.toString() &&
                x == other.x &&
                y == other.y &&
                z == other.z
    }

    override fun hashCode(): Int {
        var result = worldId.toString().hashCode()
        result = 31 * result + x
        result = 31 * result + y
        result = 31 * result + z
        return result
    }
}
