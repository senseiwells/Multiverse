package me.senseiwells.multiverse.commands.argument

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.casual.arcade.commands.type.CustomArgumentType
import net.casual.arcade.commands.type.CustomArgumentTypeInfo
import net.casual.arcade.commands.type.CustomStringArgumentInfo
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import net.minecraft.world.level.levelgen.WorldOptions
import java.util.OptionalLong
import java.util.concurrent.CompletableFuture

class SeedArgument: CustomArgumentType<OptionalLong>() {
    override fun parse(reader: StringReader): OptionalLong {
        val string = reader.readUnquotedString()
        if (string == "random") {
            return OptionalLong.empty()
        }
        val seed = string.toLongOrNull() ?: throw INVALID_SEED.create()
        return OptionalLong.of(seed)
    }

    override fun <S> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        return SharedSuggestionProvider.suggest(listOf("random"), builder)
    }

    override fun getArgumentInfo(): CustomArgumentTypeInfo<*> {
        return CustomStringArgumentInfo(StringArgumentType.StringType.SINGLE_WORD)
    }

    companion object {
        private val INVALID_SEED = SimpleCommandExceptionType(Component.literal("Invalid seed"))

        fun seed(): SeedArgument {
            return SeedArgument()
        }

        fun getSeed(context: CommandContext<*>, name: String): Long {
            return context.getArgument(name, OptionalLong::class.java)
                .orElseGet(WorldOptions::randomSeed)
        }
    }
}