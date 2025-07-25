package mobility.model

import domain.GameMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mobility.domain.Vector2D
import mobility.domain.detectCollision
import mobility.domain.handleCollision
import mobility.service.GameWebSocketHandler
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class GameRoomState {
    LOBBY,
    COUNTDOWN,
    ONGOING,
    ENDED
}

class GameRoom(
    val id: String = (1..6).map { ('A'..'Z').random() }.joinToString(""),
    val name: String,
    val players: MutableList<Player> = mutableListOf(),
    var state: GameRoomState = GameRoomState.LOBBY,
    var secondsBeforeStart: Float = 5f,
    private val maxPlayers: Int = 6
) {
    companion object {
        const val RINGS_TO_CROSS_TO_FINISH = 3
    }

    val playerInputs = ConcurrentHashMap<String, PlayerInputRequest>()
    private val logger = LoggerFactory.getLogger(GameRoom::class.java)
    private var gameLoopJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val serverTickRate = 50L

    private val gameMap = GameMap.generateDungeonMap()

    fun isFull(): Boolean = players.size >= maxPlayers

    fun getGameMap(): Array<IntArray> {
        return gameMap.grid
    }

    fun startCountdown(handler: GameWebSocketHandler) {
        scope.launch {
            while (secondsBeforeStart > 0) {
                secondsBeforeStart -= serverTickRate / 1000f

                handler.broadcastToRoom(id, GameCountdownUpdateResponse(secondsBeforeStart))

                delay(serverTickRate)
            }
        }

        state = GameRoomState.ONGOING
        startGameLoop(handler)
    }

    private fun startGameLoop(handler: GameWebSocketHandler) {
        if (state != GameRoomState.ONGOING || gameLoopJob?.isActive == true) return
        var playersFinished = 0

        gameLoopJob = scope.launch {
            while (isActive) {
                val deltaTime = serverTickRate / 100f

                // 1. Обновляем физику
                players.forEach { player ->
                    if (player.ringsCrossed != RINGS_TO_CROSS_TO_FINISH) {
                        val input = playerInputs[player.id] ?: PlayerInputRequest(false, 0f, serverTickRate.toFloat(), player.ringsCrossed)

                        logger.info("Player ${player.id} - Input: isAccelerating=${input.isAccelerating}, turnDirection=${input.turnDirection}")
                        logger.info("Player ${player.id} - Car before update: Pos=${player.car.position}, Dir=${player.car.direction}, Speed=${player.car.speed}, Turning=${player.car.direction}")

                        if (input.isAccelerating) player.car.accelerate(deltaTime)
                        else player.car.decelerate(deltaTime)

                        //FIXME: it's possible that it may cause some errors during collisions
                        //FIXME: because the collision itself may be handled twice
                        for (otherPlayer in players) {
                            if (otherPlayer != player) {
                                val collisionResult = detectCollision(player.car, otherPlayer.car)
                                if (collisionResult.isColliding) {
                                    handleCollision(player.car, otherPlayer.car, collisionResult)
                                }
                            }
                        }

                        if (input.turnDirection != 0f) {
                            player.car.startTurn(input.turnDirection)
                        } else {
                            player.car.stopTurn()
                        }

                        val cellX = player.car.position.x.toInt().coerceIn(0, gameMap.size - 1)
                        val cellY = player.car.position.y.toInt().coerceIn(0, gameMap.size - 1)
                        player.car.setSpeedModifier(gameMap.getSpeedModifier(Vector2D(cellX.toFloat(), cellY.toFloat())))

                        player.car.update(deltaTime)
                        player.ringsCrossed = input.ringsCrossed
                        player.secondsAfterStart += deltaTime

                        if (player.ringsCrossed == RINGS_TO_CROSS_TO_FINISH) {
                            playersFinished += 1
                        }
                    }
//                    logger.info("Player ${player.id} - Car after update: Pos=${player.car.position}, Dir=${player.car.direction}, Speed=${player.car.speed}, Turning=${player.car.direction}")
                }

                if (playersFinished == players.size) {
                    val finalInfo: MutableMap<String, Long> = mutableMapOf()

                    players.forEach { player ->
                        finalInfo[player.name] = player.secondsAfterStart.toLong()
                    }

                    handler.broadcastToRoom(id, GameStopResponse(finalInfo))
                    stopGameLoop()
                }

                // 2. Собираем состояние
                val playerStates = players.map { p ->
                    val dto = PlayerStateDto(
                        id = p.id,
                        posX = p.car.position.x,
                        posY = p.car.position.y,
                        visualDirection = p.car.visualDirection,
                        speed = p.car.speed,
                        isAccelerating = p.car.isAccelerating,
                        isFinished = p.ringsCrossed == RINGS_TO_CROSS_TO_FINISH
                    )
                    logger.info("Server: Sending PlayerStateDto for ${p.id}: PosX=${dto.posX}, PosY=${dto.posY}, Direction=${dto.visualDirection}")
                    dto
                }

                // 3. Рассылаем всем в комнате
                handler.broadcastToRoom(id, GameStateUpdateResponse(playerStates))

                delay(serverTickRate)
            }
        }
    }

    fun stopGameLoop() {
        state = GameRoomState.ENDED

        gameLoopJob?.cancel()
        gameLoopJob = null
    }
}