package mobility.model

import domain.GameMap
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Enums remain the same
enum class ClientMessageType {
    INIT_PLAYER,
    CREATE_ROOM,
    JOIN_ROOM,
    LEAVE_ROOM,
    START_GAME,
    PLAYER_ACTION,
    PLAYER_INPUT
}

enum class ServerMessageType {
    PLAYER_CONNECTED,
    PLAYER_DISCONNECTED,
    INFO,
    ERROR,
    ROOM_CREATED,
    JOINED_ROOM,
    LEFT_ROOM,
    STARTED_GAME,
    ROOM_UPDATE,
    PLAYER_ACTION,
    GAME_COUNTDOWN_UPDATE,
    GAME_STATE_UPDATE,
    GAME_STOP
}

@Serializable
sealed interface ClientMessage {
    val type: ClientMessageType
}

@Serializable
@SerialName("INIT_PLAYER")
data class InitPlayerRequest(val name: String) : ClientMessage {
    override val type: ClientMessageType get() = ClientMessageType.INIT_PLAYER
}

@Serializable
@SerialName("CREATE_ROOM")
data class CreateRoomRequest(val name: String) : ClientMessage {
    override val type: ClientMessageType get() = ClientMessageType.CREATE_ROOM
}

@Serializable
@SerialName("START_GAME")
data class StartGameRequest(val name: String) : ClientMessage {
    override val type: ClientMessageType get() = ClientMessageType.START_GAME
}

@Serializable
@SerialName("JOIN_ROOM")
data class JoinRoomRequest(val name: String) : ClientMessage {
    override val type: ClientMessageType get() = ClientMessageType.JOIN_ROOM
}

// This message needs no data from the client, so it can be an 'object'.
@Serializable
@SerialName("LEAVE_ROOM")
object LeaveRoomRequest : ClientMessage {
    override val type: ClientMessageType get() = ClientMessageType.LEAVE_ROOM
}

@Serializable
@SerialName("PLAYER_ACTION")
data class PlayerActionRequest(val name: String) : ClientMessage {
    override val type: ClientMessageType get() = ClientMessageType.PLAYER_ACTION
}

@Serializable
data class PlayerStateDto(
    val id: String,
    val posX: Float,
    val posY: Float,
    val direction: Float,
    val isFinished: Boolean
)

@Serializable
@SerialName("PLAYER_INPUT")
data class PlayerInputRequest(val isAccelerating: Boolean, val turnDirection: Float, val deltaTime: Float, val ringsCrossed: Int) : ClientMessage {
    override val type: ClientMessageType get() = ClientMessageType.PLAYER_INPUT
}

@Serializable
sealed interface ServerMessage {
    val type: ServerMessageType
}

@Serializable
@SerialName("PLAYER_CONNECTED")
data class PlayerConnectedResponse(val playerId: String, val playerName: String) : ServerMessage {
    override val type: ServerMessageType get() = ServerMessageType.PLAYER_CONNECTED
}

@Serializable
@SerialName("PLAYER_DISCONNECTED")
data class PlayerDisconnectedResponse(val playerId: String) : ServerMessage {
    override val type: ServerMessageType get() = ServerMessageType.PLAYER_DISCONNECTED
}

@Serializable
@SerialName("INFO")
data class InfoResponse(val message: String) : ServerMessage {
    override val type: ServerMessageType get() = ServerMessageType.INFO
}

@Serializable
@SerialName("ERROR")
data class ErrorResponse(val code: String, val message: String) : ServerMessage {
    override val type: ServerMessageType get() = ServerMessageType.ERROR
}

@Serializable
@SerialName("ROOM_CREATED")
data class RoomCreatedResponse(val roomId: String) : ServerMessage {
    override val type: ServerMessageType get() = ServerMessageType.ROOM_CREATED
}

@Serializable
@SerialName("JOINED_ROOM")
data class JoinedRoomResponse(val roomId: String) : ServerMessage {
    override val type: ServerMessageType get() = ServerMessageType.JOINED_ROOM
}

@Serializable
@SerialName("LEFT_ROOM")
data class LeftRoomResponse(val roomId: String) : ServerMessage {
    override val type: ServerMessageType get() = ServerMessageType.LEFT_ROOM
}

@Serializable
@SerialName("STARTED_GAME")
data class StartedGameResponse(val roomId: String, val gameMap: Array<IntArray>) : ServerMessage {
    override val type: ServerMessageType get() = ServerMessageType.STARTED_GAME

    //everything below this is the code that Android Studio added by itself,
    //so I don't have a clue what the hell is this
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StartedGameResponse

        if (roomId != other.roomId) return false
        if (!gameMap.contentDeepEquals(other.gameMap)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = roomId.hashCode()
        result = 31 * result + gameMap.contentDeepHashCode()
        return result
    }
}

@Serializable
@SerialName("ROOM_UPDATE")
data class RoomUpdatedResponse(val roomId: String) : ServerMessage {
    override val type: ServerMessageType get() = ServerMessageType.ROOM_UPDATE
}

@Serializable
@SerialName("PLAYER_ACTION")
data class PlayerActionResponse(val name: String) : ServerMessage {
    override val type: ServerMessageType get() = ServerMessageType.PLAYER_ACTION
}

@Serializable
@SerialName("GAME_COUNTDOWN_UPDATE")
data class GameCountdownUpdateResponse(val remainingTime: Long) : ServerMessage {
    override val type: ServerMessageType get() = ServerMessageType.GAME_COUNTDOWN_UPDATE
}

@Serializable
@SerialName("GAME_STATE_UPDATE")
data class GameStateUpdateResponse(val players: List<PlayerStateDto>) : ServerMessage {
    override val type: ServerMessageType get() = ServerMessageType.GAME_STATE_UPDATE
}

@Serializable
@SerialName("GAME_STOP")
data class GameStopResponse(val result: MutableMap<String, Long>) : ServerMessage {
    override val type: ServerMessageType get() = ServerMessageType.GAME_STOP
}