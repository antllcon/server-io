package mobility.model

import domain.Car
import io.ktor.websocket.WebSocketSession
import mobility.domain.Vector2D
import java.util.UUID

data class Player(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    @kotlinx.serialization.Transient
    val session: WebSocketSession,
    var roomId: String? = null,
    val car: Car = Car(id = id, playerName = name, initialPosition = Vector2D(5f, 5f)),
    var ringsCrossed: Int = 0,
    var secondsAfterStart: Float = 0f
)