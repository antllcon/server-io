//package mobility.service
//
//import io.ktor.websocket.WebSocketSession
//import mobility.manager.GameRoomManager
//import mobility.model.Player
//import mobility.model.ServerException
//
//// Вспомогательная функция для получения игрока или отправки ошибки
//suspend fun WebSocketSession.getInitializedPlayerOrSendError(handler: GameWebSocketHandler, context: String): Player? {
//    val playerId = handler.sessionToPlayerId[this]
//    if (playerId == null) {
//        handler.sendErrorToSession(this, ServerException(context, "Player is not initialized"))
//        return null
//    }
//    val player = GameRoomManager.players[playerId]
//    if (player == null) {
//        handler.sendErrorToSession(this, ServerException(context, "Player with ID $playerId not found"))
//        return null
//    }
//    if (player.roomId != null) {
//        handler.sendErrorToSession(this, ServerException(context, "Player is already in a room."))
//        return null
//    }
//    return player
//}