package me.senseiwells.multiverse.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import me.senseiwells.multiverse.utils.MultiverseRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryValidator;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.world.level.dimension.LevelStem;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.stream.Stream;

@Mixin(WorldLoader.class)
public class WorldLoaderMixin {
    // Okay, so this is a bit complicated because of how Minecraft loads the
    // vanilla LevelStem registry.
    // It first loads all the stems from datapacks in lambda$load$1, then
    // it composes the result and loads all the pre-existing stems that
    // are part of its level data. We want *all* level stems to be registered
    // when we register our stems, so we need to mixin into this weird spot.
    @ModifyExpressionValue(
        method = "lambda$load$2",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/server/WorldLoader$DataLoadOutput;finalDimensions:Lnet/minecraft/core/RegistryAccess$Frozen;",
            opcode = Opcodes.GETFIELD
        )
    )
    private static RegistryAccess.Frozen loadMultiverseRegistries(
        RegistryAccess.Frozen vanillaStemRegistries,
        @Local(name = "resources") CloseableResourceManager manager,
        @Local(name = "dimensionContextProvider") HolderLookup.Provider dimensionContextProvider
    ) {
        List<HolderLookup.RegistryLookup<?>> lookup = Stream.concat(
            dimensionContextProvider.listRegistries(), vanillaStemRegistries.listRegistries()
        ).toList();
        RegistryAccess.Frozen multiverseStemRegistries = RegistryDataLoader.load(manager, lookup, List.of(
            new RegistryDataLoader.RegistryData<>(MultiverseRegistries.LEVEL_STEM, LevelStem.CODEC, RegistryValidator.none())
        ), Runnable::run).join();
        return new RegistryAccess.ImmutableRegistryAccess(
            Stream.concat(vanillaStemRegistries.registries(), multiverseStemRegistries.registries())
        ).freeze();
    }
}
