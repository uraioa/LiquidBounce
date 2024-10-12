/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.player

import net.ccbluex.liquidbounce.event.EventTarget
import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.utils.block.BlockUtils.getBlock
import net.ccbluex.liquidbounce.utils.block.BlockUtils.isReplaceable
import net.ccbluex.liquidbounce.utils.misc.RandomUtils.nextFloat
import net.ccbluex.liquidbounce.utils.timing.MSTimer
import net.ccbluex.liquidbounce.value.BoolValue
import net.ccbluex.liquidbounce.value.FloatValue
import net.ccbluex.liquidbounce.value.IntegerValue
import net.minecraft.client.gui.Gui
import net.minecraft.client.settings.GameSettings
import net.minecraft.init.Blocks.air
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import kotlin.math.abs

object Eagle : Module("Eagle", Category.PLAYER, hideModule = false) {


    private val maxEdgeDistance: FloatValue = object : FloatValue("MaxEdgeDistance", 0f, 0f..0.5f) {
        override fun onChange(oldValue: Float, newValue: Float) = newValue.coerceAtLeast(minEdgeDistance.get())
    }
    private val minEdgeDistance: FloatValue = object : FloatValue("MinEdgeDistance", 0f, 0f..0.5f) {
        override fun onChange(oldValue: Float, newValue: Float) = newValue.coerceAtMost(maxEdgeDistance.get())
    }
    var eagleSneaking = false

    //private var edgeDistance = nextFloat(minEdgeDistance.get(), maxEdgeDistance.get())

    @EventTarget
    fun onUpdate(event: UpdateEvent) {
        val thePlayer = mc.thePlayer ?: return

        val should = shouldSneak()
        
        mc.gameSettings.keyBindSneak.pressed = should
        eagleSneaking = should

    }

    fun shouldSneak(): Boolean {
        if (mc.thePlayer == null) {
            return false
        }
        if (!mc.thePlayer.onGround) {
            return true
        }
        var dif = 0.5
        val blockPos = BlockPos(mc.thePlayer).down()
        
        for (side in EnumFacing.values()) {
            if (side.axis == EnumFacing.Axis.Y) {
                continue
            }
        
            val neighbor = blockPos.offset(side)

            if (isReplaceable(neighbor)) {
                val calcDif = (if (side.axis == EnumFacing.Axis.Z) {
                    abs(neighbor.z + 0.5 - mc.thePlayer.posZ)
                } else {
                    abs(neighbor.x + 0.5 - mc.thePlayer.posX)
                }) - 0.5

                if (calcDif < dif) {
                    dif = calcDif
                }
                
            }
        }
        val shouldEagle = isReplaceable(blockPos) || dif < minEdgeDistance.get()
        return shouldEagle
    }

    override fun onDisable() {
        if (mc.thePlayer == null)
            return

        if (!GameSettings.isKeyDown(mc.gameSettings.keyBindSneak)) {
            mc.gameSettings.keyBindSneak.pressed = false
            if (eagleSneaking && mc.thePlayer.isSneaking) {
                mc.thePlayer.isSneaking = false
            }
        }
    }
}
