package manager
import mobility.domain.Vector2D

data class BonusSpawnPoint(
    val id: Int,
    val position: Vector2D,
    var type: BonusType = BonusType.SPEED_BOOST,
    var isActive: Boolean = true,
    var respawnCooldown: Float = 0f
) {
    companion object {
        const val RESPAWN_TIME_SECONDS = 8.0f
    }

    fun reset() {
        isActive = true
        respawnCooldown = 0f
        type = BonusType.entries.toTypedArray().random()
    }

    fun onPickup() {
        isActive = false
        respawnCooldown = RESPAWN_TIME_SECONDS
    }
}

data class ActivePlayerEffect(
    val playerId: String,
    val type: BonusType,
    var durationRemaining: Float
)

enum class BonusType {
    SPEED_BOOST,
     MASS_INCREASE
}