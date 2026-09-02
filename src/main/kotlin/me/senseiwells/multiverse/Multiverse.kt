package me.senseiwells.multiverse

import me.senseiwells.multiverse.commands.MultiverseCommand
import me.senseiwells.multiverse.level.TickManagedCustomLevelFactory
import me.senseiwells.multiverse.utils.MultiverseRegistries
import me.senseiwells.multiverse.utils.multiverse
import net.casual.arcade.commands.register
import net.casual.arcade.dimensions.utils.DimensionRegistries
import net.casual.arcade.events.GlobalEventHandler
import net.casual.arcade.events.server.ServerRegisterCommandEvent
import net.casual.arcade.events.server.registry.RegistryEventHandler
import net.casual.arcade.events.server.registry.RegistryLoadedFromResourcesEvent
import net.casual.arcade.events.utils.register
import net.casual.arcade.utils.serialization.codec.CodecProvider.Companion.register
import net.fabricmc.api.ModInitializer
import net.minecraft.core.Holder
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.dimension.BuiltinDimensionTypes
import net.minecraft.world.level.dimension.LevelStem
import net.minecraft.world.level.levelgen.FlatLevelSource
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

object Multiverse: ModInitializer {
    const val MOD_ID = "multiverse"

    val logger: Logger = LoggerFactory.getLogger(MOD_ID)

    override fun onInitialize() {
        TickManagedCustomLevelFactory.register(DimensionRegistries.CUSTOM_LEVEL_FACTORY)

        RegistryEventHandler.register(MultiverseRegistries.LEVEL_STEM, ::registerCustomStems)

        GlobalEventHandler.Server.register<ServerRegisterCommandEvent> {
            it.register(MultiverseCommand)
        }
    }

    private fun registerCustomStems(event: RegistryLoadedFromResourcesEvent<LevelStem>) {
        val dimensions = event.lookupOrThrow(Registries.DIMENSION_TYPE)
        val overworld = dimensions.getOrThrow(BuiltinDimensionTypes.OVERWORLD)
        val biomes = event.lookupOrThrow(Registries.BIOME)
        val plains = FlatLevelGeneratorSettings.getDefaultBiome(biomes)

        event.register(
            multiverse("void"),
            LevelStem(overworld, this.createSingleLayerGenerator(Blocks.AIR, plains))
        )

        event.register(
            multiverse("white_glass"),
            LevelStem(overworld, this.createSingleLayerGenerator(Blocks.STAINED_GLASS.white, plains))
        )

        val layers = ArrayList<Pair<Int, Block>>(3);
        layers.add(Pair(1, Blocks.BEDROCK))
        layers.add(Pair(2, Blocks.DIRT))
        layers.add(Pair(1, Blocks.GRASS_BLOCK))

        event.register(
            multiverse("flat"),
            LevelStem(overworld, this.createLayeredGenerator(layers, plains))
        )

        layers.clear()

        // Copy stems from the vanilla registry
        val stems = event.lookupOrThrow(Registries.LEVEL_STEM) as HolderLookup
        for (stem in stems.listElements()) {
            event.register(stem.key().identifier(), stem.value())
        }
    }

    private fun createLayeredGenerator(blocks: ArrayList<Pair<Int, Block>>, biome: Holder<Biome>): FlatLevelSource {
        val settings = FlatLevelGeneratorSettings(Optional.empty(), biome, listOf())
        for (i in blocks.indices) {
            val block = blocks[i];
            settings.layersInfo.add(FlatLayerInfo(block.first, block.second))
        }
        settings.updateLayers()
        return FlatLevelSource(settings)
    }

    private fun createMultipleLayerGenerator(block: Block, biome: Holder<Biome>, height: Int): FlatLevelSource {
        val settings = FlatLevelGeneratorSettings(Optional.empty(), biome, listOf())
        settings.layersInfo.add(FlatLayerInfo(height, block))
        settings.updateLayers()
        return FlatLevelSource(settings)
    }

    private fun createSingleLayerGenerator(block: Block, biome: Holder<Biome>): FlatLevelSource {
        return createMultipleLayerGenerator(block, biome, 1)
    }
}