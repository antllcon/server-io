package mobility.domain

import domain.Car
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin


data class CollisionResult(
    val areColliding: Boolean,
    val penetrationVector: Vector2D
)

fun Car.getVelocity(): Vector2D {
    return Vector2D(cos(visualDirection) * speed, sin(visualDirection) * speed)
}

fun Car.withNewPhysicsState(position: Vector2D, velocity: Vector2D): Car {
    val newSpeed = velocity.magnitude()
    val newDirection = if (newSpeed > 0.001f) atan2(velocity.y, velocity.x) else this.direction

    return this.copy(
        position = position,
        speed = newSpeed.coerceIn(Car.MIN_SPEED, Car.MAX_SPEED),
        direction = newDirection,
        visualDirection = newDirection
    )
}

data class Projection(
    val min: Float,
    val max: Float
)