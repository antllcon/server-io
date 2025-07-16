package domain

import mobility.domain.Vector2D
import kotlin.math.*

class Car(
    val playerName: String = "Player",
    var isPlayer: Boolean = true,
    var isMultiplayer: Boolean = false,
    var id: String = "1",
    initialPosition: Vector2D = Vector2D.Zero
) {
    companion object {
        const val MIN_SPEED = 0f
        const val MAX_SPEED = 0.2f
        const val ACCELERATION = 0.02f
        const val DECELERATION = 0.1f
        const val BASE_TURN_RATE = 1.2f
        const val DRIFT_TURN_RATE = 2.5f
        const val DRIFT_SPEED_THRESHOLD = 1.8f
        const val WIDTH = 0.035f
        const val LENGTH = 0.06f
        const val MAP_SIZE = 10f
        const val VISUAL_LAG_SPEED = 0.05f
        const val DRIFT_ANGLE_OFFSET = 0.2f
    }

    var position: Vector2D = initialPosition

    private var _speed: Float = 0f
    private var _direction: Float = 0f
    private var _visualDirection: Float = 0f
    private var _turnInput: Float = 0f
    private var _isDrifting: Boolean = false
    private var _speedModifier: Float = 1f
    private var _targetSpeed: Float = 0f
    private var _targetTurnInput: Float = 0f

    val speed: Float get() = _speed
    val direction: Float get() = _direction
    var visualDirection: Float = 0.0f
    val isDrifting: Boolean get() = _isDrifting

    fun setSpeedModifier(modifier: Float) {
        _speedModifier = modifier.coerceIn(0f, 1f)
    }

    fun update(deltaTime: Float) {
        val safeDeltaTime = deltaTime.coerceIn(0.001f, 0.1f)
        updateTurnInput(safeDeltaTime)
        updateDriftState()
        updateTurning(safeDeltaTime)
        updatePosition(safeDeltaTime)
        updateVisualDirection(safeDeltaTime)
    }

    fun accelerate(deltaTime: Float) {
        _targetSpeed = min(MAX_SPEED * _speedModifier, _targetSpeed + ACCELERATION * deltaTime * _speedModifier)
        _speed = lerp(_speed, _targetSpeed, 0.1f, deltaTime)
    }

    fun decelerate(deltaTime: Float) {
        _targetSpeed = max(MIN_SPEED, _targetSpeed - DECELERATION * deltaTime * _speedModifier)
        _speed = lerp(_speed, _targetSpeed, 0.1f, deltaTime)
    }

    private fun lerp(start: Float, end: Float, factor: Float, deltaTime: Float): Float {
        return start + (end - start) * factor * deltaTime * 60f
    }

    fun startTurn(direction: Float) {
        _targetTurnInput = direction.coerceIn(-1f, 1f)
    }

    fun stopTurn() {
        _targetTurnInput = 0f
    }

    fun reset(position: Vector2D = Vector2D(5f, 5f)) {
        this.position = position
        _speed = 0f
        _direction = 0f
        _visualDirection = 0f
        _turnInput = 0f
        _isDrifting = false
        _speedModifier = 1f
    }

    private fun updateTurnInput(deltaTime: Float) {
        _turnInput = lerp(_turnInput, _targetTurnInput, 0.2f, deltaTime)
    }

    private fun updateDriftState() {
        _isDrifting = _speed > DRIFT_SPEED_THRESHOLD * _speedModifier && abs(_turnInput) > 0.7f
    }

    private fun updateTurning(deltaTime: Float) {
        if (_speed == 0f || _turnInput == 0f) return
        val turnRate = if (_isDrifting) DRIFT_TURN_RATE else BASE_TURN_RATE
        val turnAmount = _turnInput * turnRate * deltaTime * sqrt(_speed / MAX_SPEED)
        _direction += turnAmount
    }

    private fun updatePosition(deltaTime: Float) {
        if (_speed == 0f) return
        val moveDistance = _speed * deltaTime
        val maxMove = MAP_SIZE * 0.5f
        val actualMove = moveDistance.coerceIn(-maxMove, maxMove)

        // Используем наш Vector2D
        val newPosition = Vector2D(
            x = (position.x + actualMove * cos(_direction)).coerceIn(WIDTH, MAP_SIZE - WIDTH),
            y = (position.y + actualMove * sin(_direction)).coerceIn(WIDTH, MAP_SIZE - WIDTH)
        )
        position = newPosition
    }

    private fun updateVisualDirection(deltaTime: Float) {
        val targetDirection = if (_isDrifting) {
            _direction + (DRIFT_ANGLE_OFFSET * _turnInput)
        } else {
            _direction
        }
        val lagFactor = VISUAL_LAG_SPEED * deltaTime * 60f
        _visualDirection = lerp(_visualDirection, targetDirection, lagFactor.coerceIn(0f, 0.5f), deltaTime)
    }
}