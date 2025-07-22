package domain

import mobility.domain.Vector2D
import kotlin.math.*

data class Car(
    val playerName: String = "Player",
    val isPlayer: Boolean = true,
    val isMultiplayer: Boolean = false,
    val id: String = "1",
    val position: Vector2D = Vector2D.Zero,
    val corners: List<Vector2D> = emptyList(),
    var speed: Float = 0f,
    var direction: Float = 0f,
    var visualDirection: Float = 0f,
    val speedModifier: Float = 1f,
    val currentSprite: Int = 1,
    var distanceBeforeSpriteChange: Float = DEFAULT_SPRITE_CHANGE_DISTANCE
) {
    companion object {
        const val MIN_SPEED = 0f
        const val MAX_SPEED = 2f
        const val ACCELERATION = 0.0064f
        const val DECELERATION = 0.0096f
        const val LENGTH = 0.142f
        const val WIDTH = 0.21f
        const val MAP_SIZE = 10f
        const val MAX_DIRECTION_CHANGE = 0.09f
        const val VISUAL_LAG_SPEED = 0.05f
        const val DEFAULT_SPRITE_CHANGE_DISTANCE = 0.01f
    }

    fun update(elapsedTime: Float, directionAngle: Float?, speedModifier: Float): Car {
        return copy(
            direction = handleAnglesDiff(directionAngle),
            position = updatePosition(elapsedTime),
            speed = updateSpeed(directionAngle),
            speedModifier = setSpeedModifier(speedModifier),
            visualDirection = updateVisualDirection(),
            currentSprite = updateCurrentSprite()
        )
    }

    private fun updateCurrentSprite(): Int {
        if (distanceBeforeSpriteChange <= 0f) {
            distanceBeforeSpriteChange = DEFAULT_SPRITE_CHANGE_DISTANCE

            if (currentSprite == 4) {
                return 1
            } else {
                return currentSprite + 1
            }
        }

        return currentSprite
    }

    private fun updateSpeed(directionAngle: Float?): Float {
        if (directionAngle == null) {
            decelerate()
        } else {
            accelerate()
        }

        return speed
    }

    private fun handleAnglesDiff(newAngle: Float?): Float {
        if (newAngle != null) {
            direction = newAngle
        }
        return direction
    }

    private fun setSpeedModifier(speedModifier: Float): Float {
        return speedModifier
    }

    private fun updatePosition(deltaTime: Float): Vector2D {
        val moveDistance = speed * deltaTime * speedModifier
        val maxMove = MAP_SIZE * 0.5f
        val actualMove = moveDistance.coerceIn(-maxMove, maxMove)

        val newPosition = Vector2D(
            x = (position.x + actualMove * cos(visualDirection)),
            y = (position.y + actualMove * sin(visualDirection))
        )

        distanceBeforeSpriteChange -= moveDistance
        return newPosition
    }

    private fun decelerate() {
        if (speed > MIN_SPEED) {
            speed -= DECELERATION
        }
        if (speed < MIN_SPEED) {
            speed = MIN_SPEED
        }
    }

    private fun accelerate() {
        if (speed < MAX_SPEED) {
            speed += ACCELERATION
        }
        if (speed > MAX_SPEED) {
            speed = MAX_SPEED
        }
    }

    private fun updateVisualDirection(): Float {
        var angleDiff = direction - visualDirection

        while (angleDiff <= -PI) angleDiff += (2 * PI).toFloat()
        while (angleDiff > PI) angleDiff -= (2 * PI).toFloat()

        var directionShift =
            angleDiff * VISUAL_LAG_SPEED * (1 + speed / MAX_SPEED)

        if (abs(directionShift) > MAX_DIRECTION_CHANGE) {
            directionShift = if (directionShift > 0) {
                MAX_DIRECTION_CHANGE
            } else {
                -MAX_DIRECTION_CHANGE
            }
        }

        visualDirection += directionShift

        return visualDirection
    }
}