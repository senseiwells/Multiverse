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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

@Mixin(WorldLoader.class)
public class WorldLoaderMixin {
    @ModifyExpressionValue(
        method = "lambda$load$1",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/resources/RegistryDataLoader;load(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/List;Ljava/util/List;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
        )
    )
    private static CompletableFuture<RegistryAccess.Frozen> loadMultiverseRegistries(
        CompletableFuture<RegistryAccess.Frozen> original,
        @Local(name = "resources") CloseableResourceManager manager,
        @Local(name = "dimensionContextRegistries") List<HolderLookup.RegistryLookup<?>> dimensionContextRegistries,
        @Local(name = "backgroundExecutor") Executor backgroundExecutor
    ) {
        return original.thenComposeAsync(dimensionRegistries -> {
            List<HolderLookup.RegistryLookup<?>> lookup = Stream.concat(
                dimensionContextRegistries.stream(), dimensionRegistries.listRegistries()
            ).toList();
            return RegistryDataLoader.load(manager, lookup, List.of(
                new RegistryDataLoader.RegistryData<>(MultiverseRegistries.LEVEL_STEM, LevelStem.CODEC, RegistryValidator.none())
            ), backgroundExecutor).thenApplyAsync(multiverseRegistries -> {
                return new RegistryAccess.ImmutableRegistryAccess(
                    Stream.concat(dimensionRegistries.registries(), multiverseRegistries.registries())
                ).freeze();
            }, backgroundExecutor);
        });
    }
}
