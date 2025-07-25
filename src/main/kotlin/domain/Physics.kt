//package mobility.domain
//
//import domain.Car
//import kotlin.math.cos
//import kotlin.math.sin
//
//data class CollisionResult(
//    val isColliding: Boolean = false,
//    val normal: Vector2D = Vector2D.Zero,
//    val penetration: Float = 0f
//)
//
//private data class Projection(val min: Float, val max: Float)
//
//// --- Основная функция обработки столкновений ---
//
//fun handleCollision(car1: Car, car2: Car, result: CollisionResult) {
//    // Шаг 1: Разрешение перекрытия (расталкивание)
//    resolveOverlap(car1, car2, result)
//
//    // Шаг 2: Расчет и применение импульса для реалистичного отскока
//    applyImpulse(car1, car2, result)
//}
//
//// --- Обнаружение столкновений (улучшенный SAT) ---
//
//fun detectCollision(car1: Car, car2: Car): CollisionResult {
//    val axes1 = getAxes(car1.corners)
//    val axes2 = getAxes(car2.corners)
//    var minOverlap = Float.MAX_VALUE
//    var collisionNormal = Vector2D.Zero
//
//    for (axis in axes1 + axes2) {
//        val proj1 = project(car1.corners, axis)
//        val proj2 = project(car2.corners, axis)
//
//        val overlap = minOf(proj1.max, proj2.max) - maxOf(proj1.min, proj2.min)
//        if (overlap <= 0f) {
//            return CollisionResult(isColliding = false) // Нашли разделяющую ось
//        }
//
//        if (overlap < minOverlap) {
//            minOverlap = overlap
//            collisionNormal = axis
//        }
//    }
//
//    // Убедимся, что нормаль направлена от car1 к car2
//    val directionVector = car2.position - car1.position
//    if (dot(directionVector, collisionNormal) < 0) {
//        collisionNormal *= -1f
//    }
//
//    return CollisionResult(
//        isColliding = true,
//        normal = collisionNormal,
//        penetration = minOverlap
//    )
//}
//
//
//// --- Вспомогательные функции ---
//
//private fun resolveOverlap(car1: Car, car2: Car, result: CollisionResult) {
//    val totalInverseMass = 1 / car1.mass + 1 / car2.mass
//    if (totalInverseMass == 0f) return
//
//    val correction = result.normal * (result.penetration / totalInverseMass)
//    car1.position -= correction / car1.mass
//    car2.position += correction / car2.mass
//}
//
//private fun applyImpulse(car1: Car, car2: Car, result: CollisionResult) {
//    val normal = result.normal
//
//    // Конвертируем speed/direction в вектор скорости
//    val v1 = Vector2D(cos(car1.direction) * car1.speed, sin(car1.direction) * car1.speed)
//    val v2 = Vector2D(cos(car2.direction) * car2.speed, sin(car2.direction) * car2.speed)
//
//    // Находим точку контакта
//    val contactPoint = findContactPoint(car1.corners, car2.corners)
//    val r1 = contactPoint - car1.position
//    val r2 = contactPoint - car2.position
//
//    // Скорость в точке контакта (учитывая вращение)
//    val v1Contact = v1 + crossProduct(r1, car1.angularVelocity)
//    val v2Contact = v2 + crossProduct(r2, car2.angularVelocity)
//
//    val relativeVelocity = v1Contact - v2Contact
//    val velocityAlongNormal = dot(relativeVelocity, normal)
//
//    if (velocityAlongNormal > 0) return // Машины уже разлетаются
//
//    val restitution = 0.4f // Коэффициент упругости
//
//    val r1CrossN = crossProduct(r1, normal)
//    val r2CrossN = crossProduct(r2, normal)
//
//    val massInverseSum = 1f / car1.mass + 1f / car2.mass
//    val inertiaSum = (r1CrossN * r1CrossN) / car1.momentOfInertia +
//            (r2CrossN * r2CrossN) / car2.momentOfInertia
//
//    var j = -(1f + restitution) * velocityAlongNormal
//    j /= (massInverseSum + inertiaSum)
//
//    // Исправленное умножение Float на Vector2D
//    val impulse = normal * j  // Теперь нормаль умножается на скаляр
//
//    // Применяем импульс (убедитесь, что car1.mass - Float)
//    val newV1 = v1 + (impulse / car1.mass)
//    val newV2 = v2 - (impulse / car2.mass)
//
////    // Обновляем скорости
//    car1.setSpeedAndDirectionFromVelocity(newV1)
//    car2.setSpeedAndDirectionFromVelocity(newV2)
//
//    // Угловые скорости (используем crossProduct(Vector2D, Vector2D))
//    car1.angularVelocity += crossProduct(r1, impulse) / car1.momentOfInertia
//    car2.angularVelocity -= crossProduct(r2, impulse) / car2.momentOfInertia
//}
//private fun findContactPoint(vertices1: List<Vector2D>, vertices2: List<Vector2D>): Vector2D {
//    var contactPoint = Vector2D.Zero
//    var minDistanceSq = Float.MAX_VALUE
//    for (v in vertices1) {
//        for (u in vertices2) {
//            val distSq = (v - u).getDistanceSquared()
//            if (distSq < minDistanceSq) {
//                minDistanceSq = distSq
//                contactPoint = (v + u) / 2f
//            }
//        }
//    }
//    return contactPoint
//}
//
//// Математические утилиты
//private fun getAxes(corners: List<Vector2D>): List<Vector2D> {
//    val axes = mutableListOf<Vector2D>()
//    for (i in corners.indices) {
//        val p1 = corners[i]
//        val p2 = corners[(i + 1) % corners.size]
//        val edge = p1 - p2
//        axes.add(Vector2D(edge.y, -edge.x).normalized())
//    }
//    return axes
//}
//
//private fun project(corners: List<Vector2D>, axis: Vector2D): Projection {
//    var min = dot(corners[0], axis)
//    var max = min
//    for (i in 1 until corners.size) {
//        val p = dot(corners[i], axis)
//        if (p < min) min = p
//        else if (p > max) max = p
//    }
//    return Projection(min, max)
//}
//
//private fun dot(o1: Vector2D, o2: Vector2D): Float = o1.x * o2.x + o1.y * o2.y
//private fun crossProduct(offset: Vector2D, scalar: Float): Vector2D = Vector2D(-scalar * offset.y, scalar * offset.x)
//private fun crossProduct(a: Vector2D, b: Vector2D): Float = a.x * b.y - a.y * b.x
//private fun Vector2D.normalized(): Vector2D {
//    val len = this.getDistance()
//    return if (len > 0) this / len else this
//}
