package me.senseiwells.multiverse.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import me.senseiwells.multiverse.level.TickManagedCustomLevel;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerList.class)
public class PlayerListMixin {
    @ModifyExpressionValue(
        method = "sendLevelInfo",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;tickRateManager()Lnet/minecraft/server/ServerTickRateManager;"
        )
    )
    private ServerTickRateManager getTickRateManager(
        ServerTickRateManager original,
        @Local(argsOnly = true) ServerLevel level
    ) {
        if (level instanceof TickManagedCustomLevel custom) {
            return custom.tickRateManager();
        }
        return original;
    }
}
