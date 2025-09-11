package me.senseiwells.multiverse.utils

import me.senseiwells.multiverse.Multiverse
import net.casual.arcade.utils.ResourceLocation
import net.minecraft.resources.ResourceLocation

fun multiverse(path: String): ResourceLocation {
    return ResourceLocation(Multiverse.MOD_ID,  path)
}