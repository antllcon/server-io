package domain

import mobility.domain.Vector2D
import kotlin.collections.getOrNull

class CheckpointManager(private val route: List<Vector2D>) {
    private val carProgress: MutableMap<String, Int> = mutableMapOf()
    private val carLaps: MutableMap<String, Int> = mutableMapOf()

    fun registerCar(carId: String) {
        carProgress[carId] = 0
        carLaps[carId] = 0
    }

    fun getNextCheckpoint(carId: String): Vector2D? {
        if (route.isEmpty()) {
            println("Warning: Route is empty, cannot get next checkpoint")
            return null
        }
        val nextCheckpointIndex: Int = carProgress[carId] ?: return null
        return route[nextCheckpointIndex]
    }

    fun onCheckpointReached(carId: String, reachedCheckpointPosition: Vector2D) {
        val nextCheckpointIndex: Int = carProgress[carId] ?: return
        val targetCheckpoint: Vector2D = route.getOrNull(nextCheckpointIndex) ?: return

        if (targetCheckpoint == reachedCheckpointPosition) {
            val newNextIndex: Int = (nextCheckpointIndex + 1)

            if (newNextIndex >= route.size) {
                carLaps[carId] = (carLaps[carId] ?: 0) + 1
                carProgress[carId] = 0
                println("Car $carId completed a lap! Total laps: ${carLaps[carId]}")
            } else {
                carProgress[carId] = newNextIndex
            }
        }
    }

    fun getLapsForCar(carId: String): Int {
        return carLaps.getOrDefault(key = carId, defaultValue = 0)
    }
}