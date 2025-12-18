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
import me.senseiwells.multiverse.level.TickManagedCustomLevelFactory
import me.senseiwells.multiverse.utils.MultiverseRegistries
import net.casual.arcade.commands.*
import net.casual.arcade.commands.arguments.RegionPosArgument
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
import net.minecraft.commands.arguments.IdentifierArgument
import net.minecraft.commands.arguments.coordinates.Vec2Argument
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.server.permissions.PermissionLevel
import net.minecraft.world.level.dimension.LevelStem
import net.minecraft.world.level.levelgen.FlatLevelSource
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import org.joml.Vector2i
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.io.path.*
import kotlin.jvm.optionals.getOrNull
import kotlin.math.max
import kotlin.math.min

object MultiverseCommand: CommandTree {
    private val DIMENSION_ALREADY_EXISTS = DynamicCommandExceptionType { dim ->
        Component.literal("The dimension $dim already exists")
    }
    private val CANNOT_DELETE_DIMENSION = DynamicCommandExceptionType { dim ->
        Component.literal("Cannot delete non-custom dimension $dim")
    }

    private val REGION_DIRECTORIES = listOf("entities", "poi", "region")

    override fun create(buildContext: CommandBuildContext): LiteralArgumentBuilder<CommandSourceStack> {
        return CommandTree.buildLiteral("multiverse") {
            requires { Permissions.check(it, "multiverse.commands.multiverse", PermissionLevel.GAMEMASTERS) }
            literal("create") {
                requires { Permissions.check(it, "multiverse.commands.multiverse.create", true) }
                literal("from") {
                    argument("stem", RegistryElementArgument.element(MultiverseRegistries.LEVEL_STEM)) {
                        argument("dimension", IdentifierArgument.id()) {
                            executes { createStemmedDimension(it, 0, hasCustomGamerules = false, hasCustomTickManager = false) }
                            argument("seed", SeedArgument.seed()) {
                                executes { createStemmedDimension(it, hasCustomGamerules = false, hasCustomTickManager = false) }
                                argument("has-custom-gamerules", BoolArgumentType.bool()) {
                                    executes { createStemmedDimension(it, hasCustomTickManager = false) }
                                    argument("has-custom-tickrate", BoolArgumentType.bool()) {
                                        executes(::createStemmedDimension)
                                    }
                                }
                            }
                        }
                    }
                }
                literal("vanilla") {
                    argument("overworld-dimension", IdentifierArgument.id()) {
                        argument("nether-dimension", IdentifierArgument.id()) {
                            argument("end-dimension", IdentifierArgument.id()) {
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
                requires { Permissions.check(it, "multiverse.commands.multiverse.clone", true) }
                argument("from", DimensionArgument.dimension()) {
                    argument("to", IdentifierArgument.id()) {
                        executes { cloneDimension(it, false, null, null) }
                        argument("has-custom-tickrate", BoolArgumentType.bool()) {
                            executes { cloneDimension(it, fromRegion = null, toRegion = null) }
                            argument("region-from", RegionPosArgument.region()) {
                                executes { cloneDimension(it, toRegion = null) }
                                argument("region-to", RegionPosArgument.region()) {
                                    executes(::cloneDimension)
                                }
                            }
                        }
                    }
                }
            }
            literal("delete") {
                requires { Permissions.check(it, "multiverse.commands.multiverse.delete", true) }
                argument("dimension", DimensionArgument.dimension()) {
                    suggests(::suggestCustomDimensions)
                    executes(::deleteCustomDimension)
                    literal("force") {
                        executes { deleteCustomDimension(it, true) }
                    }
                }
            }
            literal("teleport") {
                requires { Permissions.check(it, "multiverse.commands.multiverse.teleport", true) }
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
        hasCustomGamerules: Boolean = BoolArgumentType.getBool(context, "has-custom-gamerules"),
        hasCustomTickManager: Boolean = BoolArgumentType.getBool(context, "has-custom-tickrate")
    ): Int {
        val server = context.source.server
        val stem = this.getStemHolder(context, "stem")
        val dimension = IdentifierArgument.getId(context, "dimension").toKey(Registries.DIMENSION)
        if (server.levelKeys().contains(dimension)) {
            throw DIMENSION_ALREADY_EXISTS.create(dimension.toIdString())
        }

        server.addCustomLevel {
            dimensionKey(dimension)
            levelStem(stem)
            persistence(LevelPersistence.Persistent)
            seed(seed)
            timeOfDay(0L)
            tickTime(true)
            if (hasCustomGamerules) {
                gameRules { }
            }
            if (hasCustomTickManager) {
                constructor(::TickManagedCustomLevelFactory)
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
        val overworldKey = IdentifierArgument.getId(context, "overworld-dimension").toKey(Registries.DIMENSION)
        val netherKey = IdentifierArgument.getId(context, "nether-dimension").toKey(Registries.DIMENSION)
        val endKey = IdentifierArgument.getId(context, "end-dimension").toKey(Registries.DIMENSION)
        val keys = VanillaDimension.entries.zip(listOf(overworldKey, netherKey, endKey))

        val server = context.source.server
        for ((_, key) in keys) {
            if (server.levelKeys().contains(key)) {
                throw DIMENSION_ALREADY_EXISTS.create(key.toIdString())
            }
        }

        val levels = VanillaLikeLevelsBuilder.build(server) {
            for ((dimension, key) in keys) {
                set(dimension) {
                    dimensionKey(key)
                    seed(seed)
                    persistence(LevelPersistence.Persistent)
                    timeOfDay(0L)
                    tickTime(dimension == VanillaDimension.Overworld)
                }
            }
        }
        for (level in levels.all()) {
            server.addCustomLevel(level)
        }

        val message = Component {
            literal("Successfully created custom dimensions") + nl + keys.joinToComponent(nl) { (dim, key) ->
                val command = "/multiverse teleport ${key.identifier()} ~ ~ ~"
                Component.literal("[Click to teleport to $dim]").suggestCommand(command).yellow()
            }
        }
        return context.source.success(message)
    }

    private fun cloneDimension(
        context: CommandContext<CommandSourceStack>,
        hasCustomTickManager: Boolean = BoolArgumentType.getBool(context, "has-custom-tickrate"),
        fromRegion: Vector2i? = RegionPosArgument.getRegion(context, "region-from"),
        toRegion: Vector2i? = RegionPosArgument.getRegion(context, "region-to")
    ): Int {
        val level = DimensionArgument.getDimension(context, "from")
        val destination = IdentifierArgument.getId(context, "to").toKey(Registries.DIMENSION)

        val server = context.source.server
        if (server.levelKeys().contains(destination)) {
            throw DIMENSION_ALREADY_EXISTS.create(destination.toIdString())
        }

        val from = server.getDimensionPath(level.dimension())
        val to = server.getDimensionPath(destination)

        try {
            this.copyDimensionFiles(from, to, fromRegion, toRegion)
        } catch (e: IOException) {
            Multiverse.logger.error("Failed to copy dimension from $from to $to", e)
            return context.source.fail("Failed to clone dimension, see logs for more info...")
        }

        server.addCustomLevel {
            dimensionKey(destination)
            dimensionType(level.dimensionTypeRegistration())
            chunkGenerator(level.chunkSource.generator)
            timeOfDay(level.dayTime)
            tickTime(true)
            gameRules(level.gameRules.copy(level.enabledFeatures()))
            seed(level.seed)
            flat(level.isFlat)
            persistence(LevelPersistence.Persistent)
            if (hasCustomTickManager) {
                constructor(::TickManagedCustomLevelFactory)
            }
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
            throw CANNOT_DELETE_DIMENSION.create(dimension.toIdString())
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
            return context.source.success("Successfully deleted dimension ${dimension.identifier()}")
        }
        return context.source.fail("Failed to delete dimension ${dimension.identifier()}")
    }

    private fun teleportToCustomDimension(
        context: CommandContext<CommandSourceStack>,
        position: Vec3 = Vec3Argument.getVec3(context, "position"),
        rotation: Vec2 = Vec2Argument.getVec2(context, "rotation")
    ): Int {
        val level = DimensionArgument.getDimension(context, "dimension")
        val location = level.asLocation(position, rotation)
        context.source.entityOrException.teleportTo(location)
        return context.source.success("Successfully teleported to ${level.dimension().identifier()}")
    }

    private fun suggestCustomDimensions(
        context: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val dimensions = context.source.server.allLevels.filterIsInstance<CustomLevel>()
            .map { level -> level.dimension().identifier() }
        return SharedSuggestionProvider.suggestResource(dimensions, builder)
    }

    @OptIn(ExperimentalPathApi::class)
    private fun copyDimensionFiles(from: Path, to: Path, fromRegion: Vector2i?, toRegion: Vector2i?) {
        val source = from.resolve("data")
        if (source.exists()) {
            val dest = to.resolve("data").createParentDirectories()
            source.copyToRecursively(dest, followLinks = true, overwrite = true)
        }

        if (fromRegion == null) {
            this.copyAllRegionalDimensionalFiles(from, to)
            return
        }

        this.copyRegionalDimensionFiles(from, to, fromRegion, toRegion ?: fromRegion)
    }

    private fun copyRegionalDimensionFiles(from: Path, to: Path, fromRegion: Vector2i, toRegion: Vector2i) {
        val xs = min(fromRegion.x, toRegion.x)..max(fromRegion.x, toRegion.x)
        val zs = min(fromRegion.y, toRegion.y)..max(fromRegion.y, toRegion.y)

        for (directory in REGION_DIRECTORIES) {
            val source = from.resolve(directory)
            if (source.notExists()) {
                continue
            }

            val dest = to.resolve(directory).createDirectories()
            for (x in xs) {
                for (z in zs) {
                    val name = "r.$x.$z.mca"
                    val file = source.resolve(name)
                    if (file.exists()) {
                        file.copyTo(dest.resolve(name))
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalPathApi::class)
    private fun copyAllRegionalDimensionalFiles(from: Path, to: Path) {
        for (directory in REGION_DIRECTORIES) {
            val source = from.resolve(directory)
            if (source.exists()) {
                val dest = to.resolve(directory).createParentDirectories()
                source.copyToRecursively(dest, followLinks = false, overwrite = true)
            }
        }
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