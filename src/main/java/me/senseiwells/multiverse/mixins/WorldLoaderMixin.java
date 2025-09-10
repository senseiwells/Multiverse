package me.senseiwells.multiverse.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import me.senseiwells.multiverse.utils.MultiverseRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.world.level.dimension.LevelStem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

@Mixin(WorldLoader.class)
public class WorldLoaderMixin {
    // We can't use fabric's dynamic registries because it registers
    // dynamic registries along with the other world-gen registries,
    // whereas we need to register this later after level stems.
    @ModifyArg(
        method = "load",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/LayeredRegistryAccess;replaceFrom(Ljava/lang/Object;[Lnet/minecraft/core/RegistryAccess$Frozen;)Lnet/minecraft/core/LayeredRegistryAccess;"
        ),
        index = 1
    )
    private static RegistryAccess.Frozen[] addExtraRegistry(
        RegistryAccess.Frozen[] values,
        @Local CloseableResourceManager manager,
        @Local(ordinal = 2) List<HolderLookup.RegistryLookup<?>> lookups,
        @Local WorldLoader.DataLoadOutput<?> output
    ) {
        List<HolderLookup.RegistryLookup<?>> updated = Stream.concat(
            lookups.stream(), output.finalDimensions().listRegistries()
        ).toList();
        RegistryAccess.Frozen[] copy = Arrays.copyOf(values, values.length + 1);
        copy[values.length] = RegistryDataLoader.load(manager, updated, List.of(
            new RegistryDataLoader.RegistryData<>(MultiverseRegistries.LEVEL_STEM, LevelStem.CODEC, false)
        ));
        return copy;
    }
}
