package dev.aligator.homecraft

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.util.logging.Logger
import kotlin.collections.MutableMap
        // TODO: maybe use a mixin more directly to create a block remove event, to catch all possibilities (e.g. endermen, tnt, ...)


/**
 * A WebSocket connection to Home Assistant.
 */
class HomeAssistant(url: String, private val token: String, private val ma: Homecraft) : WebSocketClient(
    URI(
        (if (url.endsWith("/")) url else "$url/")
            .replaceFirst("^http".toRegex(), "ws") + "api/websocket"
    )
) {

    private val gson = Gson()
    private val logger: Logger = Logger.getLogger("Home Assistant")
    private var isReconnecting = false
    val allEntities = ArrayList<String>()  // List of all entity IDs for tab completion
    private var id = 1
    private var statesId = 0
    val lastValues: MutableMap<String, String> = mutableMapOf()

    /**
     * Returns entity IDs that contain a search term. Append " (LINKED)" if it is found in the link store.
     *
     * @param partial string to search for in entity IDs
     * @return List of entity IDs that contain the partial string
     */
    fun searchedEntities(links: LinkStore, partial: String): List<String> {
        return allEntities.filter { it.contains(partial) }
            .map { if (links.containsEntity(it)) "$it (LINKED)" else it }
    }

    fun startReconnectTimer() {
        ServerTickEvents.START_SERVER_TICK.register {
            if (isReconnecting) {
                reconnect()
            }
        }
    }

    override fun onOpen(handshakedata: ServerHandshake) {
        if (isReconnecting) logger.info("Home Assistant socket re-opened") else logger.info("Home Assistant socket opened")
        isReconnecting = false
    }

    override fun onClose(code: Int, reason: String, remote: Boolean) {
        if (!isReconnecting) logger.info("Home Assistant socket closed! Attempting reconnect...")
        isReconnecting = true
    }

    override fun onError(ex: Exception) {
        if (!isReconnecting) logger.warning("Home Assistant socket error: ${ex.message}")
    }

    override fun onMessage(message: String) {
        logger.info(message)
        val jsonMessage = gson.fromJson(message, JsonObject::class.java)
        when (jsonMessage["type"].asString) {
            "auth_required" -> sendAuth()
            "auth_invalid" -> logger.warning("Home Assistant authentication failed: ${jsonMessage["message"].asString}")
            "auth_ok" -> {
                logger.info("Successfully authenticated with Home Assistant")
                fetchEntities()
                subscribe()
            }
            "result" -> handleResult(jsonMessage)
            "event" -> {
                val event = jsonMessage["event"].asJsonObject
                if (event["event_type"].asString == "state_changed") {
                    // Home Assistant has flipped something on or off
                    val evData = event.getAsJsonObject("data")
                    val entityID = evData["entity_id"].asString

                    if (!evData.has("new_state") || evData["new_state"].isJsonNull) {
                        return
                    }

                    val newState = evData.getAsJsonObject("new_state") ?: evData.getAsJsonObject("state")
                    val haState = newState["state"].asString

                    lastValues.put(entityID, haState)
                }
                ma.updateBlocksFromSubscription(jsonMessage["event"].asJsonObject)
            }
        }
    }

    fun lastState(entity: String?): String {
        return lastValues.getOrDefault(entity, "off")
    }

    /**
     * Change the state of an entity in Home Assistant.
     *
     * @param entity The ID of the entity to update
     * @param state  The new state (on or off)
     */
    fun setState(entity: String, state: Boolean) {
        val serviceData = JsonObject().apply { add("entity_id", JsonPrimitive(entity)) }
        val message = JsonObject().apply {
            add("id", JsonPrimitive(++id))
            add("type", JsonPrimitive("call_service"))
            add("domain", JsonPrimitive(entity.split(".")[0]))
            add("service", JsonPrimitive("turn_${if (state) "on" else "off"}"))
            add("service_data", serviceData)
        }
        logger.info(gson.toJson(message))
        send(gson.toJson(message))
    }

    /**
     * Send our access token to the server.
     */
    private fun sendAuth() {
        val message = JsonObject().apply {
            add("type", JsonPrimitive("auth"))
            add("access_token", JsonPrimitive(token))
        }
        send(gson.toJson(message))
    }

    /**
     * Ask the server to send state updates.
     */
    private fun subscribe() {
        val message = JsonObject().apply {
            add("id", JsonPrimitive(++id))
            add("type", JsonPrimitive("subscribe_events"))
            add("event_type", JsonPrimitive("state_changed"))
        }
        send(gson.toJson(message))
    }

    /**
     * Ask the server for a list of all states.
     * Used for tab completion.
     */
    fun fetchEntities() {
        val message = JsonObject().apply {
            id++
            statesId = id
            add("id", JsonPrimitive(statesId))
            add("type", JsonPrimitive("get_states"))
        }
        logger.info(gson.toJson(message))
        send(gson.toJson(message))
    }

    /**
     * Result of executing a command.
     */
    private fun handleResult(message: JsonObject) {
        if (!message["success"].asBoolean) {
            val error = message["error"].asJsonObject["message"].asString
            logger.warning("Home Assistant failed to execute command: $error")
            return
        }

        if (message["id"].asInt == statesId) {
            val result = message["result"].asJsonArray

            // update the tab completion list
            allEntities.clear()
            result.forEach {
                allEntities.add(it.asJsonObject["entity_id"].asString)
                lastValues.put(it.asJsonObject["entity_id"].asString, it.asJsonObject["state"].asString)
            }


            // update the state of any blocks we have linked
            ma.updateBlocksFromDump(result)
        }
    }
}
