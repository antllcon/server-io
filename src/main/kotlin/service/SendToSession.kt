package mobility.service

import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.send
import kotlinx.serialization.json.Json
import mobility.model.WebSocketMessage

// Единый экземпляр Json форматера для всего приложения
val AppJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    prettyPrint = false
    classDiscriminator = "messageClass"
}

/**
 * Отправляет WebSocket-сообщение в указанную сессию.
 * Сообщение сериализуется в JSON с использованием AppJson.
 * @param session Сессия WebSocket, в которую будет отправлено сообщение.
 * @param message Сообщение WebSocketMessage для отправки.
 */
suspend fun sendToSession(session: WebSocketSession, message: WebSocketMessage) {
    val jsonStr = AppJson.encodeToString(message)
    session.send(jsonStr)
}
