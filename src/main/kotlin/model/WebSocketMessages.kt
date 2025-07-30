package mobility.model

import domain.GameMap
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mobility.domain.Vector2D
import javax.swing.text.Position

// Enums remain the same
enum class ClientMessageType {
    INIT_PLAYER,
    CREATE_ROOM,
    JOIN_ROOM,
    LEAVE_ROOM,
    START_GAME,
    PLAYER_ACTION,
    PLAYER_INPUT,
    PLAYER_FINISHED
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
    val visualDirection: Float,
    val speed: Float,
    val isFinished: Boolean,
//    val currentSprite: Int
)

@Serializable
@SerialName("PLAYER_INPUT")
data class PlayerInputRequest(val visualDirection: Float, val elapsedTime: Float, val ringsCrossed: Int) : ClientMessage {
    override val type: ClientMessageType get() = ClientMessageType.PLAYER_INPUT
}

@Serializable
@SerialName("PLAYER_FINISHED")
data class PlayerFinishedRequest(val name: String): ClientMessage {
    override val type: ClientMessageType get() = ClientMessageType.PLAYER_FINISHED
}

@Serializable
sealed interface ServerMessage {
    val type: ServerMessageType
}

// TODO: поменять название на roomPlayersResponse
@Serializable
@SerialName("PLAYER_CONNECTED")
data class PlayerConnectedResponse(val playerId: String, val playerNames: Array<String>) : ServerMessage {
    override val type: ServerMessageType get() = ServerMessageType.PLAYER_CONNECTED
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PlayerConnectedResponse

        if (playerId != other.playerId) return false
        if (!playerNames.contentEquals(other.playerNames)) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = playerId.hashCode()
        result = 31 * result + playerNames.contentHashCode()
        result = 31 * result + type.hashCode()
        return result
    }
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
data class StarterPack(
    val mapGrid: Array<IntArray>,
    val mapWidth: Int,
    val mapHeight: Int,
    val initialPlayerStates: List<Vector2D>,
    val startDirection: GameMap.StartDirection,
    val route: List<Vector2D>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StarterPack

        if (mapWidth != other.mapWidth) return false
        if (mapHeight != other.mapHeight) return false
        if (!mapGrid.contentDeepEquals(other.mapGrid)) return false
        if (initialPlayerStates != other.initialPlayerStates) return false
        if (startDirection != other.startDirection) return false
        if (route != other.route) return false

        return true
    }

    override fun hashCode(): Int {
        var result = mapWidth.hashCode()
        result = 31 * result + mapHeight.hashCode()
        result = 31 * result + mapGrid.contentDeepHashCode()
        result = 31 * result + initialPlayerStates.hashCode()
        result = 31 * result + startDirection.hashCode()
        result = 31 * result + route.hashCode()
        return result
    }
}

@Serializable
@SerialName("STARTED_GAME")
data class StartedGameResponse(val roomId: String, val starterPack: StarterPack) : ServerMessage {
    override val type: ServerMessageType get() = ServerMessageType.STARTED_GAME
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
data class GameCountdownUpdateResponse(val remainingTime: Int) : ServerMessage {
    override val type: ServerMessageType get() = ServerMessageType.GAME_COUNTDOWN_UPDATE
}

@Serializable
@SerialName("GAME_STATE_UPDATE")
data class GameStateUpdateResponse(val players: List<PlayerStateDto>) : ServerMessage {
    override val type: ServerMessageType get() = ServerMessageType.GAME_STATE_UPDATE
}

@Serializable
@SerialName("GAME_STOP")
data class GameStopResponse(val result: MutableMap<String, Float>) : ServerMessage {
    override val type: ServerMessageType get() = ServerMessageType.GAME_STOP
}