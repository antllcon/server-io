package mobility.service

import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.send
import kotlinx.serialization.json.Json
import mobility.model.ErrorResponse
import mobility.model.ServerException
import mobility.model.ServerMessage

val AppJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    prettyPrint = false
    classDiscriminator = "kind"
}

suspend fun sendToSession(session: WebSocketSession, message: ServerMessage) {
    val jsonStr = AppJson.encodeToString(message)
    session.send(jsonStr)
}

suspend fun sendErrorToSession(session: WebSocketSession, error: ServerException) =
    sendToSession(session, ErrorResponse(error.type, error.message.toString()))