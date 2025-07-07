package mobility.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

enum class ClientMessageType {
    INIT_PLAYER,
    CREATE_ROOM,
    JOIN_ROOM,
    LEAVE_ROOM,
    PLAYER_ACTION
}

enum class ServerMessageType {
    PLAYER_CONNECTED,
    PLAYER_DISCONNECTED,
    INFO,
    ERROR,
    ROOM_CREATED,
    JOINED_ROOM,
    LEFT_ROOM,
    ROOM_UPDATE,
    PLAYER_ACTION_RESPONSE
}

@Serializable
sealed class WebSocketMessage {
    abstract val type: Enum<*>
}

@Serializable
data class ClientMessage(
    override val type: ClientMessageType,
    val data: JsonElement? = null
) : WebSocketMessage()

@Serializable
data class InitPlayerMessage(val name: String) : WebSocketMessage() {
    override val type: ClientMessageType = ClientMessageType.INIT_PLAYER
}

@Serializable
data class CreateRoomMessage(val name: String) : WebSocketMessage() {
    override val type: ClientMessageType = ClientMessageType.CREATE_ROOM
}

@Serializable
data class JoinRoomMessage(val name: String) : WebSocketMessage() {
    override val type: ClientMessageType = ClientMessageType.JOIN_ROOM
}

@Serializable
data class LeaveRoomMessage(val name: String) : WebSocketMessage() {
    override val type: ClientMessageType = ClientMessageType.LEAVE_ROOM
}

@Serializable
data class PlayerActionMessage(val name: String) : WebSocketMessage() {
    override val type: ClientMessageType = ClientMessageType.PLAYER_ACTION
}

@Serializable
data class ServerMessage(
    override val type: ServerMessageType,
    val data: JsonElement? = null
) : WebSocketMessage()

@Serializable
data class PlayerConnectedMessage(val playerId: String, val playerName: String) : WebSocketMessage() {
    override val type: ServerMessageType = ServerMessageType.PLAYER_CONNECTED
}

@Serializable
data class PlayerDisconnectedMessage(val playerId: String) : WebSocketMessage() {
    override val type: ServerMessageType = ServerMessageType.PLAYER_DISCONNECTED
}

@Serializable
data class InfoMessage(val message: String) : WebSocketMessage() {
    override val type: ServerMessageType = ServerMessageType.INFO
}

@Serializable
data class ErrorMessage(
    val code: String,
    val message: String
) : WebSocketMessage() {
    override val type: ServerMessageType = ServerMessageType.ERROR
}


@Serializable
data class RoomCreatedMessage(val roomId: String) : WebSocketMessage() {
    override val type: ServerMessageType = ServerMessageType.ROOM_CREATED
}

@Serializable
data class JoinedRoomMessage(val roomId: String) : WebSocketMessage() {
    override val type: ServerMessageType = ServerMessageType.JOINED_ROOM
}

@Serializable
data class LeftRoomMessage(val roomId: String) : WebSocketMessage() {
    override val type: ServerMessageType = ServerMessageType.LEFT_ROOM
}

@Serializable
data class RoomUpdatedMessage(val roomId: String) : WebSocketMessage() {
    override val type: ServerMessageType = ServerMessageType.ROOM_UPDATE
}

@Serializable
data class PlayerActionResponseMessage(val name: String) : WebSocketMessage() {
    override val type: ServerMessageType = ServerMessageType.PLAYER_ACTION_RESPONSE
}