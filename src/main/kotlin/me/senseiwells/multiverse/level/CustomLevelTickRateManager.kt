package me.senseiwells.multiverse.level

import net.casual.arcade.dimensions.level.CustomLevel
import net.casual.arcade.utils.PlayerUtils.broadcast
import net.minecraft.network.protocol.game.ClientboundTickingStatePacket
import net.minecraft.network.protocol.game.ClientboundTickingStepPacket
import net.minecraft.server.ServerTickRateManager
import net.minecraft.util.TimeUtil
import kotlin.math.max

class CustomLevelTickRateManager(
    private val level: CustomLevel
): ServerTickRateManager(level.server) {
    override fun setTickRate(tickrate: Float) {
        this.tickrate = max(tickrate, 1.0F)
        this.nanosecondsPerTick = (TimeUtil.NANOSECONDS_PER_SECOND.toDouble() / this.tickrate).toLong()
        this.updateStateToClients()
    }

    override fun setFrozen(frozen: Boolean) {
        this.isFrozen = frozen
        this.updateStateToClients()
    }

    override fun stepGameIfPaused(ticks: Int): Boolean {
        if (this.isFrozen()) {
            this.frozenTicksToRun = ticks
            this.updateStepTicks()
            return true
        }
        return false
    }

    override fun stopStepping(): Boolean {
        if (this.frozenTicksToRun > 0) {
            this.frozenTicksToRun = 0
            this.updateStepTicks()
            return true
        }
        return false
    }

    private fun updateStateToClients() {
        this.level.players().broadcast(ClientboundTickingStatePacket.from(this))
    }

    private fun updateStepTicks() {
        this.level.players().broadcast(ClientboundTickingStepPacket.from(this))
    }
}