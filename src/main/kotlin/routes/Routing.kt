package mobility.routes

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import mobility.service.GameWebSocketHandler

fun Application.configureRouting() {
    val gameHandler = GameWebSocketHandler()

    routing {
        webSocket("/") {
            gameHandler.handleSession(this)
        }
    }
}