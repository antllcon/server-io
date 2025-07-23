package domain

import mobility.domain.Vector2D
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.random.Random

class GameMap private constructor(
    val grid: Array<IntArray>,
    val width: Int,
    val height: Int,
    val finishCellPos: Vector2D
) {
    enum class TerrainType(val speedModifier: Float) {
        ROAD(speedModifier = 1.0f),
        GRASS(speedModifier = 0.5f),
        ABYSS(speedModifier = .0f),
        WATER(speedModifier = 0.3f)
    }

    companion object {
        private const val DEFAULT_MAP_WIDTH = 13
        private const val DEFAULT_MAP_HEIGHT = 13
        private const val DEFAULT_CORE_POINT = 6
        private const val DEFAULT_WATER_PROPABILITY = 0.2f

        fun generateDungeonMap(
            width: Int = DEFAULT_MAP_WIDTH,
            height: Int = DEFAULT_MAP_HEIGHT,
            roomCount: Int = DEFAULT_CORE_POINT,
            waterProbability: Float = DEFAULT_WATER_PROPABILITY
        ): GameMap {
            val grid: Array<IntArray> = Array(size = height) { IntArray(size = width) }

            val maxPossibleRooms = ((width - 2) / 2 + 1) * ((height - 2) / 2 + 1)
            val actualRoomCount = roomCount.coerceAtMost(maxPossibleRooms)

            generateCoresInternal(grid, actualRoomCount)
            generateRoadsInternal(grid)
            removeDeadEndsInternal(grid)
            val startCellPos: Vector2D = findStartCellInternal(grid)
            createWaterCellsInternal(grid, startCellPos, waterProbability)
            determinationCellTypesInternal(grid)
            return GameMap(grid, width, height, startCellPos)
        }

        private fun generateCoresInternal(grid: Array<IntArray>, roomCount: Int) {
            val width: Int = grid[0].size
            val height: Int = grid.size

            var generatedRooms = 0
            val maxAttempts = roomCount * 5
            var attempts = 0

            while (generatedRooms < roomCount && attempts < maxAttempts) {
                val x: Int = (Random.nextInt(until = (width - 2) / 2 + 1) * 2) + 1
                val y: Int = (Random.nextInt(until = (height - 2) / 2 + 1) * 2) + 1

                if (x >= 1 && x < width - 1 && y >= 1 && y < height - 1) {
                    if (grid[y][x] == 0) {
                        grid[y][x] = 1
                        generatedRooms++
                    }
                }
                attempts++
            }
        }

        private fun generateRoadsInternal(grid: Array<IntArray>) {
            val rooms: MutableList<Vector2D> = mutableListOf()

            for (y in 1 until grid.size - 1) {
                for (x in 1 until grid[y].size - 1) {
                    if (grid[y][x] == 1) rooms.add(Vector2D(x.toFloat(), y.toFloat()))
                }
            }

            if (rooms.size < 2) return

            val centerX: Float = grid[0].size / 2f
            val centerY: Float = grid.size / 2f

            rooms.sortWith(compareBy {
                atan2((it.y - centerY).toDouble(), (it.x - centerX).toDouble()).toFloat()
            })

            rooms.add(rooms.first())

            for (i in 1 until rooms.size) {
                val x1 = rooms[i - 1].x.toInt()
                val y1 = rooms[i - 1].y.toInt()
                val x2 = rooms[i].x.toInt()
                val y2 = rooms[i].y.toInt()

                carvePath(
                    grid,
                    Vector2D(x1.toFloat(), y1.toFloat()),
                    Vector2D(x2.toFloat(), y2.toFloat())
                )
            }
        }

        private fun carvePath(grid: Array<IntArray>, start: Vector2D, end: Vector2D) {
            var x1 = start.x.toInt()
            var y1 = start.y.toInt()
            val x2 = end.x.toInt()
            val y2 = end.y.toInt()

            while (x1 != x2) {
                x1 += if (x2 > x1) 1 else -1
                if (grid[y1][x1] == 0) grid[y1][x1] = 2
            }

            while (y1 != y2) {
                y1 += if (y2 > y1) 1 else -1
                if (grid[y1][x1] == 0) grid[y1][x1] = 2
            }
        }

        private fun removeDeadEndsInternal(grid: Array<IntArray>) {
            var removedSomething: Boolean
            do {
                removedSomething = false
                val deadEnds = findDeadEndCells(grid)

                for ((x, y) in deadEnds) {
                    if (getRoadNeighbors(grid, x, y).size == 1) {
                        grid[y][x] = 0
                        removedSomething = true
                    }
                }
            } while (removedSomething)
        }

        private fun findDeadEndCells(grid: Array<IntArray>): List<Pair<Int, Int>> {
            val deadEnds = mutableListOf<Pair<Int, Int>>()
            for (y in 1 until grid.size - 1) {
                for (x in 1 until grid[0].size - 1) {
                    if (grid[y][x] in listOf(1, 2) && getRoadNeighbors(grid, x, y).size == 1) {
                        deadEnds.add(x to y)
                    }
                }
            }
            return deadEnds
        }

        private fun getRoadNeighbors(grid: Array<IntArray>, x: Int, y: Int): List<Pair<Int, Int>> {
            val neighbors = mutableListOf<Pair<Int, Int>>()
            val directions = listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0)
            for ((dx, dy) in directions) {
                val nx = x + dx
                val ny = y + dy
                if (ny in grid.indices && nx in grid[ny].indices && grid[ny][nx] in listOf(1, 2)) {
                    neighbors.add(nx to ny)
                }
            }
            return neighbors
        }

        private fun createWaterCellsInternal(
            grid: Array<IntArray>,
            startPosition: Vector2D,
            waterProbability: Float
        ) {
            val waterRoomCode = 300

            for (y in 1 until grid.size - 1) {
                for (x in 1 until grid[y].size - 1) {
                    if (grid[y][x] / 100 == 2 &&
                        !(x == startPosition.x.toInt() && y == startPosition.y.toInt())
                    ) {

                        if (Random.nextFloat() < waterProbability) {
                            grid[y][x] = waterRoomCode
                        }
                    }
                }
            }
        }

        private fun determinationCellTypesInternal(grid: Array<IntArray>) {
            val width = grid[0].size
            val height = grid.size

            for (y in 1 until height - 1) {
                for (x in 1 until width - 1) {
                    var index = 0
                    val currentCellType = grid[y][x]

                    val top = grid[y - 1][x]
                    val bottom = grid[y + 1][x]
                    val left = grid[y][x - 1]
                    val right = grid[y][x + 1]

                    if (currentCellType == 1) {
                        if (top == 0 && bottom == 0 && left != 0 && right != 0) index = 101
                        else if (top != 0 && bottom != 0 && left == 0 && right == 0) index = 102
                        else if (top != 0 && bottom == 0 && left == 0 && right != 0) index = 103
                        else if (top != 0 && bottom == 0 && left != 0 && right == 0) index = 104
                        else if (top == 0 && bottom != 0 && left == 0 && right != 0) index = 105
                        else if (top == 0 && bottom != 0 && left != 0 && right == 0) index = 106
                        else index = 100

                    } else if (currentCellType == 2) {
                        if (top == 0 && bottom == 0 && left != 0 && right != 0) index = 201
                        else if (top != 0 && bottom != 0 && left == 0 && right == 0) index = 202
                        else if (top != 0 && bottom == 0 && left == 0 && right != 0) index = 203
                        else if (top != 0 && bottom == 0 && left != 0 && right == 0) index = 204
                        else if (top == 0 && bottom != 0 && left == 0 && right != 0) index = 205
                        else if (top == 0 && bottom != 0 && left != 0 && right == 0) index = 206
                        else index = 200
                    }

                    if (index != 0) {
                        grid[y][x] = index
                    }
                }
            }
        }

        private fun findStartCellInternal(grid: Array<IntArray>): Vector2D {
            var startRoom = Vector2D(x = grid[0].size.toFloat(), y = grid.size.toFloat())

            for (y: Int in 1 until grid.size - 1) {
                for (x: Int in 1 until grid[y].size - 1) {
                    if (grid[y][x] / 100 == 1) {
                        if (x < startRoom.x || y < startRoom.y) {
                            startRoom = Vector2D(x = x.toFloat(), y = y.toFloat())
                        }
                    }
                }
            }
            return startRoom
        }
    }

    val size: Int get() = height

    fun getTerrainAt(x: Int, y: Int): TerrainType {
        return when (grid[y][x] / 100) {
            1 -> TerrainType.ROAD
            2 -> TerrainType.ROAD
            3 -> TerrainType.WATER
            else -> TerrainType.GRASS
        }
    }

    fun isMovable(x: Int, y: Int): Boolean {
        return getTerrainAt(x, y) != TerrainType.ABYSS
    }

    fun getSpeedModifier(position: Vector2D): Float {
        val cellX: Int = position.x.toInt().coerceIn(0, width - 1)
        val cellY: Int = position.y.toInt().coerceIn(0, height - 1)

        return getTerrainAt(x = cellX, y = cellY).speedModifier
    }
}