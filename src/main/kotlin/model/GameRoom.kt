package mobility.model

import domain.GameMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mobility.service.GameWebSocketHandler
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GameRoom(
    val id: String = UUID.randomUUID().toString(),  
    val name: String,
    val players: MutableList<Player> = mutableListOf(),
    val maxPlayers: Int = 4
) {
    val playerInputs = ConcurrentHashMap<String, PlayerInputRequest>()
    private val logger = LoggerFactory.getLogger(GameRoom::class.java)
    private var gameLoopJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val gameMap = GameMap.createRaceTrackMap()

    fun isFull(): Boolean = players.size >= maxPlayers

    fun startGameLoop(handler: GameWebSocketHandler) {
        if (gameLoopJob?.isActive == true) return

        val serverTickRate = 50L

        gameLoopJob = scope.launch {
            while (isActive) {
                val deltaTime = serverTickRate / 1000f

                // 1. Обновляем физику
                players.forEach { player ->
                    val input = playerInputs[player.id] ?: PlayerInputRequest(false, 0f)

                    logger.info("Player ${player.id} - Input: isAccelerating=${input.isAccelerating}, turnDirection=${input.turnDirection}")
                    logger.info("Player ${player.id} - Car before update: Pos=${player.car.position}, Dir=${player.car.direction}, Speed=${player.car.speed}, Turning=${player.car.direction}")

                    if (input.isAccelerating) player.car.accelerate(deltaTime)
                    else player.car.decelerate(deltaTime)

                    if (input.turnDirection != 0f) {
                        player.car.startTurn(input.turnDirection)
                    } else {
                        player.car.stopTurn()
                    }

                    val cellX = player.car.position.x.toInt().coerceIn(0, gameMap.size - 1)
                    val cellY = player.car.position.y.toInt().coerceIn(0, gameMap.size - 1)
                    player.car.setSpeedModifier(gameMap.getSpeedModifier(cellX, cellY))

                    player.car.update(deltaTime)
                    logger.info("Player ${player.id} - Car after update: Pos=${player.car.position}, Dir=${player.car.direction}, Speed=${player.car.speed}, Turning=${player.car.direction}")
                }

                // 2. Собираем состояние
                val playerStates = players.map { p ->
                    PlayerStateDto(
                        id = p.id,
                        posX = p.car.position.x,
                        posY = p.car.position.y,
                        direction = p.car.direction
                    )
                }

                // 3. Рассылаем всем в комнате
                handler.broadcastToRoom(id, GameStateUpdateResponse(playerStates))

                delay(serverTickRate)
            }
        }
    }

    fun stopGameLoop() {
        gameLoopJob?.cancel()
        gameLoopJob = null
    }
}