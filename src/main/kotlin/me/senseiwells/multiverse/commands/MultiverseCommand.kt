package me.senseiwells.multiverse.commands

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import kotlinx.io.IOException
import me.lucko.fabric.api.permissions.v0.Permissions
import me.senseiwells.multiverse.Multiverse
import me.senseiwells.multiverse.commands.argument.SeedArgument
import me.senseiwells.multiverse.utils.MultiverseRegistries
import net.casual.arcade.commands.*
import net.casual.arcade.commands.arguments.RegistryElementArgument
import net.casual.arcade.dimensions.level.CustomLevel
import net.casual.arcade.dimensions.level.LevelPersistence
import net.casual.arcade.dimensions.level.vanilla.VanillaDimension
import net.casual.arcade.dimensions.level.vanilla.VanillaLikeLevelsBuilder
import net.casual.arcade.dimensions.utils.addCustomLevel
import net.casual.arcade.dimensions.utils.deleteCustomLevel
import net.casual.arcade.dimensions.utils.getDimensionPath
import net.casual.arcade.utils.component.*
import net.casual.arcade.utils.math.location.LocationWithLevel.Companion.asLocation
import net.casual.arcade.utils.teleportTo
import net.casual.arcade.utils.toIdString
import net.casual.arcade.utils.toKey
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.DimensionArgument
import net.minecraft.commands.arguments.ResourceLocationArgument
import net.minecraft.commands.arguments.coordinates.Vec2Argument
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.world.level.dimension.LevelStem
import net.minecraft.world.level.levelgen.FlatLevelSource
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import java.util.concurrent.CompletableFuture
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.jvm.optionals.getOrNull

object MultiverseCommand: CommandTree {
    private val DIMENSION_ALREADY_EXISTS = DynamicCommandExceptionType { dim ->
        Component.literal("The dimension $dim already exists")
    }
    private val CANNOT_DELETE_DIMENSION = DynamicCommandExceptionType { dim ->
        Component.literal("Cannot delete non-custom dimension $dim")
    }

    override fun create(buildContext: CommandBuildContext): LiteralArgumentBuilder<CommandSourceStack> {
        return CommandTree.buildLiteral("multiverse") {
            requires { Permissions.check(it, "multiverse.commands.multiverse", 2) }
            literal("create") {
                requires { Permissions.check(it, "multiverse.commands.multiverse.create", 2) }
                literal("from") {
                    argument("stem", RegistryElementArgument.element(MultiverseRegistries.LEVEL_STEM)) {
                        argument("dimension", ResourceLocationArgument.id()) {
                            executes { createStemmedDimension(it, 0, false) }
                            argument("seed", SeedArgument.seed()) {
                                executes { createStemmedDimension(it, hasCustomGamerules = false) }
                                argument("has-custom-gamerules", BoolArgumentType.bool()) {
                                    executes(::createStemmedDimension)
                                }
                            }
                        }
                    }
                }
                literal("vanilla") {
                    argument("overworld-dimension", ResourceLocationArgument.id()) {
                        argument("nether-dimension", ResourceLocationArgument.id()) {
                            argument("end-dimension", ResourceLocationArgument.id()) {
                                executes { createVanillaDimensions(it, 0) }
                                argument("seed", SeedArgument.seed()) {
                                    executes(::createVanillaDimensions)
                                }
                            }
                        }
                    }
                }
            }
            literal("clone") {
                requires { Permissions.check(it, "multiverse.commands.multiverse.clone", 2) }
                argument("from", DimensionArgument.dimension()) {
                    argument("to", ResourceLocationArgument.id()) {
                        executes(::cloneDimension)
                    }
                }
            }
            literal("delete") {
                requires { Permissions.check(it, "multiverse.commands.multiverse.delete", 2) }
                argument("dimension", DimensionArgument.dimension()) {
                    suggests(::suggestCustomDimensions)
                    executes(::deleteCustomDimension)
                    literal("force") {
                        executes { deleteCustomDimension(it, true) }
                    }
                }
            }
            literal("teleport") {
                requires { Permissions.check(it, "multiverse.commands.multiverse.teleport", 2) }
                argument("dimension", DimensionArgument.dimension()) {
                    executes { teleportToCustomDimension(it, it.source.position, it.source.rotation) }
                    argument("position", Vec3Argument.vec3()) {
                        executes { teleportToCustomDimension(it, rotation = it.source.rotation) }
                        argument("rotation", Vec2Argument.vec2()) {
                            executes(::teleportToCustomDimension)
                        }
                    }
                }
            }
        }
    }

    private fun createStemmedDimension(
        context: CommandContext<CommandSourceStack>,
        seed: Long = SeedArgument.getSeed(context, "seed"),
        hasCustomGamerules: Boolean = BoolArgumentType.getBool(context, "has-custom-gamerules")
    ): Int {
        val server = context.source.server
        val stem = this.getStemHolder(context, "stem")
        val dimension = ResourceLocationArgument.getId(context, "dimension").toKey(Registries.DIMENSION)
        if (server.levelKeys().contains(dimension)) {
            throw DIMENSION_ALREADY_EXISTS.create(dimension.location())
        }

        server.addCustomLevel {
            dimensionKey(dimension)
            levelStem(stem)
            persistence(LevelPersistence.Persistent)
            seed(seed)
            if (hasCustomGamerules) {
                gameRules { }
            }
            if (stem.value().generator is FlatLevelSource) {
                flat(true)
            }
        }
        val id = dimension.toIdString()
        val message = Component {
            literal("Successfully created custom dimension $id") + nl +
                literal("[Click to teleport]").suggestCommand("/multiverse teleport $id ~ ~ ~").yellow()
        }
        return context.source.success(message)
    }

    private fun createVanillaDimensions(
        context: CommandContext<CommandSourceStack>,
        seed: Long = SeedArgument.getSeed(context, "seed"),
    ): Int {
        val overworldKey = ResourceLocationArgument.getId(context, "overworld-dimension").toKey(Registries.DIMENSION)
        val netherKey = ResourceLocationArgument.getId(context, "nether-dimension").toKey(Registries.DIMENSION)
        val endKey = ResourceLocationArgument.getId(context, "end-dimension").toKey(Registries.DIMENSION)
        val keys = VanillaDimension.entries.zip(listOf(overworldKey, netherKey, endKey))

        val server = context.source.server
        for ((_, key) in keys) {
            if (server.levelKeys().contains(key)) {
                throw DIMENSION_ALREADY_EXISTS.create(key.location())
            }
        }

        val levels = VanillaLikeLevelsBuilder.build(server) {
            for ((dimension, key) in keys) {
                set(dimension) {
                    dimensionKey(key)
                    seed(seed)
                    persistence(LevelPersistence.Persistent)
                }
            }
        }
        for (level in levels.all()) {
            server.addCustomLevel(level)
        }

        val message = Component {
            literal("Successfully created custom dimensions") + nl + keys.joinToComponent(nl) { (dim, key) ->
                val command = "/multiverse teleport ${key.location()} ~ ~ ~"
                Component.literal("[Click to teleport to $dim]").suggestCommand(command).yellow()
            }
        }
        return context.source.success(message)
    }

    private fun cloneDimension(context: CommandContext<CommandSourceStack>): Int {
        val level = DimensionArgument.getDimension(context, "from")
        val destination = ResourceLocationArgument.getId(context, "to").toKey(Registries.DIMENSION)

        val server = context.source.server
        if (server.levelKeys().contains(destination)) {
            throw DIMENSION_ALREADY_EXISTS.create(destination.location())
        }

        val from = server.getDimensionPath(level.dimension())
        val to = server.getDimensionPath(destination)

        val directoriesToCopy = listOf("data", "entities", "poi", "region")
        @OptIn(ExperimentalPathApi::class)
        try {
            for (directory in directoriesToCopy) {
                val source = from.resolve(directory)
                if (source.exists()) {
                    val dest = to.resolve(directory).createParentDirectories()
                    source.copyToRecursively(dest, followLinks = true, overwrite = true)
                }
            }
        } catch (e: IOException) {
            Multiverse.logger.error("Failed to copy dimension from $from to $to", e)
            return context.source.fail("Failed to clone dimension, see logs for more info...")
        }

        server.addCustomLevel {
            dimensionKey(destination)
            dimensionType(level.dimensionTypeRegistration())
            chunkGenerator(level.chunkSource.generator)
            gameRules(level.gameRules.copy(level.enabledFeatures()))
            seed(level.seed)
            flat(level.isFlat)
            persistence(LevelPersistence.Persistent)
        }

        val id = destination.toIdString()
        val message = Component {
            literal("Successfully cloned dimension ${level.dimension().toIdString()} into $id") + nl +
                literal("[Click to teleport]").suggestCommand("/multiverse teleport $id ~ ~ ~").yellow()
        }
        return context.source.success(message)
    }

    private fun deleteCustomDimension(
        context: CommandContext<CommandSourceStack>,
        forced: Boolean = false
    ): Int {
        val level = DimensionArgument.getDimension(context, "dimension")
        val dimension = level.dimension()
        if (level !is CustomLevel) {
            throw CANNOT_DELETE_DIMENSION.create(dimension.location())
        }

        if (!forced) {
            val message = Component {
                literal("Are you sure you want to delete this dimension? This action ") +
                    literal("cannot").italicize().red() + literal(" be undone") + nl +
                    literal("[Click to confirm deletion]").suggestCommand("/multiverse delete ${dimension.toIdString()} force").yellow()
            }
            return context.source.success(message)
        }

        if (context.source.server.deleteCustomLevel(level)) {
            return context.source.success("Successfully deleted dimension ${dimension.location()}")
        }
        return context.source.fail("Failed to delete dimension ${dimension.location()}")
    }

    private fun teleportToCustomDimension(
        context: CommandContext<CommandSourceStack>,
        position: Vec3 = Vec3Argument.getVec3(context, "position"),
        rotation: Vec2 = Vec2Argument.getVec2(context, "rotation")
    ): Int {
        val level = DimensionArgument.getDimension(context, "dimension")
        val location = level.asLocation(position, rotation)
        context.source.entityOrException.teleportTo(location)
        return context.source.success("Successfully teleported to ${level.dimension().location()}")
    }

    private fun suggestCustomDimensions(
        context: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val dimensions = context.source.server.allLevels.filterIsInstance<CustomLevel>()
            .map { level -> level.dimension().location() }
        return SharedSuggestionProvider.suggestResource(dimensions, builder)
    }

    @Suppress("SameParameterValue")
    private fun getStemHolder(context: CommandContext<CommandSourceStack>, name: String): Holder<LevelStem> {
        // What we're essentially doing here is replacing the holder from the multiverse
        // registries to a holder from the vanilla registries.
        // We get reference holders to those who exist in the vanilla registries
        // and provide direct holders for those which are not.
        val stem = RegistryElementArgument.getHolder<LevelStem>(context, name)
        val access = context.source.registryAccess()
        val stems = access.lookupOrThrow(Registries.LEVEL_STEM)
        val key = stems.getResourceKey(stem.value()).getOrNull() ?: return Holder.direct(stem.value())
        return stems.getOrThrow(key)
    }
}