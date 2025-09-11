package me.senseiwells.multiverse.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.senseiwells.multiverse.level.TickManagedCustomLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.commands.TickCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TickCommand.class)
public class TickCommandMixin {
    @ModifyExpressionValue(
        method = {"setTickingRate", "tickQuery", "sprint", "setFreeze", "step", "stopStepping", "stopSprinting"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;tickRateManager()Lnet/minecraft/server/ServerTickRateManager;"
        )
    )
    private static ServerTickRateManager getTickrateManager(ServerTickRateManager original, CommandSourceStack source) {
        if (source.getLevel() instanceof TickManagedCustomLevel level) {
            return level.tickRateManager();
        }
        return original;
    }
}
