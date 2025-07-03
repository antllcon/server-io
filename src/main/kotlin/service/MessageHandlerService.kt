//package mobility.service
//
//import io.ktor.websocket.send
//import kotlinx.serialization.json.Json
//import kotlinx.serialization.json.buildJsonObject
//import kotlinx.serialization.json.jsonObject
//import kotlinx.serialization.json.jsonPrimitive
//import mobility.manager.GameRoomManager
//import mobility.model.Player
//
//object MessageHandlerService {
//    private suspend fun handleClientMessage(player: Player, message: String) {
//        try {
//            val json = Json.parseToJsonElement(message)
//            val type = json.jsonObject["type"]?.jsonPrimitive?.content ?: return
//
//            when (type) {
//                "create_room" -> {
//                    val roomName = json.jsonObject["roomName"]?.jsonPrimitive?.content ?: "New Room"
//                    val room = GameRoomManager.createRoom(roomName)
//                    GameRoomManager.joinRoom(player.id, room.id)
//
//                    player.session.send(buildJsonObject {
//                        put("type", "room_created")
//                        put("roomId", room.id)
//                        put("roomName", room.name)
//                    }.toString())
//                }
//
//                "join_room" -> {
//                    val roomId = json.jsonObject["roomId"]?.jsonPrimitive?.content ?: return
//                    if (GameRoomManager.joinRoom(player.id, roomId)) {
//                        notifyRoomPlayers(roomId, player)
//                    } else {
//                        player.session.send(buildJsonObject {
//                            put("type", "join_error")
//                            put("message", "Cannot join room")
//                        }.toString())
//                    }
//                }
//
//                "game_action" -> {
//                    val roomId = player.roomId ?: return
//                    broadcastToRoom(roomId, message, player.id)
//                }
//            }
//        } catch (e: Exception) {
//            println("Error handling message: ${e.message}")
//        }
//    }
//}