package mobility

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import mobility.routes.configureRouting
import kotlinx.serialization.json.*
import mobility.manager.GameRoomManager
import mobility.model.Player
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import java.util.*


fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

//fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    // Установка WebSocket
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    // Регистрация маршрутов
    routing {
        webSocket("/game") {
            // 1. Принимаем имя игрока (первое сообщение)
            val nameFrame = incoming.receive() as? Frame.Text ?: return@webSocket
            val playerName = nameFrame.readText()
            val player = GameRoomManager.registerPlayer(playerName, this)

            // 2. Обрабатываем последующие сообщения
            try {
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            println("Raw message: $text") // Логируем сырое сообщение

                            try {
                                // Парсим JSON
                                val json = Json.parseToJsonElement(text)
                                val type = json.jsonObject["type"]?.jsonPrimitive?.content

                                when (type) {
                                    "create_room" -> {
                                        val roomName = json.jsonObject["roomName"]?.jsonPrimitive?.content ?: "New Room"
                                        val room = GameRoomManager.createRoom(roomName)
                                        GameRoomManager.joinRoom(player.id, room.id)

                                        send(
                                            buildJsonObject {
                                                put("type", "room_created")
                                                put("roomId", room.id)
                                                put("roomName", room.name)
                                            }.toString()
                                        )
                                    }
                                    // ... другие команды ...
                                }
                            } catch (e: Exception) {
                                println("JSON parse error: ${e.message}")
                            }
                        }
                        else -> {}
                    }
                }
            } finally {
                GameRoomManager.removePlayer(player.id)
            }
        }
    }
//    configureRouting()
//    configureSecurity()
//    configureMonitoring()
//    configureSerialization()
//    configureDatabases()
//    configureSockets()
}

// Обработчик сообщений от клиента
private suspend fun handleClientMessage(player: Player, message: String) {
    try {
        val json = Json.parseToJsonElement(message)
        val type = json.jsonObject["type"]?.jsonPrimitive?.content ?: return

        when (type) {
            "create_room" -> {
                val roomName = json.jsonObject["roomName"]?.jsonPrimitive?.content ?: "New Room"
                val room = GameRoomManager.createRoom(roomName)
                GameRoomManager.joinRoom(player.id, room.id)

                player.session.send(buildJsonObject {
                    put("type", "room_created")
                    put("roomId", room.id)
                    put("roomName", room.name)
                }.toString())
            }

            "join_room" -> {
                val roomId = json.jsonObject["roomId"]?.jsonPrimitive?.content ?: return
                if (GameRoomManager.joinRoom(player.id, roomId)) {
                    notifyRoomPlayers(roomId, player)
                } else {
                    player.session.send(buildJsonObject {
                        put("type", "join_error")
                        put("message", "Cannot join room")
                    }.toString())
                }
            }

            "game_action" -> {
                val roomId = player.roomId ?: return
                broadcastToRoom(roomId, message, player.id)
            }
        }
    } catch (e: Exception) {
        println("Error handling message: ${e.message}")
    }
}

// Вспомогательные функции
private suspend fun notifyRoomPlayers(roomId: String, newPlayer: Player) {
    val room = GameRoomManager.rooms[roomId] ?: return

    // Отправляем новому игроку информацию о комнате
    newPlayer.session.send(buildJsonObject {
        put("type", "room_info")
        put("roomId", room.id)
        put("roomName", room.name)
        put("players", buildJsonArray {
            room.players.forEach { player ->
                if (player.id != newPlayer.id) {
                    add(buildJsonObject {
                        put("id", player.id)
                        put("name", player.name)
                    })
                }
            }
        })
    }.toString())

    // Уведомляем других игроков
    broadcastToRoom(roomId, buildJsonObject {
        put("type", "player_joined")
        put("player", buildJsonObject {
            put("id", newPlayer.id)
            put("name", newPlayer.name)
        })
    }.toString(), newPlayer.id)
}

private suspend fun broadcastToRoom(roomId: String, message: String, excludePlayerId: String? = null) {
    val room = GameRoomManager.rooms[roomId] ?: return

    room.players.forEach { player ->
        if (player.id != excludePlayerId) {
            try {
                player.session.send(message)
            } catch (e: Exception) {
                // Игрок отключился
                GameRoomManager.removePlayer(player.id)
            }
        }
    }
}