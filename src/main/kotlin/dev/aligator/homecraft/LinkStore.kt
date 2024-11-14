package dev.aligator.homecraft

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.registry.RegistryKey
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import org.apache.logging.log4j.LogManager
import java.io.*
import java.nio.file.Path
import kotlin.collections.ArrayList

class LinkStore(file: File) {

    private val gson = Gson()
    private val logger = LogManager.getLogger("Home Assistant Link Store")
    val path: Path = FabricLoader.getInstance().configDir
    private val file = path.resolve("links.json").toFile()
    private var blocks: MutableMap<BlockLocation, LinkedBlock> = mutableMapOf()

    /**
     * Read blocks list from disk.
     */
    fun read() {
        try {
            FileReader(file).use { reader ->
                val type = object : TypeToken<ArrayList<LinkedBlock>>() {}.type
                val values: ArrayList<LinkedBlock>? = gson.fromJson(reader, type)

                blocks = if (values == null) {
                    mutableMapOf()
                } else {
                    values.associateBy { it.location }.toMutableMap()
                }
            }
        } catch (exception: IOException) {
            if (exception !is FileNotFoundException) {
                logger.warn("Could not read Home Assistant links: $exception")
            }
            blocks = mutableMapOf()
        }
    }

    /**
     * Save the blocks list to disk.
     */
    fun commit() {
        try {
            FileWriter(file).use { writer ->
                gson.toJson(blocks.values, writer)
            }
        } catch (exception: IOException) {
            logger.warn("Could not commit Home Assistant links: $exception")
        }
    }

    /**
     * Operate on all blocks assigned to a specific entity.
     *
     * @param haEntity The ID of the Home Assistant entity
     */
    fun linkedBlocks(haEntity: String, action: (LinkedBlock) -> Unit) {
        blocks.values.filter { it.entity == haEntity }.forEach(action)
    }

    /**
     * Check if we have at least one block in the store linked to a Home Assistant entity.
     *
     * @param haEntity The entity ID to check for
     * @return whether it was found or not
     */
    fun containsEntity(haEntity: String): Boolean {
        return blocks.values.any { it.entity.equals(haEntity, ignoreCase = true) }
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

        return if (link.material == world.getBlockState(pos).block.asItem().toString()) {
            link.entity
        } else {
            remove(location)  // Remove the link if the material doesn't match
            null
        }
    }

    /**
     * Add a new link
     *
     * @param world the world of the block
     * @param pos the position of the block
     * @param haEntity The Home Assistant entity ID
     */
    fun add(world: ServerWorld, pos: BlockPos, haEntity: String) {
        val link = LinkedBlock(world, pos, haEntity)
        blocks[link.location] = link
        commit()
    }

    /**
     * Remove a link by location.
     *
     * @param location The location of the linked block
     */
    fun remove(location: BlockLocation) {
        if (blocks.remove(location) != null) {
            commit()
        }
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
                    "${world.getBlockState(pos).block.asItem()} was unlinked from '${removed.entity}' because $reason"
                )
            }
        }
        commit()
    }
}

data class LinkedBlock(val location: BlockLocation, val material: String, val entity: String) : Serializable {
    /**
     * Create a link between a block and an entity.
     *
     * @param world the world of the block
     * @param pos the position of the block
     * @param entity The Home Assistant entity ID
     */
    constructor(world: ServerWorld, pos: BlockPos, entity: String) : this(
        location = BlockLocation(world, pos),
        material = world.getBlockState(pos).block.asItem().toString(),
        entity = entity
    )
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
