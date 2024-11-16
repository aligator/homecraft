package dev.aligator.homecraft

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents
import net.minecraft.block.AbstractBlock
import net.minecraft.block.AbstractSignBlock
import net.minecraft.block.entity.SignBlockEntity
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import net.minecraft.world.RaycastContext
import org.apache.logging.log4j.LogManager
import java.net.URISyntaxException
import java.util.regex.Pattern

fun isControlBlock(block: AbstractBlock): Boolean {
  return block is AbstractSignBlock
}

class Homecraft : ModInitializer {
    private val logger = LogManager.getLogger("MineAssistant")
    private var links: LinkStore = LinkStore()
    private var ha: HomeAssistant? = null
    private var server: MinecraftServer? = null
    private var updateHandler: BlockUpdateHandler? = null


    companion object {
        val config: SimpleConfig  = SimpleConfig.of( "homecraft" ).request();

        val haURL = config.getOrDefault("ha_url", "http://localhost:8123" );
        val haToken = config.getOrDefault("ha_token", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJiOGI4MzVhYjY5NGU0MTk5OTZmYWYxMzYwN2Y0ZjljNSIsImlhdCI6MTczMTY5MTAwOSwiZXhwIjoyMDQ3MDUxMDA5fQ.CV8u2pWYp6Zue89njcbKKnolV4Dv9HqA2zYVV2jVSUs")
    }

    override fun onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register { minecraftServer ->
            // Initialize Home Assistant connection.
            try {
                ha = HomeAssistant(haURL, haToken, this)
                ha!!.connect()
                ha!!.startReconnectTimer()
                server = minecraftServer


                if (ha != null) {
                    updateHandler = BlockUpdateHandler(ha!!, links)
                }

            } catch (e: URISyntaxException) {
                logger.warn("Could not connect to Home Assistant", e)
            }
        }

        // Not sure why, but ServerBlockEntityEvents.BLOCK_ENTITY_LOAD doesn't do anything...
        ServerChunkEvents.CHUNK_LOAD.register{serverWorld, chunk ->
            chunk.blockEntities.forEach { pos, entity ->
                if (entity is SignBlockEntity) {
                    // Note: pass the entity directly, as we have it already.
                    // Not doing so leads to a deadlock, as the world may not be set up fully yet.
                    val controlBlock = ControlBlock(serverWorld, pos, entity)
                    if (controlBlock.isValid()) {
                        println("connect homecraft sign ${controlBlock.getWorld()} ${controlBlock.pos} to HA entity: ${controlBlock.getHAEnttyId()} ")
                        links.add(controlBlock)
                    }
                }
            }
        }

        registerCommands()

        ServerLifecycleEvents.SERVER_STOPPED.register {
            // Cleanup on server stop
            ha?.close()
            ha = null
        }
    }

    private fun registerCommands() {
        CommandRegistrationCallback.EVENT.register { dispatcher, registry, environment ->
            dispatcher.register(
                literal("link").executes { context ->
                    return@executes 0
                }.then(CommandManager.argument("haEntity", StringArgumentType.string()).suggests(haEntitySuggestionProvider())
                    .executes { context ->
                        val player = context.source.player
                        if (player == null) {
                            return@executes 0
                        }
                        val haEntity = StringArgumentType.getString(context, "haEntity")
                        onLinkBlock(player, haEntity)
                        1
                    }
                )
            )
        }
    }


    private fun haEntitySuggestionProvider(): SuggestionProvider<ServerCommandSource> {
        return SuggestionProvider { context, builder ->
            val ha = ha ?: return@SuggestionProvider builder.buildFuture()
            val links = links ?: return@SuggestionProvider builder.buildFuture()

            val input = builder.remaining
            val suggestions = ha.searchedEntities(links, input)
            for (suggestion in suggestions) {
                builder.suggest(suggestion)
            }
            return@SuggestionProvider builder.buildFuture()
        }
    }


    /**
     * Link the block the player looks at to HA.
     * Use the sign back lines to save the needed data.
     */
    private fun onLinkBlock(player: ServerPlayerEntity, haEntity: String): Boolean {
        val ha = ha ?: return false
        val links = links ?: return false

        if (!ha.allEntities.contains(haEntity)) {
            player.sendMessageToClient(Text.literal("Entity '$haEntity' does not exist!"), false)
            return true
        }

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

        val controlBlock = ControlBlock(player.serverWorld, blockPos, haEntity)
        if (!controlBlock.isValid()) {
            player.sendMessageToClient(Text.literal("Block at $blockPos is not a valid block for Homecraft. Only signs are allowed."), false)
            return false
        }

        links.add(controlBlock)
        logger.info("${player.name} linked block at $blockPos to Home Assistant entity '$haEntity'")
        player.sendMessage(Text.literal("Linked block at $blockPos to entity '$haEntity'"), false)

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
        val links = links ?: return
        val server = server ?: return

        // Not sure what to do with that:
        //val invalidLinks = mutableListOf<BlockLocation>()

        links.linkedBlocks(entityID) { link ->

            val world = link.getWorld()
            if (world == null) {
               // invalidLinks.add(link.location)
                return@linkedBlocks
            }

            // Must run in the main thread to be able to access the entities.
            server.execute{
                val blockEntity = world.getBlockEntity(link.pos)
                if (blockEntity != null) {
                    if (blockEntity is SignBlockEntity) {
                        updateSign(blockEntity, entityID, entityName, haState)
                    }
                }

                handleBlockUpdate(world, link.pos)
            }
        }

        // Remove no longer valid links.
        // invalidLinks.forEach(links::remove)
    }

    private fun updateSign(sign: SignBlockEntity, entityId: String, entityName: String, haState: String) {
        // Todo refactor

        val textPattern = Pattern.quote("\\L")
        val signText = if (haState.contains(textPattern)) {
            haState.split(textPattern).take(4)
        } else {
            val color = if (haState == "on") {
                // Green
                "§3"
            } else if (haState == "off") {
                // Red
                "§4"
            } else {
                ""
            }
            listOf(entityName, "$color$haState", "", "")

        }

        signText.forEachIndexed { index, line ->
            sign.setText(sign.getText(true).withMessage(index, Text.literal(line)), true)
        }

        val signTextBack = if (haState.contains(textPattern)) {
            haState.split(textPattern).take(4)
        } else {
            listOf("[homecraft]", "$entityId", "", "")
        }

        signTextBack.forEachIndexed { index, line ->
            sign.setText(sign.getText(false).withMessage(index, Text.literal(line)), false)
        }
        sign.markDirty()
    }

    fun handleBlockUpdate(world: ServerWorld, pos: BlockPos) {
        val blockState = world.getBlockState(pos)

        world.updateComparators(pos, blockState.block)
    }
}
