package mobility.service

import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import mobility.manager.GameRoomManager
import mobility.model.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.log

class GameWebSocketHandler {

    private val sessionToPlayerId = ConcurrentHashMap<WebSocketSession, String>()
    private val logger = LoggerFactory.getLogger(GameWebSocketHandler::class.java)

    suspend fun handleSession(session: WebSocketServerSession) {
        try {
            for (frame in session.incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    val clientMessage = try {
                        AppJson.decodeFromString<ClientMessage>(text)
                    } catch (e: Exception) {
                        sendErrorToSession(session, ServerException("MESSAGE_DECODE_ERROR", "Invalid message format: ${e.message}"))
                        continue
                    }
                    handleIncoming(session, clientMessage)
                }
            }

        } catch (_: ClosedReceiveChannelException) {
        } catch (e: Exception) {
            sendErrorToSession(session, ServerException("SESSION_ERROR", "An error occurred: ${e.message}"))
        } finally {
            handleDisconnect(session)
        }
    }

    suspend fun handleIncoming(session: WebSocketSession, message: ClientMessage) {
        try {
            when (message) {
                is InitPlayerRequest -> handleInitPlayer(session, message)
                is CreateRoomRequest -> handleCreateRoom(session, message)
                is JoinRoomRequest -> handleJoinRoom(session, message)
                is StartGameRequest -> handleStartGame(session, message)
                is LeaveRoomRequest -> handleLeaveRoom(session)
                is PlayerActionRequest -> handlePlayerAction(session, message)
                is PlayerInputRequest -> handlePlayerInput(session, message)
                is PlayerFinishedRequest -> handlePlayerFinished(session, message)

            }
        } catch (e: Exception) {
            sendErrorToSession(session, ServerException(message.type.name, "Failed to process message: ${e.message}"))
            return
        }
    }

    suspend fun handleInitPlayer(session: WebSocketSession, request: InitPlayerRequest) {
        try {
            logger.info("Init player, ${request.name}")

            val player = GameRoomManager.registerPlayer(request.name, session)
            sessionToPlayerId[session] = player.id
//            sendToSession(session, PlayerConnectedResponse(player.id, GameRoomManager.getPlayersNames()))

        } catch (e: Exception) {
            sendErrorToSession(session, ServerException(request.name, "Failed to init player: ${e.message}"))
            return
        }
    }

    suspend fun handleCreateRoom(session: WebSocketSession, request: CreateRoomRequest) {
        try {
            logger.info("Create room, ${request.name}")

            if (request.name.isBlank()) {
                sendErrorToSession(session, ServerException(request.name, "Room name cannot be empty"))
                return
            }

            GameRoomManager.rooms.keys.forEach {
                logger.info(it)
            }

            val player = getAndValidateNewPlayerOrSendError(session, request.name) ?: return

            val newRoom = GameRoomManager.createRoom(request.name)
            val isJoined = GameRoomManager.joinRoom(player.id, newRoom.id)

            if (!isJoined) {
                GameRoomManager.rooms.remove(newRoom.id)
                sendErrorToSession(session, ServerException(request.name, "Failed to join room after creation"))
                return
            }

            sendToSession(session, RoomCreatedResponse(newRoom.id))

        } catch (e: Exception) {
            sendErrorToSession(session, ServerException(request.name, "Failed to create room: ${e.message}"))
            return
        }
    }

    suspend fun handleDisconnect(session: WebSocketSession) {
        val playerId = sessionToPlayerId.remove(session) ?: return
        val player = GameRoomManager.players[playerId] ?: return

        logger.info("Disconnect, ${player.name}")
        val roomId = player.roomId ?: return
        val room = GameRoomManager.rooms[roomId] ?: return

        val newPlayersList: MutableList<Player> = mutableListOf()

        for (p in room.players) {
            if (p.name != player.name) {
                newPlayersList.add(p)
            }
        }

        room.players = newPlayersList

        broadcastToRoom(roomId, PlayerDisconnectedResponse(player.name))
        broadcastToRoom(roomId, PlayerConnectedResponse("", GameRoomManager.getPlayersNames(roomId)))

        GameRoomManager.cleanupEmptyRoom(roomId)
    }

    suspend fun broadcastToRoom(roomId: String, message: ServerMessage, exceptPlayerId: String? = null) {
        val room = GameRoomManager.rooms[roomId] ?: return

        room.players.forEach { player ->
            if (player.id != exceptPlayerId) {
                try {
//                    logger.info("Broadcast ${player.name} to room ${room.name}")
                    sendToSession(player.session, message)

                } catch (e: Exception) {
                    sendErrorToSession(player.session, ServerException(room.name, "Failed to broadcast ${e.message}"))
                }
            }
        }
    }

    suspend fun handleJoinRoom(session: WebSocketSession, request: JoinRoomRequest) {
        try {
            logger.info("Join room, ${request.name}")

            val player = getAndValidateNewPlayerOrSendError(session, request.name) ?: return

            val targetRoomIdOrName = request.name
            val room = GameRoomManager.rooms[targetRoomIdOrName]
                ?: GameRoomManager.rooms.values.firstOrNull { it.name == targetRoomIdOrName }
                ?: run {
                    sendErrorToSession(session, ServerException(request.name, "Room '$targetRoomIdOrName' not found"))
                    return
                }

            if (room.isFull()) {
                sendErrorToSession(session, ServerException(request.name, "Room '${room.name}' is full"))
                return
            }

            room.players.forEach {
                if (it.name == player.name) {
                    sendErrorToSession(session, ServerException(request.name, "Name ${player.name} is already taken in this room!"))
                    return
                }
            }

            if (room.state == GameRoomState.ONGOING || room.state == GameRoomState.ENDED) {
                sendErrorToSession(session, ServerException(request.name, "Game in room ${room.name} is already started!"))
                return
            }

            val isJoined = GameRoomManager.joinRoom(player.id, room.id)

            if (isJoined) {
                sendToSession(session, JoinedRoomResponse(room.id))
                broadcastToRoom(player.roomId!!, PlayerConnectedResponse(player.id, GameRoomManager.getPlayersNames(room.id)))
            } else {
                sendErrorToSession(session, ServerException(request.name, "Failed to join room '${room.name}'"))
            }

        } catch (e: Exception) {
            sendErrorToSession(session, ServerException(request.name, "Failed to join room: ${e.message}"))
            return
        }
    }

    suspend fun handleLeaveRoom(session: WebSocketSession) {
        val (player, roomId) = getAndValidatePlayerInRoomOrSendError(session, "LEAVE_ROOM") ?: return
        logger.info("Leave room, ${player.name} from ${GameRoomManager.rooms[player.roomId]?.name}")

        try {
            GameRoomManager.leaveRoom(player.id)
            sendToSession(session, LeftRoomResponse(roomId))

            broadcastToRoom(roomId, PlayerDisconnectedResponse(player.id), player.id)
            broadcastToRoom(roomId, PlayerConnectedResponse("", GameRoomManager.getPlayersNames(roomId)))

        } catch (e: Exception) {
            sendErrorToSession(session, ServerException("LEAVE_ROOM", "Failed to leave room: ${e.message}"))
        }
    }

    suspend fun handleStartGame(session: WebSocketSession, request: StartGameRequest) {
        try {
            logger.info("Starting game, ${request.name}")

            val (player, roomId) = getAndValidatePlayerInRoomOrSendError(session, request.name) ?: return

            val currentRoom = GameRoomManager.rooms[roomId]!!

            if (player != currentRoom.players[0]) {
                sendErrorToSession(session, ServerException(request.name, "This player is not an admin!"))
                return
            }

            val starterPack = currentRoom.initGameAndCreateStarterPack()
            if (starterPack == null) {
                sendErrorToSession(session, ServerException(request.name, "Game has already been started or is in an invalid state."))
                return
            }

            val responseMessage = StartedGameResponse(roomId, starterPack)
            broadcastToRoom(roomId, responseMessage)

            currentRoom.state = GameRoomState.COUNTDOWN
            currentRoom.startCountdown(this)

        } catch (_: Exception) {
            sendErrorToSession(session, ServerException(request.name, "Failed to start the game!"))
        }
    }

    suspend fun handlePlayerAction(session: WebSocketSession, request: PlayerActionRequest) {
        try {
            val playerId = sessionToPlayerId[session] ?: run {
                sendErrorToSession(session, ServerException(request.name, "Player is not init"))
                return
            }
            val player = GameRoomManager.players[playerId] ?: run {
                sendErrorToSession(session, ServerException(request.name, "Player with ID $playerId not found"))
                return
            }

            val currentRoomId = player.roomId

            if (currentRoomId == null) {
                sendErrorToSession(session, ServerException(request.name, "Player is not in any room to do actions"))
                return
            }

            //
            // TODO: логика обработки действий (их валидность)
            //

            broadcastToRoom(currentRoomId, PlayerActionResponse("Player ${player.name} performed action: ${request.name}"), playerId)

        } catch (e: Exception) {
            sendErrorToSession(session, ServerException(request.name, "Failed to process player action: ${e.message}"))
        }
    }

    private fun handlePlayerInput(session: WebSocketSession, request: PlayerInputRequest) {
        val playerId = sessionToPlayerId[session] ?: return
        val player = GameRoomManager.players[playerId] ?: return
        val roomId = player.roomId ?: return
        val room = GameRoomManager.rooms[roomId] ?: return

        room.playerInputs[playerId] = request
    }

    private suspend fun handlePlayerFinished(session: WebSocketSession, request: PlayerFinishedRequest) {
        val playerId = sessionToPlayerId[session] ?: run {
            sendErrorToSession(session, ServerException(request.name, "Player is not init"))
            return
        }
        val player = GameRoomManager.players[playerId] ?: run {
            sendErrorToSession(session, ServerException(request.name, "Player with ID $playerId not found"))
            return
        }
        val roomId = player.roomId ?: return
        val room = GameRoomManager.rooms[roomId] ?: return

        logger.info("${player.name} finished!")

        player.isFinished = true
        val result: MutableMap<String, Float> = mutableMapOf()

        for (player in room.players) {
            if (!player.isFinished) return
            result.put(player.name, player.secondsAfterStart)
        }

        val sortedResult = result.toMap().toList().sortedBy { (_, value) -> value }.toMap().toMutableMap()

        logger.info("Sending to room: $sortedResult")

        broadcastToRoom(roomId, GameStopResponse(sortedResult))
    }

    private suspend fun getPlayerIdOrSendError(session: WebSocketSession, context: String): String? {
        val playerId = sessionToPlayerId[session]
        if (playerId == null) {
            sendErrorToSession(session, ServerException(context, "Player is not initialized"))
            return null
        }
        return playerId
    }

    private suspend fun getPlayerOrSendError(session: WebSocketSession, playerId: String, context: String): Player? {
        val player = GameRoomManager.players[playerId]
        if (player == null) {
            sendErrorToSession(session, ServerException(context, "Failed to find player with id: $playerId"))
            return null
        }
        return player
    }

    private suspend fun ensurePlayerNotInRoom(session: WebSocketSession, player: Player, context: String): Boolean {
        if (!player.roomId.isNullOrEmpty()) {
            sendErrorToSession(session, ServerException(context, "Player already in room"))
            return false
        }
        return true
    }

    private suspend fun getAndValidateNewPlayerOrSendError(session: WebSocketSession, context: String): Player? {
        val playerId = getPlayerIdOrSendError(session, context) ?: return null
        val player = getPlayerOrSendError(session, playerId, context) ?: return null
        if (!ensurePlayerNotInRoom(session, player, context)) return null
        return player
    }

    private suspend fun getAndValidatePlayerInRoomOrSendError(session: WebSocketSession, context: String): Pair<Player, String>? {
        val playerId = getPlayerIdOrSendError(session, context) ?: return null
        val player = getPlayerOrSendError(session, playerId, context) ?: return null
        val roomId = player.roomId
        if (roomId.isNullOrEmpty()) {
            sendErrorToSession(session, ServerException(context, "Player is not in any room"))
            return null
        }
        return Pair(player, roomId)
    }
}