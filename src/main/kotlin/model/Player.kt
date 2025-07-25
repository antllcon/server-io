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
    val car: Car = Car(
        position = Vector2D(5.4f, 5.5f),
        id = "Temp ID",
        direction = 0f,
        visualDirection = 0f
    ),
    var ringsCrossed: Int = 0,
    var secondsAfterStart: Float = 0f
)