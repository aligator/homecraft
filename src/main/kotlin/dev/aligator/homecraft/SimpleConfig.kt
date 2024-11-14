package dev.aligator.homecraft

import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

class SimpleConfig(private val request: ConfigRequest) {

    private val config = HashMap<String, String>()
    private var broken = false

    companion object {
        private val LOGGER: Logger = LogManager.getLogger("SimpleConfig")

        /**
         * Creates a new config request object, ideally `namespace`
         * should be the mod ID of the requesting mod.
         *
         * @param filename - name of the config file
         * @return new config request object
         */
        fun of(filename: String): ConfigRequest {
            val path: Path = FabricLoader.getInstance().configDir
            return ConfigRequest(path.resolve("$filename.properties").toFile(), filename)
        }
    }

    interface DefaultConfig {
        fun get(namespace: String): String

        companion object {
            class EmptyConfig : DefaultConfig {
                override fun get(namespace: String): String {
                    return ""
                }
            }

            val default: DefaultConfig = EmptyConfig()
        }
    }



    class ConfigRequest(val file: File, val filename: String) {
        var provider: DefaultConfig = DefaultConfig.default

        /**
         * Sets the default config provider, used to generate the
         * config if it's missing.
         *
         * @param provider default config provider
         * @return current config request object
         * @see DefaultConfig
         */
        fun provider(provider: DefaultConfig): ConfigRequest {
            this.provider = provider
            return this
        }

        /**
         * Loads the config from the filesystem.
         *
         * @return config object
         * @see SimpleConfig
         */
        fun request(): SimpleConfig {
            return SimpleConfig(this)
        }

        fun getConfig(): String {
            return provider.get(filename) + "\n"
        }
    }

    init {
        val identifier = "Config '${request.filename}'"
        if (!request.file.exists()) {
            LOGGER.info("$identifier is missing, generating default one...")
            try {
                createConfig()
            } catch (e: IOException) {
                LOGGER.error("$identifier failed to generate!")
                LOGGER.trace(e)
                broken = true
            }
        }

        if (!broken) {
            try {
                loadConfig()
            } catch (e: Exception) {
                LOGGER.error("$identifier failed to load!")
                LOGGER.trace(e)
                broken = true
            }
        }
    }

    private fun createConfig() {
        request.file.parentFile.mkdirs()
        Files.createFile(request.file.toPath())

        PrintWriter(request.file, "UTF-8").use { writer ->
            writer.write(request.getConfig())
        }
    }

    private fun loadConfig() {
        Scanner(request.file).use { reader ->
            var line = 1
            while (reader.hasNextLine()) {
                parseConfigEntry(reader.nextLine(), line++)
            }
        }
    }

    private fun parseConfigEntry(entry: String, line: Int) {
        if (entry.isNotEmpty() && !entry.startsWith("#")) {
            val parts = entry.split("=", limit = 2)
            if (parts.size == 2) {
                this@SimpleConfig.config[parts[0]] = parts[1]
            } else {
                throw RuntimeException("Syntax error in config file on line $line!")
            }
        }
    }

    /**
     * Queries a value from config, returns `null` if the
     * key does not exist.
     *
     * @return value corresponding to the given key
     * @see SimpleConfig.getOrDefault
     */
    @Deprecated("Use getOrDefault instead.")
    fun get(key: String): String? {
        return this@SimpleConfig.config[key]
    }

    /**
     * Returns string value from config corresponding to the given
     * key, or the default string if the key is missing.
     *
     * @return value corresponding to the given key, or the default value
     */
    fun getOrDefault(key: String, def: String): String {
        return this@SimpleConfig.config[key] ?: def
    }

    /**
     * Returns integer value from config corresponding to the given
     * key, or the default integer if the key is missing or invalid.
     *
     * @return value corresponding to the given key, or the default value
     */
    fun getOrDefault(key: String, def: Int): Int {
        return this@SimpleConfig.config[key]?.toIntOrNull() ?: def
    }

    /**
     * Returns boolean value from config corresponding to the given
     * key, or the default boolean if the key is missing.
     *
     * @return value corresponding to the given key, or the default value
     */
    fun getOrDefault(key: String, def: Boolean): Boolean {
        return this@SimpleConfig.config[key]?.equals("true", ignoreCase = true) ?: def
    }

    /**
     * Returns double value from config corresponding to the given
     * key, or the default string if the key is missing or invalid.
     *
     * @return value corresponding to the given key, or the default value
     */
    fun getOrDefault(key: String, def: Double): Double {
        return this@SimpleConfig.config[key]?.toDoubleOrNull() ?: def
    }

    /**
     * If any error occurred during loading or reading from the config
     * a 'broken' flag is set, indicating that the config's state
     * is undefined and should be discarded using `delete()`
     *
     * @return the 'broken' flag of the configuration
     */
    fun isBroken(): Boolean {
        return broken
    }

    /**
     * Deletes the config file from the filesystem
     *
     * @return true if the operation was successful
     */
    fun delete(): Boolean {
        LOGGER.warn("Config '${request.filename}' was removed from existence! Restart the game to regenerate it.")
        return request.file.delete()
    }
}
