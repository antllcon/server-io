package mobility.model

import mobility.config.GameConst.DEFAULT_MAX_PLAYERS
import java.util.UUID

data class GameRoom (
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val players: MutableList<Player> = mutableListOf(),
    val maxPlayers: UInt = DEFAULT_MAX_PLAYERS
) {
    fun isFull(): Boolean = players.size.toUInt() >= maxPlayers
}