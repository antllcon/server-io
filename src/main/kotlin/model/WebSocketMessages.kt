package mobility.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Enums remain the same
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
    PLAYER_ACTION
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
@SerialName("ROOM_UPDATE")
data class RoomUpdatedResponse(val roomId: String) : ServerMessage {
    override val type: ServerMessageType get() = ServerMessageType.ROOM_UPDATE
}

@Serializable
@SerialName("PLAYER_ACTION")
data class PlayerActionResponse(val name: String) : ServerMessage {
    override val type: ServerMessageType get() = ServerMessageType.PLAYER_ACTION
}