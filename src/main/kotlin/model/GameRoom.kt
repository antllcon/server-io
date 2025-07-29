package mobility.model

import domain.Car
import domain.CheckpointManager
import domain.GameMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mobility.domain.Vector2D
import mobility.service.GameWebSocketHandler
import org.slf4j.LoggerFactory
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
    private val maxPlayers: Int = 6,
) {
    companion object {
        const val RINGS_TO_CROSS_TO_FINISH = 3
    }

    private val logger = LoggerFactory.getLogger(GameRoom::class.java)
    private var gameLoopJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val serverTickRateMs = 50L
    private val serverTickRateSeconds = serverTickRateMs / 1000f
    private var gameMap: GameMap? = null
    private var checkpointManager: CheckpointManager? = null
    val playerInputs = ConcurrentHashMap<String, PlayerInputRequest>()

    fun isFull(): Boolean = players.size >= maxPlayers

    fun getGameMap(): Array<IntArray> {
        return gameMap?.grid ?: return arrayOf()
    }

    fun initGameAndCreateStarterPack(): StarterPack? {
        if (state != GameRoomState.LOBBY) return null

        val map = GameMap.generateDungeonMap()
        this.gameMap = map
        this.checkpointManager = CheckpointManager(map.route)

        val initialDirection = when (map.startDirection) {
            GameMap.StartDirection.HORIZONTAL -> Car.DIRECTION_RIGHT
            GameMap.StartDirection.VERTICAL -> Car.DIRECTION_UP
        }

        val initialPlayerStates = mutableListOf<Vector2D>()

        // TODO: подкорректировать
        players.forEachIndexed { index, player ->
            val startPositionOffset = Vector2D(index * 1f, 0f)
            val basePosition = Vector2D(map.startCellPos.x + 0.2f, map.startCellPos.y + 0.6f)
            val finalPosition = Vector2D(basePosition.x + startPositionOffset.x, basePosition.y + startPositionOffset.y)

            logger.info(finalPosition.toString())

            player.car = Car(
                id = player.id,
                playerName = player.name,
                position = finalPosition,
                direction = initialDirection,
                visualDirection = initialDirection
            )

            initialPlayerStates.add(finalPosition)

        }

        return StarterPack(
            mapGrid = map.grid,
            mapWidth = map.width,
            mapHeight = map.height,
            initialPlayerStates = initialPlayerStates.toList(),
            startDirection = map.startDirection,
            route = map.route
        )
    }

    fun startCountdown(handler: GameWebSocketHandler) {
        scope.launch {
            while (secondsBeforeStart > 0) {
                secondsBeforeStart -= serverTickRateSeconds
                if (secondsBeforeStart < 0) secondsBeforeStart = 0f

                handler.broadcastToRoom(id, GameCountdownUpdateResponse(secondsBeforeStart))

                delay(serverTickRateMs)
            }
        }

        state = GameRoomState.ONGOING
        startGameLoop(handler)
    }

    private fun startGameLoop(handler: GameWebSocketHandler) {
        if (state != GameRoomState.ONGOING || gameLoopJob?.isActive == true) return
//        val playersFinished = 0

        gameLoopJob = scope.launch {
            var lastTime = System.currentTimeMillis()

            while (isActive) {
                val currentTime = System.currentTimeMillis()
                val deltaTime = (currentTime - lastTime) / 1000f
                lastTime = currentTime

                // внутри игровая логика
                // + обработка коллизий в будущем
                processPlayerInputs(deltaTime)
//                movePlayers(deltaTime)
                sendGameStateUpdate(handler)

                delay(serverTickRateMs)
            }
        }
    }

    private fun processPlayerInputs(deltaTime: Float) {
        players.forEach { player ->
            playerInputs[player.id]?.let { input ->
                val speedModifier = gameMap?.getSpeedModifier(player.car!!.position) ?: 1f
                val newDirection = if (input.visualDirection == 0f) {
                    null
                } else {
                    input.visualDirection
                }

                player.car = player.car!!.update(
                    elapsedTime = deltaTime,
                    directionAngle = newDirection,
                    speedModifier = speedModifier
                )

                // TODO: Здесь также можно обрабатывать ringsCrossed,
                // например, обновлять счетчик колец для игрока на сервере.
                // checkpointManager?.recordCheckpoint(player.car.id, input.ringsCrossed) // Пример
            }
        }
        // playerInputs.clear()
    }

    private fun movePlayers(deltaTime: Float) {
        players.forEach { player ->
            val speedModifier = gameMap?.getSpeedModifier(player.car!!.position) ?: 1f
            player.car = player.car!!.update(
                elapsedTime = deltaTime,
                directionAngle = null,
                speedModifier = speedModifier
            )
        }
    }

    private suspend fun sendGameStateUpdate(handler: GameWebSocketHandler) {
        val playerStates: List<PlayerStateDto> = players.map { player ->
            player.car ?: run {
                PlayerStateDto(
                    id = player.car!!.playerName,
                    posX = 0f,
                    posY = 0f,
                    visualDirection = 0f,
                    speed = 0f,
                    isFinished = false
                )
            }

            // TODO: хз почему car не видно
            PlayerStateDto(
                id = player.car!!.playerName,
                posX = player.car!!.position.x,
                posY = player.car!!.position.y,
                visualDirection = player.car!!.visualDirection,
                speed = player.car!!.speed,
                isFinished = false,
            )
        }.toList()

        handler.broadcastToRoom(id, GameStateUpdateResponse(playerStates))
    }


    fun stopGameLoop() {
        state = GameRoomState.ENDED

        gameLoopJob?.cancel()
        gameLoopJob = null
    }
}