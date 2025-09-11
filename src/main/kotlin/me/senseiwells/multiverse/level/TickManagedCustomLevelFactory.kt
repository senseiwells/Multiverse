package me.senseiwells.multiverse.level

import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import me.senseiwells.multiverse.utils.multiverse
import net.casual.arcade.dimensions.level.CustomLevel
import net.casual.arcade.dimensions.level.LevelGenerationOptions
import net.casual.arcade.dimensions.level.LevelPersistence
import net.casual.arcade.dimensions.level.LevelProperties
import net.casual.arcade.dimensions.level.factory.CustomLevelFactory
import net.casual.arcade.dimensions.level.factory.SimpleCustomLevelFactory
import net.casual.arcade.utils.serialization.codec.CodecProvider
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.Level

class TickManagedCustomLevelFactory(
    properties: LevelProperties,
    generationOptions: LevelGenerationOptions,
    persistence: LevelPersistence
): SimpleCustomLevelFactory(properties, generationOptions, persistence) {
    override fun create(server: MinecraftServer, key: ResourceKey<Level>): CustomLevel {
        return TickManagedCustomLevel(server, key, this.properties, this.generationOptions, this.persistence, this)
    }

    override fun codec(): MapCodec<out CustomLevelFactory> {
        return CODEC
    }

    companion object: CodecProvider<TickManagedCustomLevelFactory> {
        override val ID: ResourceLocation = multiverse("tick_managed")

        override val CODEC: MapCodec<out TickManagedCustomLevelFactory> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                propertiesCodec(), generationOptionsCodec(), persistenceCodec()
            ).apply(instance, ::TickManagedCustomLevelFactory)
        }
    }
}