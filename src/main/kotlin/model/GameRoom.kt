package mobility.model

import manager.ActivePlayerEffect
import manager.BonusSpawnPoint
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
import manager.BonusType
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
    private val bonusSpawnPoints = mutableListOf<BonusSpawnPoint>()
    private val activePlayerEffects = ConcurrentHashMap<String, ActivePlayerEffect>()

    fun isFull(): Boolean = players.size >= maxPlayers

    fun getGameMap(): Array<IntArray> {
        return gameMap?.grid ?: return arrayOf()
    }

    fun initGameAndCreateStarterPack(): StarterPack? {
        if (state != GameRoomState.LOBBY) return null

        val map = GameMap.generateDungeonMap()
        this.gameMap = map
        this.checkpointManager = CheckpointManager(map.route)
        bonusSpawnPoints.clear()
        map.bonusPoints.forEachIndexed { index, bonusPosition ->
            val newPosition = Vector2D(bonusPosition.x + 0.4f, bonusPosition.y + 0.4f)
            bonusSpawnPoints.add(
                BonusSpawnPoint(id = index, position = newPosition)
            )
        }

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

                updateBonuses(deltaTime, handler)
                processPlayerInputs(deltaTime)
                CollisionManager.checkAndResolveCollisions(players)
                updatePlayersTime(deltaTime)
                sendGameStateUpdate(handler)

                delay(serverTickRateMs)
            }
        }
    }

    private suspend fun updateBonuses(deltaTime: Float, handler: GameWebSocketHandler) {
        // Обновляем таймер респавна
        bonusSpawnPoints.forEach { spawnPoint ->
            if (!spawnPoint.isActive) {
                spawnPoint.respawnCooldown -= deltaTime
                if (spawnPoint.respawnCooldown <= 0) {
                    spawnPoint.reset()
                }
            }
        }

        // Обновляем таймеры активных эффектов у игроков
        activePlayerEffects.forEach { (playerId, effect) ->
            effect.durationRemaining -= deltaTime
            if (effect.durationRemaining <= 0) {
                activePlayerEffects.remove(playerId)
            }
        }

        // Проверяем подбор бонусов
        val playersToCheck = players.filter { !it.isFinished && it.car != null }
        playersToCheck.forEach { player ->
            val car = player.car!!
            bonusSpawnPoints.forEach { spawnPoint ->
                if (spawnPoint.isActive) {
                    val distance = (car.position - spawnPoint.position).magnitude()
                    if (distance < 0.5f) {
                        spawnPoint.onPickup()

                        val effect = ActivePlayerEffect(
                            playerId = player.id,
                            type = spawnPoint.type,
                            durationRemaining = 5.0f
                        )
                        activePlayerEffects[player.id] = effect

                        handler.broadcastToRoom(
                            id,
                            BonusPickedUpResponse(player.name, spawnPoint.type.name)
                        )
                    }
                }
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
                val effect = activePlayerEffects[player.id]

                var speedMultiplier = 1.0f
                var sizeMultiplier = 1.0f

                if (effect != null) {
                    when (effect.type) {
                        BonusType.SPEED_BOOST -> speedMultiplier = 1.5f
                        BonusType.MASS_INCREASE -> sizeMultiplier = 2.0f
                    }
                }

                val carWithBonuses = player.car!!.copy(
                    bonusSpeedMultiplier = speedMultiplier,
                    sizeModifier = sizeMultiplier
                )

                val newDirection = if (input.visualDirection == 0f) {
                    null
                } else {
                    input.visualDirection
                }

                player.car = carWithBonuses.update(
                    elapsedTime = deltaTime,
                    directionAngle = newDirection,
                    speedModifier = speedMultiplier
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
                    direction = 0f,
                    visualDirection = 0f,
                    speed = 0f,
                    isFinished = false,
                    sizeModifier = 1f
                )
            }

            PlayerStateDto(
                id = player.car!!.playerName,
                posX = player.car!!.position.x,
                posY = player.car!!.position.y,
                direction = player.car!!.direction,
                visualDirection = player.car!!.visualDirection,
                speed = player.car!!.speed,
                isFinished = player.isFinished,
                sizeModifier = player.car!!.sizeModifier
            )
        }.toList()

        val bonusStates = bonusSpawnPoints.map { spawnPoint ->
            BonusDto(
                id = spawnPoint.id,
                type = spawnPoint.type.name,
                posX = spawnPoint.position.x,
                posY = spawnPoint.position.y,
                isActive = spawnPoint.isActive
            )
        }

        handler.broadcastToRoom(id, GameStateUpdateResponse(playerStates, bonusStates))
    }


    fun stopGameLoop() {
        state = GameRoomState.ENDED

        gameLoopJob?.cancel()
        gameLoopJob = null
    }
}