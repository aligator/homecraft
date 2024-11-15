package dev.aligator.homecraft

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
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
import java.nio.file.Path
import java.util.regex.Pattern

fun isControlBlock(block: AbstractBlock): Boolean {
  return block is AbstractSignBlock
}

class Homecraft : ModInitializer {
    private val logger = LogManager.getLogger("MineAssistant")
    private var links: LinkStore? = null
    private var ha: HomeAssistant? = null
    private var server: MinecraftServer? = null
    private var updateHandler: BlockUpdateHandler? = null


    companion object {
        val config: SimpleConfig  = SimpleConfig.of( "homecraft" ).request();

        val haURL = config.getOrDefault("ha_url", "http://localhost:8123" );
        val haToken = config.getOrDefault("ha_token", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJiOGI4MzVhYjY5NGU0MTk5OTZmYWYxMzYwN2Y0ZjljNSIsImlhdCI6MTczMTY5MTAwOSwiZXhwIjoyMDQ3MDUxMDA5fQ.CV8u2pWYp6Zue89njcbKKnolV4Dv9HqA2zYVV2jVSUs")
    }

    override fun onInitialize() {
        val path: Path = FabricLoader.getInstance().configDir

        ServerLifecycleEvents.SERVER_STARTING.register { minecraftServer ->
            // Initialize Home Assistant connection.
            try {
                ha = HomeAssistant(haURL, haToken, this)
                ha!!.connect()
                ha!!.startReconnectTimer()
                server = minecraftServer


                if (ha != null) {
                    links = LinkStore((path.resolve("homecraft_links.json").toFile()))
                    links!!.read()

                    updateHandler = BlockUpdateHandler(ha!!, links!!)

                }

            } catch (e: URISyntaxException) {
                logger.warn("Could not connect to Home Assistant", e)
            }
        }
        registerCommands()

        ServerLifecycleEvents.SERVER_STOPPED.register {
            // Cleanup on server stop
            links?.commit()
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

        val block = player.world.getBlockState(blockPos).block

        if (isControlBlock(block)) {

            links.add(player.serverWorld, BlockPos(blockPos.x.toInt(), blockPos.y.toInt(), blockPos.z.toInt()), haEntity)
            logger.info("${player.name} linked block at $blockPos to Home Assistant entity '$haEntity'")
            player.sendMessage(Text.literal("Linked block at $blockPos to entity '$haEntity'"), false)

            // Request all entity states from Home Assistant
            ha.fetchEntities()
            return true
        } else {
            player.sendMessage(Text.literal("Block at $blockPos is not a valid block for Homecraft"), false)
            return false
        }
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

        val invalidLinks = mutableListOf<BlockLocation>()

        links.linkedBlocks(entityID) { link ->

            val world = server.getWorld(server.worldRegistryKeys?.find { key -> key.registry == link.location.worldId.registry && key.value == link.location.worldId.value })
            if (world == null) {
               // invalidLinks.add(link.location)
                return@linkedBlocks
            }
            val targetBlockPos = BlockPos(link.location.x, link.location.y, link.location.z)
            println("POS ${targetBlockPos}")
            // Must run in the main thread to be able to access the entities.
            // TODO: Test if we get null on unloaded chunks?
            server.execute{
                val blockEntity = world.getBlockEntity(targetBlockPos)
                println("${entityID} ${blockEntity}")
                if (blockEntity != null) {
                    println("UPDATE sign${targetBlockPos}")
                    if (blockEntity is SignBlockEntity) {
                        updateSign(blockEntity, entityName, haState)
                        return@execute
                    }
                }

                println("UPDATE ${targetBlockPos}")
                // Handle powerable/openable blocks (e.g., doors, levers)
                handleBlockUpdate(world, targetBlockPos, haState == "on")
            }
        }

        // Remove no longer valid links.
        invalidLinks.forEach(links::remove)
    }

    private fun updateSign(sign: SignBlockEntity, entityName: String, haState: String) {
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
            listOf("$entityName", "$color$haState", "", "")
        }

        signText.forEachIndexed { index, line ->
            sign.setText(sign.getText(true).withMessage(index, Text.literal(line)), true)
        }
        sign.markDirty()
    }

    fun handleBlockUpdate(world: ServerWorld, pos: BlockPos, newState: Boolean) {
        val blockState = world.getBlockState(pos)

        world.updateComparators(pos, blockState.block)
    }
}
