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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GameRoom(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val players: MutableList<Player> = mutableListOf(),
    val maxPlayers: Int = 4
) {
    val playerInputs = ConcurrentHashMap<String, PlayerInputRequest>()
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

                    if (input.isAccelerating) player.car.accelerate(deltaTime)
                    else player.car.decelerate(deltaTime)

                    player.car.startTurn(input.turnDirection)

                    val cellX = player.car.position.x.toInt().coerceIn(0, gameMap.size - 1)
                    val cellY = player.car.position.y.toInt().coerceIn(0, gameMap.size - 1)
                    player.car.setSpeedModifier(gameMap.getSpeedModifier(cellX, cellY))

                    player.car.update(deltaTime)
                }

                // 2. Собираем состояние
                val playerStates = players.map { p ->
                    PlayerStateDto(
                        id = p.id,
                        posX = p.car.position.x,
                        posY = p.car.position.y,
                        direction = p.car.visualDirection
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