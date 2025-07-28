package domain

import mobility.domain.Vector2D
import org.slf4j.LoggerFactory
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log
import kotlin.math.sin

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
    private val logger = LoggerFactory.getLogger(Car::class.java)

    companion object {
        const val MIN_SPEED = 0f
        const val MAX_SPEED = 2f
        const val ACCELERATION = 0.02f
        const val DECELERATION = 0.2f
        const val LENGTH = 0.203f
        const val WIDTH = 0.30f
        const val MAP_SIZE = 13f
        const val MAX_DIRECTION_CHANGE = 0.09f
        const val VISUAL_LAG_SPEED = 0.05f
        const val DEFAULT_SPRITE_CHANGE_DISTANCE = 0.01f
        const val DIRECTION_RIGHT = 0f
        const val DIRECTION_DOWN = (PI / 2).toFloat()
        const val DIRECTION_LEFT = PI.toFloat()
        const val DIRECTION_UP = (3 * PI / 2).toFloat()
    }

    fun update(elapsedTime: Float, directionAngle: Float?, speedModifier: Float): Car {
        return copy(
            direction = directionAngle ?: this.direction,
            position = updatePosition(elapsedTime),
            speed = updateSpeed(directionAngle),
            speedModifier = setSpeedModifier(speedModifier),
            visualDirection = updateVisualDirection(),
            currentSprite = updateCurrentSprite()
        )
    }

    fun setNewPosition(newPosition: Vector2D): Car {
        return copy(
            position = newPosition
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

        logger.info(actualMove.toString())

        val halfCarLength = LENGTH / 2

        val newPosition = Vector2D(
            x = (position.x + actualMove * cos(visualDirection)),
            y = (position.y + actualMove * sin(visualDirection))
        )

        val clampedPosition = Vector2D(
            x = newPosition.x.coerceIn(halfCarLength, MAP_SIZE - halfCarLength),
            y = newPosition.y.coerceIn(halfCarLength, MAP_SIZE - halfCarLength)
        )

        distanceBeforeSpriteChange -= moveDistance
        return clampedPosition
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