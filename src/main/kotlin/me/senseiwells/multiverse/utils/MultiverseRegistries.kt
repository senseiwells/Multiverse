package me.senseiwells.multiverse.utils

import me.senseiwells.multiverse.Multiverse
import net.casual.arcade.utils.registries.RegistryKeySupplier
import net.minecraft.world.level.dimension.LevelStem

object MultiverseRegistries: RegistryKeySupplier(Multiverse.MOD_ID) {
    @JvmField val LEVEL_STEM = this.create<LevelStem>("dimension")
}