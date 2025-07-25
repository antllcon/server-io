package domain

import mobility.domain.Vector2D
import kotlin.math.atan2
import kotlin.random.Random

class GameMap private constructor(
    val grid: Array<IntArray>,
    val width: Int,
    val height: Int,
    val startCellPos: Vector2D,
    val startDirection: StartDirection,
    val route: List<Vector2D>
) {
    enum class StartDirection {
        HORIZONTAL,
        VERTICAL
    }

    enum class TerrainType(val speedModifier: Float) {
        ROAD(speedModifier = 1.0f),
        GRASS(speedModifier = 0.6f),
        WATER(speedModifier = 0.4f),
        ABYSS(speedModifier = .0f)
    }

    companion object {
        private const val DEFAULT_MAP_WIDTH = 13
        private const val DEFAULT_MAP_HEIGHT = 13
        private const val DEFAULT_CORE_POINT = 15
        private const val DEFAULT_WATER_PROPABILITY = 0.1f
        private const val EMPTY_CELL_CODE = 0
        private const val CORE_CELL_CODE = 1
        private const val ROAD_CELL_CODE = 2
        private const val WATER_CORE_CODE = 200
        private const val WATER_ROAD_CODE = 400

        private data class StartInfo(
            val position: Vector2D,
            val direction: StartDirection
        )

        private data class CellNode(
            val x: Int,
            val y: Int,
            val cameFromX: Int?,
            val cameFromY: Int?
        )

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

            createWaterCellsInternal(grid, waterProbability)
            determinationCellTypesInternal(grid)

            val startInfo = findStartCellInternal(grid)

            val route = generateRouteFromStart(grid, startInfo.position)

            return GameMap(
                grid,
                width,
                height,
                startInfo.position,
                startInfo.direction,
                route
            )
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
                if (grid[y1][x1] == EMPTY_CELL_CODE) grid[y1][x1] = ROAD_CELL_CODE
            }

            while (y1 != y2) {
                y1 += if (y2 > y1) 1 else -1
                if (grid[y1][x1] == EMPTY_CELL_CODE) grid[y1][x1] = ROAD_CELL_CODE
            }
        }

        private fun removeDeadEndsInternal(grid: Array<IntArray>) {
            var removedSomething: Boolean
            do {
                removedSomething = false
                val deadEnds = findDeadEndCells(grid)

                for ((x, y) in deadEnds) {
                    if (getRoadNeighbors(grid, x, y).size == 1) {
                        grid[y][x] = EMPTY_CELL_CODE
                        removedSomething = true
                    }
                }
            } while (removedSomething)
        }

        private fun findDeadEndCells(grid: Array<IntArray>): List<Pair<Int, Int>> {
            val deadEnds = mutableListOf<Pair<Int, Int>>()
            for (y in 1 until grid.size - 1) {
                for (x in 1 until grid[0].size - 1) {
                    if (grid[y][x] in listOf(CORE_CELL_CODE, ROAD_CELL_CODE) && getRoadNeighbors(
                            grid,
                            x,
                            y
                        ).size == 1
                    ) {
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
                if (ny in grid.indices && nx in grid[ny].indices && grid[ny][nx] in listOf(
                        CORE_CELL_CODE,
                        ROAD_CELL_CODE
                    )
                ) {
                    neighbors.add(nx to ny)
                }
            }
            return neighbors
        }

        private fun createWaterCellsInternal(
            grid: Array<IntArray>,
            waterProbability: Float
        ) {
            for (y in 1 until grid.size - 1) {
                for (x in 1 until grid[y].size - 1) {
                    if (Random.nextFloat() < waterProbability) {
                        when (grid[y][x]) {
                            CORE_CELL_CODE -> grid[y][x] = WATER_CORE_CODE
                            ROAD_CELL_CODE -> grid[y][x] = WATER_ROAD_CODE
                        }
                    }
                }
            }
        }

        private fun determinationCellTypesInternal(grid: Array<IntArray>) {
            val height = grid.size
            val width = grid[0].size

            val newGrid = Array(height) { IntArray(width) }

            for (y in 0 until height) {
                for (x in 0 until width) {
                    val cellValue = grid[y][x]

                    when (cellValue) {
                        EMPTY_CELL_CODE -> {
                            newGrid[y][x] = 0
                        }

                        CORE_CELL_CODE, ROAD_CELL_CODE, WATER_CORE_CODE, WATER_ROAD_CODE -> {
                            val hasTop = y > 0 && grid[y - 1][x] != EMPTY_CELL_CODE
                            val hasBottom = y < height - 1 && grid[y + 1][x] != EMPTY_CELL_CODE
                            val hasLeft = x > 0 && grid[y][x - 1] != EMPTY_CELL_CODE
                            val hasRight = x < width - 1 && grid[y][x + 1] != EMPTY_CELL_CODE

                            var shapeCode = 0

                            when {
                                hasTop && hasBottom && hasLeft && hasRight -> shapeCode = 11
                                hasTop && hasBottom && hasLeft -> shapeCode = 10
                                hasTop && hasLeft && hasRight -> shapeCode = 9
                                hasBottom && hasLeft && hasRight -> shapeCode = 8
                                hasTop && hasBottom && hasRight -> shapeCode = 7
                                hasBottom && hasLeft -> shapeCode = 6
                                hasBottom && hasRight -> shapeCode = 5
                                hasTop && hasLeft -> shapeCode = 4
                                hasTop && hasRight -> shapeCode = 3
                                hasTop && hasBottom -> shapeCode = 2
                                hasLeft && hasRight -> shapeCode = 1
                            }

                            val baseCode = when (cellValue) {
                                CORE_CELL_CODE -> 100
                                WATER_CORE_CODE -> 200
                                ROAD_CELL_CODE -> 300
                                WATER_ROAD_CODE -> 400
                                else -> 0
                            }

                            if (shapeCode > 0 && baseCode > 0) {
                                newGrid[y][x] = baseCode + shapeCode
                            } else {
                                newGrid[y][x] = if (baseCode == 200 || baseCode == 400) 10 else 0
                            }
                        }

                        else -> {
                            newGrid[y][x] = cellValue
                        }
                    }
                }
            }

            for (y in 0 until height) {
                System.arraycopy(newGrid[y], 0, grid[y], 0, width)
            }
        }

        private fun findStartCellInternal(grid: Array<IntArray>): StartInfo {
            val width: Int = grid[0].size
            val height: Int = grid.size
            val candidateCells = mutableListOf<Pair<Vector2D, StartDirection>>()

            for (y in 1 until height - 1) {
                for (x in 1 until width - 1) {
                    val cellValue = grid[y][x]
                    when (cellValue) {
                        101, 301 -> {
                            candidateCells.add(
                                Pair(
                                    Vector2D(x.toFloat(), y.toFloat()),
                                    StartDirection.HORIZONTAL
                                )
                            )
                        }

                        102, 302 -> {
                            candidateCells.add(
                                Pair(
                                    Vector2D(x.toFloat(), y.toFloat()),
                                    StartDirection.VERTICAL
                                )
                            )
                        }
                    }
                }
            }

            if (candidateCells.isNotEmpty()) {
                val (chosenPosition, chosenDirection) = candidateCells.random()
                if (chosenDirection == StartDirection.HORIZONTAL) {
                    grid[chosenPosition.y.toInt()][chosenPosition.x.toInt()] = 112
                } else {
                    grid[chosenPosition.y.toInt()][chosenPosition.x.toInt()] = 113
                }
                return StartInfo(chosenPosition, chosenDirection)
            }

            println("Warning: Suitable start cell (horizontal/vertical road) not found. Using center position.")
            return StartInfo(Vector2D(width / 2f, height / 2f), StartDirection.HORIZONTAL)
        }


        private fun generateRouteFromStart(grid: Array<IntArray>, startPos: Vector2D): List<Vector2D> {
            val height = grid.size
            val width = grid[0].size
            val route = mutableListOf<Vector2D>()

            val visited = Array(height) { BooleanArray(width) { false } }

            val addedCheckpoints = mutableSetOf<Pair<Int, Int>>()

            val startX = startPos.x.toInt()
            val startY = startPos.y.toInt()

            if (grid[startY][startX] in 100..299) {
                val posPair = Pair(startX, startY)
                if (!addedCheckpoints.contains(posPair)) {
                    route.add(Vector2D(startX.toFloat(), startY.toFloat()))
                    addedCheckpoints.add(posPair)
                }
            }
            visited[startY][startX] = true

            val directions = listOf(
                Pair(0, -1),
                Pair(1, 0),
                Pair(0, 1),
                Pair(-1, 0)
            )

            dfsExplore(
                grid = grid,
                visited = visited,
                addedCheckpoints = addedCheckpoints,
                currentX = startX,
                currentY = startY,
                route = route,
                directions = directions,
                width = width,
                height = height
            )

            if (route.isEmpty()) {
                println("Warning: Generated route is empty after DFS.")
                if (grid[startY][startX] in 100..299) {
                    val posPair = Pair(startX, startY)
                    if (!addedCheckpoints.contains(posPair)) {
                        route.add(Vector2D(startX.toFloat(), startY.toFloat()))
                        addedCheckpoints.add(posPair)
                    }
                }
            } else if (route.size == 1) {
                println("Warning: Generated route contains only one checkpoint after DFS.")
            }

            val finishPos = Vector2D(startX.toFloat(), startY.toFloat())
            if (route.isEmpty() || route.lastOrNull() != finishPos) {
                route.add(finishPos)
            }

            return route
        }

        private fun dfsExplore(
            grid: Array<IntArray>,
            visited: Array<BooleanArray>,
            addedCheckpoints: MutableSet<Pair<Int, Int>>,
            currentX: Int,
            currentY: Int,
            route: MutableList<Vector2D>,
            directions: List<Pair<Int, Int>>,
            width: Int,
            height: Int
        ) {
            for ((dx, dy) in directions) {
                val nextX = currentX + dx
                val nextY = currentY + dy

                if (nextX in 0 until width && nextY in 0 until height) {
                    val nextCellCode = grid[nextY][nextX]

                    if (nextCellCode in 100..499) {
                        if (nextCellCode in 100..299) {
                            val posPair = Pair(nextX, nextY)
                            if (!addedCheckpoints.contains(posPair)) {
                                route.add(Vector2D(nextX.toFloat(), nextY.toFloat()))
                                addedCheckpoints.add(posPair)
                                // println("Added checkpoint at ($nextX, $nextY) with code $nextCellCode")
                            } else {
                                // println("Skipped duplicate checkpoint at ($nextX, $nextY)")
                            }
                        }

                        if (!visited[nextY][nextX]) {
                            visited[nextY][nextX] = true
                            dfsExplore(
                                grid,
                                visited,
                                addedCheckpoints,
                                nextX,
                                nextY,
                                route,
                                directions,
                                width,
                                height
                            )
                        }
                    }
                }
            }
        }
    }

    val size: Int get() = height

    fun getTerrainName(x: Int, y: Int): String {
        if (y !in grid.indices || x !in grid[y].indices) {
            println("Warning: Attempted to get terrain name for out-of-bounds cell ($x, $y). Returning terrain_0.")
            return "terrain_${EMPTY_CELL_CODE}"
        }
        return "terrain_" + grid[y][x].toString().padStart(3, '0')
    }
    fun getTerrainType(x: Int, y: Int): String {
        return when (grid[y][x] / 100) {
            1 -> "ROAD"
            2 -> "ABYSS"
            3 -> "ROAD"
            4 -> "ABYSS"
            else -> "GRASS"
        }
    }

    private fun getTerrainAt(x: Int, y: Int): TerrainType {
        if (y !in grid.indices || x !in grid[y].indices) {
            println("Warning: Attempted to get terrain at out-of-bounds cell ($x, $y). Returning ABYSS.")
            return TerrainType.ABYSS
        }

        val cellValue = grid[y][x]
        return when (cellValue) {
            0 -> TerrainType.GRASS
            10 -> TerrainType.WATER
            in 100..199 -> TerrainType.ROAD
            in 200..299 -> TerrainType.WATER
            in 300..399 -> TerrainType.ROAD
            in 400..499 -> TerrainType.WATER
            else -> TerrainType.ABYSS
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