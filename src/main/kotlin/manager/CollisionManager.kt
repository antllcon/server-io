package mobility.manager

import domain.Car
import mobility.domain.CollisionResult
import mobility.domain.Projection
import mobility.domain.Vector2D
import mobility.domain.getVelocity
import mobility.domain.withNewPhysicsState
import mobility.model.Player
import kotlin.math.cos
import kotlin.math.sin

object CollisionManager {

    private const val RESTITUTION = 1f // Коэффициент упругости (0 - нет отскока, 1 - идеальный отскок)

    fun checkAndResolveCollisions(players: List<Player>) {
        val cars: List<Car> = players.mapNotNull { if(it.isFinished) null else it.car!! }

        for (i: Int in cars.indices) {
            for (j: Int in (i + 1) until cars.size) {
                val car1: Car = cars[i]
                val car2: Car = cars[j]

                val corners1: List<Vector2D> = getCarCorners(car1)
                val corners2: List<Vector2D> = getCarCorners(car2)

                val result: CollisionResult = detectCollision(corners1, corners2, car1.position, car2.position)

                if (result.areColliding) {
                    resolveCollision(players[i], players[j], result.penetrationVector)
                }
            }
        }
    }

    private fun detectCollision(corners1: List<Vector2D>, corners2: List<Vector2D>, pos1: Vector2D, pos2: Vector2D): CollisionResult {
        var minOverlap = Float.MAX_VALUE
        var smallestAxis = Vector2D.Zero

        val axes = getAxes(corners1) + getAxes(corners2)

        for (axis in axes) {
            val p1 = project(corners1, axis)
            val p2 = project(corners2, axis)

            val overlap = minOf(p1.max, p2.max) - maxOf(p1.min, p2.min)
            if (overlap < 0) {
                return CollisionResult(false, Vector2D.Zero)
            }

            if (overlap < minOverlap) {
                minOverlap = overlap
                smallestAxis = axis
            }
        }

        var penetrationVector = smallestAxis * minOverlap

        val directionVector = pos2 - pos1
        if (directionVector.dot(penetrationVector) < 0) {
            penetrationVector = -penetrationVector
        }

        return CollisionResult(true, penetrationVector)
    }

    private fun resolveCollision(player1: Player, player2: Player, penetrationVector: Vector2D) {
        val car1 = player1.car!!
        val car2 = player2.car!!

        val correction = penetrationVector / 2f
        val newPos1 = car1.position - correction
        val newPos2 = car2.position + correction

        val v1 = car1.getVelocity()
        val v2 = car2.getVelocity()

        val relativeVelocity = v2 - v1
        val velAlongNormal = relativeVelocity.dot(penetrationVector.normalized())

        if (velAlongNormal > 0) {
            player1.car = car1.copy(position = newPos1)
            player2.car = car2.copy(position = newPos2)
            return
        }

        val j = -(1 + RESTITUTION) * velAlongNormal
        val impulse = penetrationVector.normalized() * j

        val newV1 = v1 - impulse
        val newV2 = v2 + impulse

        player1.car = car1.withNewPhysicsState(newPos1, newV1)
        player2.car = car2.withNewPhysicsState(newPos2, newV2)
    }

    private fun getCarCorners(car: Car): List<Vector2D> {
        val pos = car.position
        val angle = car.visualDirection
        val shrinkFactor = 0.05f
        val halfW = (Car.WIDTH * car.sizeModifier) / 2f
        val halfL = (Car.LENGTH * car.sizeModifier) / 2f

        val cosA = cos(angle)
        val sinA = sin(angle)

        val corners = listOf(
            Vector2D(-halfL * (1f - shrinkFactor), -halfW * (1f - shrinkFactor)),
            Vector2D(halfL * (1f - shrinkFactor), -halfW * (1f - shrinkFactor)),
            Vector2D(halfL * (1f - shrinkFactor), halfW * (1f - shrinkFactor)),
            Vector2D(-halfL * (1f - shrinkFactor), halfW * (1f - shrinkFactor))
        )

        return corners.map { c ->
            Vector2D(
                x = c.x * cosA - c.y * sinA + pos.x,
                y = c.x * sinA + c.y * cosA + pos.y
            )
        }
    }

    private fun getAxes(corners: List<Vector2D>): List<Vector2D> {
        val axes = mutableListOf<Vector2D>()
        for (i in corners.indices) {
            val p1 = corners[i]
            val p2 = corners[(i + 1) % corners.size]
            val edge = p2 - p1

            axes.add(Vector2D(-edge.y, edge.x).normalized())
        }
        return axes
    }

    private fun project(corners: List<Vector2D>, axis: Vector2D): Projection {
        var min = corners[0].dot(axis)
        var max = min
        for (i in 1 until corners.size) {
            val p = corners[i].dot(axis)
            if (p < min) min = p
            else if (p > max) max = p
        }
        return Projection(min, max)
    }
}