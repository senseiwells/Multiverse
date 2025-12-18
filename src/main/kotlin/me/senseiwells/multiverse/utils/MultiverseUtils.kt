package me.senseiwells.multiverse.utils

import me.senseiwells.multiverse.Multiverse
import net.casual.arcade.utils.Identifier
import net.minecraft.resources.Identifier

fun multiverse(path: String): Identifier {
    return Identifier(Multiverse.MOD_ID,  path)
}