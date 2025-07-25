package mobility.model

import domain.Car
import io.ktor.websocket.WebSocketSession

data class Player(
    val id: String = (1..6).map { ('A'..'Z').random() }.joinToString(""),
    val name: String,
    @kotlinx.serialization.Transient
    val session: WebSocketSession,
    var roomId: String? = null,
    var car: Car? = null,
    var ringsCrossed: Int = 0,
    var secondsAfterStart: Float = 0f
)