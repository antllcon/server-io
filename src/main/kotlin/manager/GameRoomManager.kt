package mobility.manager

import domain.GameMap
import io.ktor.websocket.WebSocketSession
import mobility.model.GameRoom
import mobility.model.Player

object GameRoomManager {
    val rooms = mutableMapOf<String, GameRoom>()
    val players = mutableMapOf<String, Player>()

    fun createRoom(name: String): GameRoom {
        val room = GameRoom(name = name)
        rooms[room.id] = room
        return room
    }

    fun joinRoom(playerId: String, roomId: String): Boolean {
        val player = players[playerId] ?: return false
        val room = rooms[roomId] ?: return false
        if (room.isFull()) return false

        player.roomId = roomId
        room.players.add(player)
        return true
    }

    fun leaveRoom(playerId: String) {
        val player = players[playerId] ?: return
        val roomId = player.roomId ?: return
        rooms[roomId]?.players?.remove(player)
        player.roomId = null
    }

    fun registerPlayer(name: String, session: WebSocketSession): Player {
        val player = Player(name = name, session = session)
        players[player.id] = player
        return player
    }

    fun removePlayer(playerId: String) {
        leaveRoom(playerId)
        players.remove(playerId)
    }

    fun getPlayersNames(): Array<String> {
        var names = emptyArray<String>()
        players.forEach { _, player ->
            names = names.plus(player.name)
        }

        return names
    }

    fun cleanupEmptyRoom(roomId: String): Boolean {
        val room = rooms[roomId]
        if (room != null && room.players.isEmpty()) {
            println("Room $roomId is empty, stopping loop and removing it.")
            room.stopGameLoop()
            rooms.remove(roomId)
            return true
        }
        return false
    }
}