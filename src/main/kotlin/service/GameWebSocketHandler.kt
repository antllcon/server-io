package mobility.service

import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import mobility.manager.GameRoomManager
import mobility.model.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

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
                        println("Error decoding message: $e")
                        sendErrorToSession(session, ServerException("MESSAGE_DECODE_ERROR", "Invalid message format: ${e.message}"))
                        continue
                    }
                    handleIncoming(session, clientMessage)
                }
            }

        } catch (_: ClosedReceiveChannelException) {
            println("WebSocket session closed: $session")
        } catch (e: Exception) {
            println("WebSocket exception: $e")
            sendErrorToSession(session, ServerException("SESSION_ERROR", "An error occurred: ${e.message}"))
        } finally {
            println("Disconnect for session: $session")
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

            }
        } catch (e: Exception) {
            sendErrorToSession(session, ServerException(message.type.name, "Failed to process message: ${e.message}"))
            return
        }
    }

    suspend fun handleInitPlayer(session: WebSocketSession, request: InitPlayerRequest) {
        try {
            val player = GameRoomManager.registerPlayer(request.name, session)
            sessionToPlayerId[session] = player.id
            sendToSession(session, PlayerConnectedResponse(player.id, GameRoomManager.getPlayersNames()))
        } catch (e: Exception) {
            sendErrorToSession(session, ServerException(request.name, "Failed to init player: ${e.message}"))
            return
        }
    }

    suspend fun handleCreateRoom(session: WebSocketSession, request: CreateRoomRequest) {
        try {
            val playerId = sessionToPlayerId[session] ?: run {
                sendErrorToSession(session, ServerException(request.name, "Player is not init"))
                return
            }

            val player = GameRoomManager.players[playerId] ?: run {
                sendErrorToSession(session, ServerException(request.name, "Failed to find player with id: $playerId"))
                return
            }

            if (player.roomId != null) {
                sendErrorToSession(session, ServerException(request.name, "Player already in room."))
                return
            }

            val roomName = request.name

            if (roomName.isBlank()) {
                sendErrorToSession(session, ServerException(request.name, "Room name cannot be empty"))
                return
            }

            val newRoom = GameRoomManager.createRoom(roomName)
            val isJoined = GameRoomManager.joinRoom(player.id, newRoom.id)
            if (!isJoined) {
                GameRoomManager.rooms.remove(newRoom.id)
                sendErrorToSession(session, ServerException(request.name, "Failed to join room after creation"))
                return
            }

            broadcastToRoom(request.name, PlayerConnectedResponse(player.id, GameRoomManager.getPlayersNames()))
            logger.info("Create -> ${GameRoomManager.getPlayersNames().first()}")

            sendToSession(session, RoomCreatedResponse(newRoom.id))
            sendToSession(session, InfoResponse("You have created and joined room: ${newRoom.name}"))

        } catch (e: Exception) {
            sendErrorToSession(session, ServerException(request.name, "Failed to create room: ${e.message}"))
            return
        }
    }

    suspend fun handleDisconnect(session: WebSocketSession) {
        val playerId = sessionToPlayerId.remove(session) ?: return
        val player = GameRoomManager.players[playerId] ?: return

        val roomId = player.roomId
        GameRoomManager.removePlayer(playerId)
        if (roomId == null) {
            return
        }

        val room = GameRoomManager.rooms[roomId]
        if (room != null && room.players.isEmpty()) {
            room.stopGameLoop()
            GameRoomManager.rooms.remove(roomId)
        }

        broadcastToRoom(roomId, PlayerDisconnectedResponse(playerId), null)

        if (GameRoomManager.rooms[roomId]?.players?.isEmpty() == true) {
            println("Room $roomId is empty, removing it")
            GameRoomManager.rooms.remove(roomId)
        }
    }

    suspend fun broadcastToRoom(roomId: String, message: ServerMessage, exceptPlayerId: String? = null) {
        val room = GameRoomManager.rooms[roomId] ?: return
        room.players.forEach { player ->
            logger.info("Broadcast ${player.name} в комнату ${room.id}")

            if (player.id != exceptPlayerId) {
                try {
                    sendToSession(player.session, message)

                } catch (e: Exception) {
                    println("Error broadcasting message to player ${player.id} in room $roomId: ${e.message}")
                }
            }
        }
    }

    suspend fun handleJoinRoom(session: WebSocketSession, request: JoinRoomRequest) {
        try {
            logger.info("Join работает")

            val playerId = sessionToPlayerId[session] ?: run {
                sendErrorToSession(session, ServerException(request.name, "Player is not init"))
                return
            }

            val player = GameRoomManager.players[playerId] ?: run {
                sendErrorToSession(session, ServerException(request.name, "Failed to find player with ID $playerId"))
                return
            }

            if (player.roomId != null) {
                sendErrorToSession(session, ServerException(request.name, "Player already in room"))
                return
            }

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

            if (room.state == GameRoomState.ONGOING || room.state == GameRoomState.ENDED) {
                sendErrorToSession(session, ServerException(request.name, "Game in room ${room.name} is already started!"))
                return
            }

            val isJoined = GameRoomManager.joinRoom(player.id, room.id)
            if (isJoined) {

                // sendToSession(session, JoinedRoomResponse(room.id))
                broadcastToRoom(request.name, PlayerConnectedResponse(player.id, GameRoomManager.getPlayersNames()))
                logger.info("Join -> ${GameRoomManager.getPlayersNames().first()}")

                sendToSession(session, InfoResponse("You have joined room: ${room.name}"))
            } else {
                sendErrorToSession(session, ServerException(request.name, "Failed to join room '${room.name}'"))
            }

        } catch (e: Exception) {
            sendErrorToSession(session, ServerException(request.name, "Failed to join room: ${e.message}"))
            return
        }
    }

    suspend fun handleLeaveRoom(session: WebSocketSession) {
        val name = "LEAVE_ROOM"
        try {

            val playerId = sessionToPlayerId[session] ?: run {
                sendErrorToSession(session, ServerException(name, "Player is not init"))
                return
            }

            val player = GameRoomManager.players[playerId] ?: run {
                sendErrorToSession(session, ServerException(name, "Player with ID $playerId not found"))
                return
            }

            val currentRoomId = player.roomId
            if (currentRoomId == null) {
                sendErrorToSession(session, ServerException(name, "Player is not in any room"))
                return
            }

            GameRoomManager.leaveRoom(playerId)

            sendToSession(session, LeftRoomResponse(currentRoomId))
            sendToSession(session, InfoResponse("You have left room: $currentRoomId"))

            val room = GameRoomManager.rooms[currentRoomId]
            if (room != null && room.players.isEmpty()) {
                println("Room $currentRoomId is empty, stopping loop and removing it.")
                room.stopGameLoop()
                GameRoomManager.rooms.remove(currentRoomId)
            }

            broadcastToRoom(currentRoomId, PlayerDisconnectedResponse(playerId), playerId)

            if (GameRoomManager.rooms[currentRoomId]?.players?.isEmpty() == true) {
                println("Room $currentRoomId is empty, removing it.")
                GameRoomManager.rooms.remove(currentRoomId)
            }

        } catch (e: Exception) {
            sendErrorToSession(session, ServerException(name, "Failed to leave room: ${e.message}"))
        }
    }

    suspend fun handleStartGame(session: WebSocketSession, request: StartGameRequest) {
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
                sendErrorToSession(session, ServerException(request.name, "There is no such room!"))
                return
            }

            val currentRoom = GameRoomManager.rooms[currentRoomId]!!

            if (player != currentRoom.players[0]) {
                sendErrorToSession(session, ServerException(request.name, "This player is not an admin!"))
                return
            }

            sendToSession(session, StartedGameResponse(currentRoomId, currentRoom.getGameMap()))
            sendToSession(session, InfoResponse("You have started the game in room $currentRoomId"))

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

            println("Player ${player.name} (${player.id}) in room $currentRoomId performed action: ${request.name}")

            sendToSession(session, PlayerActionResponse("Action '${request.name}' received."))
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
}