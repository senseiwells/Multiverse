package me.senseiwells.multiverse.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.senseiwells.multiverse.level.TickManagedCustomLevel;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerTickRateManager.class)
public class ServerTickRateManagerMixin {
    @WrapOperation(
        method = {"updateStateToClients", "updateStepTicks"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastAll(Lnet/minecraft/network/protocol/Packet;)V"
        )
    )
    private void onBroadcastState(PlayerList instance, Packet<?> packet, Operation<Void> original) {
        for (ServerPlayer player : instance.getPlayers()) {
            if (!(player.level() instanceof TickManagedCustomLevel)) {
                player.connection.send(packet);
            }
        }
    }
}
