package me.senseiwells.multiverse

import me.senseiwells.multiverse.commands.MultiverseCommand
import me.senseiwells.multiverse.utils.MultiverseRegistries
import net.casual.arcade.commands.register
import net.casual.arcade.events.GlobalEventHandler
import net.casual.arcade.events.ListenerRegistry.Companion.register
import net.casual.arcade.events.server.ServerRegisterCommandEvent
import net.casual.arcade.events.server.registry.RegistryEventHandler
import net.casual.arcade.events.server.registry.RegistryLoadedFromResourcesEvent
import net.casual.arcade.utils.ResourceLocation
import net.fabricmc.api.ModInitializer
import net.minecraft.core.Holder
import net.minecraft.core.HolderLookup
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.dimension.BuiltinDimensionTypes
import net.minecraft.world.level.dimension.LevelStem
import net.minecraft.world.level.levelgen.FlatLevelSource
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings
import java.util.*

object Multiverse: ModInitializer {
    const val MOD_ID = "multiverse"

    override fun onInitialize() {
        RegistryEventHandler.register(MultiverseRegistries.LEVEL_STEM, ::registerCustomStems)

        GlobalEventHandler.Server.register<ServerRegisterCommandEvent> {
            it.register(MultiverseCommand)
        }
    }

    fun id(path: String): ResourceLocation {
        return ResourceLocation(MOD_ID, path)
    }

    private fun registerCustomStems(event: RegistryLoadedFromResourcesEvent<LevelStem>) {
        val dimensions = event.lookupOrThrow(Registries.DIMENSION_TYPE)
        val overworld = dimensions.getOrThrow(BuiltinDimensionTypes.OVERWORLD)
        val biomes = event.lookupOrThrow(Registries.BIOME)
        val plains = FlatLevelGeneratorSettings.getDefaultBiome(biomes)

        Registry.register(
            event.registry, id("void"), LevelStem(overworld, this.createSingleLayerGenerator(Blocks.AIR, plains))
        )
        Registry.register(
            event.registry, id("white_glass"), LevelStem(overworld, this.createSingleLayerGenerator(Blocks.WHITE_STAINED_GLASS, plains))
        )

        // Copy stems from the vanilla registry
        val stems = event.lookupOrThrow(Registries.LEVEL_STEM) as HolderLookup
        for (stem in stems.listElements()) {
            Registry.register(event.registry, stem.key(), stem.value())
        }
    }

    private fun createSingleLayerGenerator(block: Block, biome: Holder<Biome>): FlatLevelSource {
        val settings = FlatLevelGeneratorSettings(Optional.empty(), biome, listOf())
        settings.layersInfo.add(FlatLayerInfo(1, block))
        settings.updateLayers()
        return FlatLevelSource(settings)
    }
}