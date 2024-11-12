package dev.aligator.homecraft

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.mojang.brigadier.arguments.StringArgumentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.RedstoneLampBlock
import net.minecraft.block.TargetBlock
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.LidOpenable
import net.minecraft.block.entity.SignBlockEntity
import net.minecraft.block.entity.SignText
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.state.property.Properties
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import net.minecraft.world.RaycastContext
import org.apache.logging.log4j.LogManager
import java.io.File
import java.net.URISyntaxException
import java.util.regex.Pattern

class Homecraft : ModInitializer {
    private val logger = LogManager.getLogger("MineAssistant")
    private lateinit var links: LinkStore
    private var ha: HomeAssistant? = null
    private var server: MinecraftServer? = null

    companion object {
        val CONFIG: SimpleConfig  = SimpleConfig.of( "homecraft" ).request();

        val haURL = CONFIG.getOrDefault("ha_url", "http://localhost:8123" );
        val haToken = CONFIG.getOrDefault("ha_token", "token")
    }

    override fun onInitialize() {
        links = LinkStore(File("config/homecraft_links"))
        var  updateHandler: BlockUpdateHandler

        ServerLifecycleEvents.SERVER_STARTING.register { minecraftServer ->
            server = minecraftServer
            // Initialize Home Assistant connection
            try {
                ha = HomeAssistant(haURL, haToken, this)
                ha?.connect()
                ha?.startReconnectTimer()

                if (ha != null) {
                    updateHandler = BlockUpdateHandler(ha!!, links)
                }

            } catch (e: URISyntaxException) {
                logger.warn("Invalid URL for Home Assistant", e)
            }
            links.read()
        }

        ServerLifecycleEvents.SERVER_STOPPED.register {
            // Cleanup on server stop
            ha?.close()
            ha = null
            links.commit()
        }

        CommandRegistrationCallback.EVENT.register { dispatcher, registry, environment ->
            dispatcher.register(
                literal("link").executes { context ->
                    return@executes 0
                }.then(CommandManager.argument("haEntity", StringArgumentType.string())
                    .executes { context ->
                        val player = context.source.player
                        if (player == null) {
                            return@executes 0
                        }
                        val haEntity = StringArgumentType.getString(context, "haEntity")
                        onCommand(player, haEntity)
                        1
                    }
                )
            )
        }

    }

    private fun onCommand(player: ServerPlayerEntity, haEntity: String): Boolean {
        val ha = ha ?: return false

        // Calculate start and end points of the ray
        val cameraPos = player.getCameraPosVec(1.0f)
        val lookVec = player.getRotationVec(1.0f)
        val endPos = cameraPos.add(lookVec.multiply(5.0))

        // Perform the raycast
        val hitResult = player.world?.raycast(
            RaycastContext(
                cameraPos,
                endPos,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                player
            )
        )

        val blockPos = hitResult?.blockPos
        if (blockPos == null) {
            player.sendMessageToClient(Text.literal("Please look at a block within range."), false)
            return false
        }

        if (!ha.allEntities.contains(haEntity)) {
            player.sendMessageToClient(Text.literal("Entity '$haEntity' does not exist!"), false)
            return true
        }

        links.add(player.serverWorld, BlockPos(blockPos.x.toInt(), blockPos.y.toInt(), blockPos.z.toInt()), haEntity)
        logger.info("${player.name} linked block at $blockPos to Home Assistant entity '$haEntity'")
        player.sendMessage(Text.literal("Linked block at $blockPos to entity '$haEntity'"), false)

        // Request all entity states from Home Assistant
        ha.fetchEntities()
        return true
    }

    fun updateBlocksFromDump(states: JsonArray) {
        states.forEach { stateElement ->
            val state = stateElement.asJsonObject
            val entityID = state["entity_id"].asString
            val haState = state["state"].asString

            val attributes = state.getAsJsonObject("attributes")
            val haName = attributes?.get("friendly_name")?.asString ?: entityID

            updateBlocks(entityID, haName, haState)
        }
    }

    /**
     * Updates all the blocks linked to a single HA entity.
     * Used to process the subscription messages from HA.
     *
     * @param event the JsonObject of an event from HA
     */
    fun updateBlocksFromSubscription(event: JsonObject) {
        // logger.info(event.toString())
        if (event["event_type"].asString == "state_changed") {
            // Home Assistant has flipped something on or off
            val evData = event.getAsJsonObject("data")
            val entityID = evData["entity_id"].asString

            if (!evData.has("new_state") || evData["new_state"].isJsonNull) {
                return
            }

            val newState = evData.getAsJsonObject("new_state") ?: evData.getAsJsonObject("state")
            val haState = newState["state"].asString

            val attributes = newState.getAsJsonObject("attributes")
            val haName = attributes?.get("friendly_name")?.asString ?: entityID

            // Iterate through the list of blocks this entity is linked to
            updateBlocks(entityID, haName, haState)
        }
    }


    private fun updateBlocks(entityID: String, entityName: String, haState: String) {
        val invalidLinks = mutableListOf<BlockLocation>()

        links.linkedBlocks(entityID) { link ->
            val world = server?.getWorld(server?.worldRegistryKeys?.find { key -> key.registry == link.location.worldId.registry && key.value == link.location.worldId.value })
            if (world == null) {
                return@linkedBlocks
            }
            val targetBlockPos = BlockPos(link.location.x, link.location.y, link.location.z)

            server?.execute{
                val l = entityID
                val blockEntity = world.getBlockEntity(targetBlockPos)

                if (blockEntity != null && blockEntity is SignBlockEntity) {
                    updateSign(blockEntity, entityName, haState)
                    return@execute
                }

                // Handle powerable/openable blocks (e.g., doors, levers)
                handleBlockUpdate(world, targetBlockPos, haState == "on")
            }

        }

        invalidLinks.forEach(links::remove)
    }

    private fun updateSign(sign: SignBlockEntity, entityName: String, haState: String) {
        val textPattern = Pattern.quote("\\L")
        val signText = if (haState.contains(textPattern)) {
            haState.split(textPattern).take(4)
        } else {
            listOf("&3$entityName", haState, "", "")
        }

        signText.forEachIndexed { index, line ->
            sign.setText(sign.getText(true).withMessage(index, Text.literal(line)), true)
        }
        sign.markDirty()
    }

    fun handleBlockUpdate(world: ServerWorld, pos: BlockPos, newState: Boolean) {
        val blockState = world.getBlockState(pos)

        if (blockState.contains(Properties.OPEN)) {
            // This block is openable (e.g., door or trapdoor)
            world.setBlockState(pos, blockState.with(Properties.OPEN, newState))

            // Make a sound if the state has changed
            if (BlockUtil.isOpen(world.getBlockState(pos)) != newState) {
                BlockUtil.makeSound(world, pos, blockState, newState)
            }

        } else if (blockState.contains(Properties.POWERED)) {
            // This block is powerable (e.g., lever, button)
            world.setBlockState(pos, blockState.with(Properties.POWERED, newState))

            // Make a sound if the state matches the intended powered state
            if (BlockUtil.isPowered(world.getBlockState(pos)) == newState) {
                BlockUtil.makeSound(world, pos, blockState, newState)
            }
        }
    }
}