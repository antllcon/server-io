package mobility.config

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlin.time.Duration.Companion.seconds


fun Application.configureWebSockets() {
    install(WebSockets) {
        pingPeriod = 50.seconds
        timeout = 50.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
}