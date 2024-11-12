package dev.aligator.homecraft

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.state.property.Properties
import net.minecraft.state.property.Property
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import org.apache.logging.log4j.LogManager

object BlockUtil {
    private val logger = LogManager.getLogger("BlockUtil")

    /**
     * Get the current power level of an analogue powerable block.
     *
     * @param state The block state to check
     * @return The power level as a float in the range 0..1
     */
    fun powerLevel(state: BlockState): Float {
        val power = state.luminance // Luminance gives an approximation of power levels
        val maxPower = 15f // Default max power level in Minecraft
        return if (power > 0) 1 - (power / maxPower) else 0f
    }

    /**
     * Check if a powerable block is powered.
     *
     * @param state The block state to check
     * @return The boolean "powered" state for powerable blocks, or
     * whether the block is at least 50% powered for analog powerable blocks.
     */
    fun isPowered(state: BlockState): Boolean {
        // For blocks with a power level, check if they are at least half-powered
        if (state.properties.contains(Properties.POWER)) {
            val powerLevel = state[Properties.POWER]
            return powerLevel >= 8 // 50% of max power (15)
        }
        // For simple powerable blocks, check the POWERED property
        else if (state.contains(Properties.POWERED)) {
            // Some blocks have inverted power states (for example, redstone torches), so we invert
            return !state[Properties.POWERED]
        }

        return false // If the block has no power-related properties, it's considered unpowered
    }

    /**
     * Sets the powered state of a block if it has a POWERED property.
     *
     * @param world The world the block is in
     * @param pos The position of the block
     * @param powered The desired powered state
     */
    fun setPowered(world: World, pos: BlockPos, powered: Boolean) {
        val state = world.getBlockState(pos)

        // Check if the block has the POWERED property
        if (state.contains(Properties.POWERED)) {
            // Update the block state with the new powered value
            val newState = state.with(Properties.POWERED, powered)
            world.setBlockState(pos, newState)
        }
    }

    fun isOpen(state: BlockState): Boolean {
        return state.get(Properties.OPEN) ?: false // Checks if the block has an "open" property and returns its state
    }

    fun setOpen(world: World, pos: BlockPos, open: Boolean) {
        val state = world.getBlockState(pos)
        if (state.contains(Properties.OPEN)) {
            world.setBlockState(pos, state.with(Properties.OPEN, open))
        }
    }

    /**
     * Get the value of an existing property in the specified BlockState.
     *
     * @param state The BlockState object to operate on
     * @param propertyName The name of the property to retrieve
     * @return The value of the property as a string, or null if the property is not found
     */
    fun getBlockDataProperty(state: BlockState, propertyName: String): String? {
        // Find the property with the given name
        val property: Property<*>? = state.block.stateManager.properties.firstOrNull { it.name == propertyName }

        // If the property exists, retrieve its value from the state and return it as a string
        return property?.let { state.get(it)?.toString() }
    }

    /**
     * Set the value of an existing property in the specified BlockState.
     *
     * @param world The world the block is in
     * @param pos The position of the block
     * @param propertyName The property whose value we want to update
     * @param value The new value for the property as a string
     * @return True if the property was successfully updated, false otherwise
     */
    fun setBlockStateProperty(world: World, pos: BlockPos, propertyName: String, value: String): Boolean {
        val state = world.getBlockState(pos)

        // Try to find the property with the given name
        val property = state.block.stateManager.properties.firstOrNull { it.name == propertyName }

        if (property != null) {
            // Attempt to parse and cast the value for the property
            val parsedValue = property.parse(value).orElse(null) as? Comparable<*> // Ensure parsed value is comparable

            // Only proceed if the parsed value matches the property type
            if (parsedValue != null && property.type.isInstance(parsedValue)) {
                @Suppress("UNCHECKED_CAST")
                val castedProperty = property as Property<Comparable<Any>>
                val newState = state.with(castedProperty, parsedValue as Comparable<Any>)
                world.setBlockState(pos, newState)
                return true
            }
        }

        return false // Property not found or value could not be parsed
    }


    /**
     * Plays an appropriate sound based on the block type and its new state.
     *
     * @param world The world of the block
     * @param pos The position of the block
     * @param state The block state
     * @param newState The new state indicating if the block is open/powered
     */
    fun makeSound(world: World, pos: BlockPos, state: BlockState, newState: Boolean) {
        val sound = when {
            state.isOf(net.minecraft.block.Blocks.IRON_DOOR) ->
                if (newState) SoundEvents.BLOCK_IRON_DOOR_OPEN else SoundEvents.BLOCK_IRON_DOOR_CLOSE
            state.isOf(net.minecraft.block.Blocks.OAK_DOOR) ->
                if (newState) SoundEvents.BLOCK_WOODEN_DOOR_OPEN else SoundEvents.BLOCK_WOODEN_DOOR_CLOSE
            state.isOf(net.minecraft.block.Blocks.IRON_TRAPDOOR) ->
                if (newState) SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN else SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE
            state.isOf(net.minecraft.block.Blocks.OAK_TRAPDOOR) ->
                if (newState) SoundEvents.BLOCK_WOODEN_TRAPDOOR_OPEN else SoundEvents.BLOCK_WOODEN_TRAPDOOR_CLOSE
            state.isOf(net.minecraft.block.Blocks.OAK_FENCE_GATE) ->
                if (newState) SoundEvents.BLOCK_FENCE_GATE_OPEN else SoundEvents.BLOCK_FENCE_GATE_CLOSE
            state.isOf(net.minecraft.block.Blocks.LEVER) ->
                SoundEvents.BLOCK_LEVER_CLICK
            else -> {
                logger.warn("Missing state update sound for block = ${state.block.name}, newState = $newState")
                if (newState) SoundEvents.BLOCK_WOODEN_TRAPDOOR_OPEN else SoundEvents.BLOCK_WOODEN_TRAPDOOR_CLOSE
            }
        }
        world.playSound(null, pos, sound, SoundCategory.BLOCKS, 1.0f, 1.0f)
    }
}
