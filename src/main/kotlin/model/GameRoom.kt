package mobility.model

import domain.Car
import mobility.manager.CheckpointManager
import domain.GameMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mobility.domain.Vector2D
import mobility.manager.CollisionManager
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
    var players: MutableList<Player> = mutableListOf(),
    var state: GameRoomState = GameRoomState.LOBBY,
    private val maxPlayers: Int = 6,
) {
    companion object {
        const val RINGS_TO_CROSS_TO_FINISH = 3
    }

    private val logger = LoggerFactory.getLogger(GameRoom::class.java)
    private var gameLoopJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val serverTickRateMs = 16L
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
        val initialPlayerStates = mutableListOf<Vector2D>()

        players.forEachIndexed { index, player ->
            val startPositionOffset = Vector2D(index * 0.4f, 0f)
            val basePosition = Vector2D(map.startCellPos.x + 0.2f, map.startCellPos.y + 0.6f)
            val finalPosition = Vector2D(basePosition.x + startPositionOffset.x, basePosition.y + startPositionOffset.y)

            logger.info(finalPosition.toString())

            player.car = Car(
                id = player.id,
                playerName = player.name,
                position = finalPosition,
                direction = map.startAngle,
                visualDirection = map.startAngle
            )

            initialPlayerStates.add(finalPosition)

        }

        return StarterPack(
            mapGrid = map.grid,
            mapWidth = map.width,
            mapHeight = map.height,
            initialPlayerStates = initialPlayerStates.toList(),
            startAngle = map.startAngle,
            route = map.route
        )
    }

    suspend fun startCountdown(handler: GameWebSocketHandler) {
        withContext(Dispatchers.IO) {
           for (second in 0 until 6) {
               delay(1000)
               handler.broadcastToRoom(id, GameCountdownUpdateResponse(5 - second))
           }
        }

        state = GameRoomState.ONGOING
        startGameLoop(handler)
    }

    private fun startGameLoop(handler: GameWebSocketHandler) {
        if (state != GameRoomState.ONGOING || gameLoopJob?.isActive == true) return

        gameLoopJob = scope.launch {
            var lastTime = System.currentTimeMillis()

            while (isActive) {
                val currentTime = System.currentTimeMillis()
                val deltaTime = (currentTime - lastTime) / 1000f
                lastTime = currentTime

                processPlayerInputs(deltaTime)
                CollisionManager.checkAndResolveCollisions(players)
                updatePlayersTime(deltaTime)
                sendGameStateUpdate(handler)

                delay(serverTickRateMs)
            }
        }
    }

    private fun updatePlayersTime(elapsedTime: Float) {
        for (player in players) {
            if (!player.isFinished) {
                player.secondsAfterStart += elapsedTime
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
            }
        }
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
                isFinished = player.isFinished,
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