package mobility.domain

import kotlin.math.sqrt

data class Vector2D(
    val x: Float = 0f,
    val y: Float = 0f
) {
    companion object {
        val Zero = Vector2D(0f, 0f)
    }

    fun getDistance(): Float {
        return sqrt(x * x + y * y)
    }

    fun getDistanceSquared(): Float {
        return x * x + y * y
    }

    operator fun plus(other: Vector2D): Vector2D {
        return Vector2D(x + other.x, y + other.y)
    }

    operator fun minus(other: Vector2D): Vector2D {
        return Vector2D(x - other.x, y - other.y)
    }

    operator fun times(scalar: Float): Vector2D {
        return Vector2D(x * scalar, y * scalar)
    }

    operator fun div(scalar: Float): Vector2D {
        return Vector2D(x / scalar, y / scalar)
    }
}