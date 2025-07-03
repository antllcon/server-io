package mobility.model

import java.util.UUID

data class GameRoom(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val players: MutableList<Player> = mutableListOf(),
    val maxPlayers: Int = 4
) {
    fun isFull(): Boolean = players.size >= maxPlayers
}