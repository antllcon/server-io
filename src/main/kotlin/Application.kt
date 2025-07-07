package mobility

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import mobility.routes.configureRouting
import mobility.service.configureWebSockets


fun main() {
    // Метод создания сервера
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configureWebSockets()
    configureRouting()
}