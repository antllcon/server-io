package mobility.model

import io.ktor.websocket.WebSocketSession
import java.util.UUID

data class Player (
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val session: WebSocketSession,
    var roomId: String? = null
)