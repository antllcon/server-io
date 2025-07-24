package mobility.service

import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.send
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import mobility.model.ErrorResponse
import mobility.model.ServerException
import mobility.model.ServerMessage
import mobility.model.*

val AppJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    prettyPrint = false
    classDiscriminator = "kind"

    // Регистрация интерфейсов и их подклассов для полиморфной сериализации/десериализации
    serializersModule = SerializersModule {
        polymorphic(ClientMessage::class) {
            subclass(InitPlayerRequest::class)
            subclass(CreateRoomRequest::class)
            subclass(JoinRoomRequest::class)
            subclass(LeaveRoomRequest::class)
            subclass(StartGameRequest::class)
            subclass(PlayerActionRequest::class)
            subclass(PlayerInputRequest::class)
        }
        polymorphic(ServerMessage::class) {
            subclass(PlayerConnectedResponse::class)
            subclass(PlayerDisconnectedResponse::class)
            subclass(InfoResponse::class)
            subclass(ErrorResponse::class)
            subclass(RoomCreatedResponse::class)
            subclass(JoinedRoomResponse::class)
            subclass(LeftRoomResponse::class)
            subclass(StartedGameResponse::class)
            subclass(RoomUpdatedResponse::class)
            subclass(PlayerActionResponse::class)
            subclass(GameCountdownUpdateResponse::class)
            subclass(GameStateUpdateResponse::class)
            subclass(GameStopResponse::class)
        }
    }
}

suspend fun sendToSession(session: WebSocketSession, message: ServerMessage) {
    val jsonStr = AppJson.encodeToString(message)
    session.send(jsonStr)
}

suspend fun sendErrorToSession(session: WebSocketSession, error: ServerException) =
    sendToSession(session, ErrorResponse(error.type, error.message.toString()))
