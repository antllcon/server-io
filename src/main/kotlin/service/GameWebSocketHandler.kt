package mobility.service

import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import mobility.manager.GameRoomManager
import mobility.model.ClientMessage
import mobility.model.ClientMessageType
import mobility.model.CreateRoomRequest
import mobility.model.ErrorResponse
import mobility.model.InfoResponse
import mobility.model.InitPlayerRequest
import mobility.model.JoinRoomRequest
import mobility.model.JoinedRoomResponse
import mobility.model.LeftRoomResponse
import mobility.model.PlayerActionRequest
import mobility.model.PlayerActionResponse
import mobility.model.PlayerConnectedResponse
import mobility.model.PlayerDisconnectedResponse
import mobility.model.RoomCreatedResponse
import mobility.model.RoomUpdatedResponse
import mobility.model.WebSocketMessage
import java.util.concurrent.ConcurrentHashMap

class GameWebSocketHandler {

    private val sessionToPlayerId = ConcurrentHashMap<WebSocketSession, String>()

    suspend fun handleSession(session: WebSocketServerSession) {
        try {
            for (frame in session.incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    val clientMessage = AppJson.decodeFromString<ClientMessage>(text)
                    handleIncoming(session, clientMessage)
                }
            }
        } catch (_: ClosedReceiveChannelException) {
            println("WebSocket session closed: $session")
        } catch (e: Exception) {
            println("WebSocket exception: $e")
            sendToSession(
                session,
                ErrorResponse("SESSION_ERROR", "An error occurred: ${e.message}")
            )
        } finally {
            println("Disconnect for session: $session")
            handleDisconnect(session)

        }
    }

    suspend fun handleIncoming(session: WebSocketSession, message: ClientMessage) {
        try {
            when (message.type) {
                ClientMessageType.INIT_PLAYER -> handleInitPlayer(session, message)
                ClientMessageType.CREATE_ROOM -> handleCreateRoom(session, message)
                ClientMessageType.JOIN_ROOM -> handleJoinRoom(session, message)
                ClientMessageType.LEAVE_ROOM -> handleLeaveRoom(session)
                ClientMessageType.PLAYER_ACTION -> handlePlayerAction(session, message)

            }
        } catch (e: Exception) {
            sendToSession(
                session,
                ErrorResponse(
                    "PROCESSING_ERROR",
                    "Error: ${e.message}"
                )
            )
        }
    }

    suspend fun handleInitPlayer(session: WebSocketSession, message: ClientMessage) {
        try {
            val request = message.data?.let {
                Json.decodeFromJsonElement<InitPlayerRequest>(it)
            } ?: throw IllegalArgumentException("Missing data for INIT_PLAYER")

            val player = GameRoomManager.registerPlayer(
                request.name,
                session
            )

            sessionToPlayerId[session] = player.id

            sendToSession(
                session,
                PlayerConnectedResponse(
                    player.id,
                    player.name
                )
            )

        } catch (e: Exception) {
            sendToSession(
                session,
                ErrorResponse(
                    "INIT_PLAYER_ERROR",
                    "Failed to init player: ${e.message}"
                )
            )
        }
    }

    suspend fun handleCreateRoom(session: WebSocketSession, message: ClientMessage) {
        try {
            val playerId = sessionToPlayerId[session] ?: run {
                sendToSession(
                    session,
                    ErrorResponse("AUTH_ERROR", "Player not initialized. Please send INIT_PLAYER first.")
                )
                return
            }

            val player = GameRoomManager.players[playerId] ?: run {
                sendToSession(session, ErrorResponse("PLAYER_NOT_FOUND", "Player with ID $playerId not found."))
                return
            }

            if (player.roomId != null) {
                sendToSession(
                    session,
                    ErrorResponse("ALREADY_IN_ROOM", "Player is already in a room: ${player.roomId}")
                )
                return
            }

            val request = message.data?.let {
                AppJson.decodeFromJsonElement<CreateRoomRequest>(it)
            } ?: throw IllegalArgumentException("Missing data for CREATE_ROOM")

            val roomName = request.name

            if (roomName.isBlank()) {
                sendToSession(session, ErrorResponse("INVALID_ROOM_NAME", "Room name cannot be empty."))
                return
            }

            val newRoom = GameRoomManager.createRoom(roomName)
            val joined = GameRoomManager.joinRoom(player.id, newRoom.id)
            if (!joined) {
                GameRoomManager.rooms.remove(newRoom.id)
                sendToSession(
                    session,
                    ErrorResponse("ROOM_JOIN_FAILED", "Failed to join newly created room: ${newRoom.id}")
                )
                return
            }

            sendToSession(
                session,
                RoomCreatedResponse(newRoom.id)
            )

        } catch (e: Exception) {
            sendToSession(
                session,
                ErrorResponse(
                    "CREATE_ROOM_ERROR",
                    "Failed to create room: ${e.message}"
                )
            )
        }
    }

    suspend fun handleDisconnect(session: WebSocketSession) {
        val playerId = sessionToPlayerId.remove(session)
        if (playerId == null) {
            return
        }

        val player = GameRoomManager.players[playerId]
        if (player == null) {
            return
        }

        val roomId = player.roomId
        GameRoomManager.removePlayer(playerId)
        if (roomId == null) {
            return
        }

        broadcastToRoom(roomId, PlayerDisconnectedResponse(playerId), null)

        if (GameRoomManager.rooms[roomId]?.players?.isEmpty() == true) {
            println("Room $roomId is empty, removing it.")
            GameRoomManager.rooms.remove(roomId)
        }
    }

    suspend fun broadcastToRoom(roomId: String, message: WebSocketMessage, exceptPlayerId: String? = null) {
        val room = GameRoomManager.rooms[roomId] ?: return
        room.players.forEach { player ->
            if (player.id != exceptPlayerId) {
                try {
                    sendToSession(player.session, message)
                } catch (e: Exception) {
                    println("Error broadcasting message to player ${player.id} in room $roomId: ${e.message}")
//                    GameRoomManager.removePlayer(player.id)
                }
            }
        }
    }

    suspend fun handleJoinRoom(session: WebSocketSession, message: ClientMessage) {
        try {
            val playerId = sessionToPlayerId[session] ?: run {
                sendToSession(
                    session,
                    ErrorResponse("AUTH_ERROR", "Player not initialized. Please send INIT_PLAYER first.")
                )
                return
            }

            val player = GameRoomManager.players[playerId] ?: run {
                sendToSession(session, ErrorResponse("PLAYER_NOT_FOUND", "Player with ID $playerId not found."))
                return
            }

            if (player.roomId != null) {
                sendToSession(
                    session,
                    ErrorResponse("ALREADY_IN_ROOM", "Player is already in a room: ${player.roomId}")
                )
                return
            }

            val request = message.data?.let {
                AppJson.decodeFromJsonElement<JoinRoomRequest>(it)
            } ?: throw IllegalArgumentException("Missing data for JOIN_ROOM")

            val targetRoomIdOrName = request.name // Предполагаем, что 'name' здесь - это roomId или имя комнаты
            val room = GameRoomManager.rooms[targetRoomIdOrName] // Попытка найти по ID
                ?: GameRoomManager.rooms.values.firstOrNull { it.name == targetRoomIdOrName } // Попытка найти по имени
                ?: run {
                    sendToSession(session, ErrorResponse("ROOM_NOT_FOUND", "Room '$targetRoomIdOrName' not found."))
                    return
                }

            if (room.isFull()) {
                sendToSession(session, ErrorResponse("ROOM_FULL", "Room '${room.name}' is full."))
                return
            }

            val joined = GameRoomManager.joinRoom(player.id, room.id)
            if (joined) {
                sendToSession(session, JoinedRoomResponse(room.id))
                // Уведомляем других игроков в комнате о новом игроке
                broadcastToRoom(room.id, RoomUpdatedResponse(room.id), player.id)
                sendToSession(session, InfoResponse("You have joined room: ${room.name}"))
            } else {
                sendToSession(session, ErrorResponse("JOIN_FAILED", "Failed to join room '${room.name}'."))
            }

        } catch (e: Exception) {
            sendToSession(
                session,
                ErrorResponse(
                    "JOIN_ROOM_ERROR",
                    "Failed to join room: ${e.message}"
                )
            )
        }
    }

    suspend fun handleLeaveRoom(session: WebSocketSession) {
        try {
            val playerId = sessionToPlayerId[session] ?: run {
                sendToSession(
                    session,
                    ErrorResponse("AUTH_ERROR", "Player not initialized. Please send INIT_PLAYER first.")
                )
                return
            }
            val player = GameRoomManager.players[playerId] ?: run {
                sendToSession(session, ErrorResponse("PLAYER_NOT_FOUND", "Player with ID $playerId not found."))
                return
            }

            val currentRoomId = player.roomId
            if (currentRoomId == null) {
                sendToSession(session, ErrorResponse("NOT_IN_ROOM", "Player is not in any room."))
                return
            }

            GameRoomManager.leaveRoom(playerId)

            sendToSession(session, LeftRoomResponse(currentRoomId))
            sendToSession(session, InfoResponse("You have left room: $currentRoomId"))

            broadcastToRoom(currentRoomId, PlayerDisconnectedResponse(playerId), playerId)

            if (GameRoomManager.rooms[currentRoomId]?.players?.isEmpty() == true) {
                println("Room $currentRoomId is empty, removing it.")
                GameRoomManager.rooms.remove(currentRoomId)
            }

        } catch (e: Exception) {
            sendToSession(
                session,
                ErrorResponse(
                    "LEAVE_ROOM_ERROR",
                    "Failed to leave room: ${e.message}"
                )
            )
        }
    }

    suspend fun handlePlayerAction(session: WebSocketSession, message: ClientMessage) {
        try {
            val playerId = sessionToPlayerId[session] ?: run {
                sendToSession(
                    session,
                    ErrorResponse("AUTH_ERROR", "Player not initialized. Please send INIT_PLAYER first.")
                )
                return
            }
            val player = GameRoomManager.players[playerId] ?: run {
                sendToSession(session, ErrorResponse("PLAYER_NOT_FOUND", "Player with ID $playerId not found."))
                return
            }

            val currentRoomId = player.roomId
            if (currentRoomId == null) {
                sendToSession(session, ErrorResponse("NOT_IN_ROOM", "Player is not in any room to perform actions."))
                return
            }

            val request = message.data?.let {
                AppJson.decodeFromJsonElement<PlayerActionRequest>(it)
            } ?: throw IllegalArgumentException("Missing data for PLAYER_ACTION")

            // TODO: логика обработки действий (их валидность)

            println("Player ${player.name} (${player.id}) in room $currentRoomId performed action: ${request.name}")

            sendToSession(session, PlayerActionResponse("Action '${request.name}' received."))

            broadcastToRoom(
                currentRoomId,
                PlayerActionResponse("Player ${player.name} performed action: ${request.name}"),
                playerId
            )

        } catch (e: Exception) {
            sendToSession(
                session,
                ErrorResponse(
                    "PLAYER_ACTION_ERROR",
                    "Failed to process player action: ${e.message}"
                )
            )
        }
    }
}