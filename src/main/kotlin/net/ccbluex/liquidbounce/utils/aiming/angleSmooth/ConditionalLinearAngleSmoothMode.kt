/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2024 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */

package net.ccbluex.liquidbounce.utils.aiming.angleSmooth

import net.ccbluex.liquidbounce.config.ChoiceConfigurable
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.features.NotifyWhenFail.failedHitsIncrement
import net.ccbluex.liquidbounce.utils.aiming.Rotation
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.facingEnemy
import net.ccbluex.liquidbounce.utils.kotlin.random
import net.minecraft.entity.Entity
import net.minecraft.util.math.Vec3d
import kotlin.math.*

class ConditionalLinearAngleSmoothMode(override val parent: ChoiceConfigurable<*>)
    : AngleSmoothMode("Conditional") {

    private val coefDistance by float("CoefDistance", -1.393f, -2f..2f)
    private val coefDiffH by float("CoefDiffH", 0.21f, -1f..1f)
    private val coefDiffV by float("CoefDiffV", 0.14f, -1f..1f)
    private val coefCrosshairH by float("CoefCrosshairH", -5.99f, -30f..30f)
    private val coefCrosshairV by float("CoefCrosshairV", -14.32f, -30f..30f)
    private val interceptH by float("InterceptH", 11.988f, 0f..20f)
    private val interceptV by float("InterceptV", 4.715f, 0f..10f)
    private val minimumTurnSpeedH by float("MinimumTurnSpeedH", 3.05e-5f, 0f..10f)
    private val minimumTurnSpeedV by float("MinimumTurnSpeedV", 5.96e-8f, 0f..10f)

    private val notVisibleFactorH by float("NotVisibleH", 0.3f, 0.0f..1f)
    private val notVisibleFactorV by float("NotVisibleV", 0.8f, 0.0f..1f)

    /**
     * Only applies for KillAura
     */
    private val failCap by int("FailCap", 3, 1..40)
    private val failIncrementH by float("FailIncrementH", 0f, 0.0f..10f)
    private val failIncrementV by float("FailIncrementV", 0f, 0.0f..10f)

    private var easeInStart by float("EaseInStartThreshold", 0.0f, 0.0f..2.0f)
    private var easeInRatio by floatRange("EaseInRatio", 0.1f..0.4f, 0.0f..2.0f)

    private var bezierInterpolation by boolean("BezierInterpolation", true)

    override fun limitAngleChange(currentRotation: Rotation, targetRotation: Rotation,
                                  vec3d: Vec3d?,
                                  entity: Entity?): Rotation {
        val distance = vec3d?.distanceTo(player.pos) ?: 0.0
        val crosshair = entity?.let { facingEnemy(entity, max(3.0, distance), currentRotation) } ?: false

        val yawDifference = RotationManager.angleDifference(targetRotation.yaw, currentRotation.yaw)
        val pitchDifference = RotationManager.angleDifference(targetRotation.pitch, currentRotation.pitch)

        val rotationDifference = hypot(abs(yawDifference), abs(pitchDifference))
        var (factorH, factorV) = computeTurnSpeed(
            distance.toFloat(),
            abs(yawDifference),
            abs(pitchDifference),
            crosshair,
        )

        val recentYawDifference = abs(RotationManager.angleDifference(currentRotation.yaw,
            RotationManager.previousServerRotation.yaw))
        val recentPitchDifference = abs(RotationManager.angleDifference(currentRotation.pitch,
            RotationManager.previousServerRotation.pitch))
        if (recentYawDifference < easeInStart) {
            factorH *= easeInRatio.random().toFloat()
        }
        if (recentPitchDifference < easeInStart) {
            factorV *= easeInRatio.random().toFloat()
        }

        if (abs(yawDifference) > 75) {
            factorH *= notVisibleFactorH
            factorV *= notVisibleFactorV
        }

        val straightLineYaw = max(abs(yawDifference / rotationDifference) * factorH,
            minimumTurnSpeedH)
        val straightLinePitch = max((abs(pitchDifference / rotationDifference) * factorV),
            minimumTurnSpeedV)

        val speed = (factorH + factorV) / 2
        val t = ((rotationDifference.coerceIn(-speed, speed) / 60f) % 1f)
        val control = (targetRotation.pitch * 2).coerceIn(-90f, 90f)

        val interpolatedPitch = if (bezierInterpolation && abs(pitchDifference) > 1) {
            bezierInterpolate(currentRotation.pitch, control, targetRotation.pitch, 1 - t)
                .coerceIn(-90f, 90f)
        } else {
            currentRotation.pitch + pitchDifference.coerceIn(-straightLinePitch, straightLinePitch)
        }

        return Rotation(
            currentRotation.yaw + yawDifference.coerceIn(-straightLineYaw, straightLineYaw),
            interpolatedPitch
        )
    }

    override fun howLongToReach(currentRotation: Rotation, targetRotation: Rotation): Int {
        val yawDifference = RotationManager.angleDifference(targetRotation.yaw, currentRotation.yaw)
        val pitchDifference = RotationManager.angleDifference(targetRotation.pitch, currentRotation.pitch)

        val (computedH, computedV) = computeTurnSpeed(0f, yawDifference, pitchDifference,
            false)
        val lowest = min(computedH, computedV)

        if (lowest <= 0.0) {
            return 0
        }

        if (yawDifference == 0f && pitchDifference == 0f) {
            return 0
        }

        return (hypot(abs(yawDifference), abs(pitchDifference)) / lowest).roundToInt()
    }

    private fun computeTurnSpeed(distance: Float, diffH: Float, diffV: Float, crosshair: Boolean): Pair<Float, Float> {
        val turnSpeedH = coefDistance * distance + coefDiffH * diffH +
            (if (crosshair) coefCrosshairH else 0f) + interceptH + (failIncrementH * min(failCap, failedHitsIncrement))
        val turnSpeedV = coefDistance * distance + coefDiffV * max(0f, diffV - diffH) +
            (if (crosshair) coefCrosshairV else 0f) + interceptV + (failIncrementV * min(failCap, failedHitsIncrement))
        return Pair(max(abs(turnSpeedH), minimumTurnSpeedH), max(abs(turnSpeedV), minimumTurnSpeedV))
    }

    private fun bezierInterpolate(start: Float, control: Float, end: Float, t: Float): Float {
        return (1 - t) * (1 - t) * start + 2 * (1 - t) * t * control + t * t * end
    }

}
