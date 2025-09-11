package me.senseiwells.multiverse.level

import net.casual.arcade.dimensions.level.CustomLevel
import net.casual.arcade.dimensions.level.LevelGenerationOptions
import net.casual.arcade.dimensions.level.LevelPersistence
import net.casual.arcade.dimensions.level.LevelProperties
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.Level
import java.util.function.BooleanSupplier

class TickManagedCustomLevel(
    server: MinecraftServer,
    key: ResourceKey<Level>,
    properties: LevelProperties,
    options: LevelGenerationOptions,
    persistence: LevelPersistence,
    factory: TickManagedCustomLevelFactory
): CustomLevel(server, key, properties, options, persistence, factory) {
    private val manager = CustomLevelTickRateManager(this)

    override fun tickRateManager(): CustomLevelTickRateManager {
        return this.manager
    }

    override fun tick(hasTimeLeft: BooleanSupplier) {
        this.tickRateManager().tick()
        super.tick(hasTimeLeft)
    }
}