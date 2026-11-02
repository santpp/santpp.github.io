package app.rawapp.develop.math

data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun times(s: Double) = Vec3(x * s, y * s, z * s)
    operator fun div(s: Double) = Vec3(x / s, y / s, z / s)
    fun max() = maxOf(x, y, z)
    fun min() = minOf(x, y, z)
    fun map(f: (Double) -> Double) = Vec3(f(x), f(y), f(z))
}

/** Row-major 3×3 matrix. */
class Mat3(private val v: DoubleArray) {
    init {
        require(v.size == 9) { "Mat3 needs 9 values" }
    }

    operator fun get(row: Int, col: Int): Double = v[row * 3 + col]

    operator fun times(o: Mat3): Mat3 = Mat3(
        DoubleArray(9) { i ->
            val r = i / 3
            val c = i % 3
            this[r, 0] * o[0, c] + this[r, 1] * o[1, c] + this[r, 2] * o[2, c]
        },
    )

    operator fun times(p: Vec3): Vec3 = Vec3(
        this[0, 0] * p.x + this[0, 1] * p.y + this[0, 2] * p.z,
        this[1, 0] * p.x + this[1, 1] * p.y + this[1, 2] * p.z,
        this[2, 0] * p.x + this[2, 1] * p.y + this[2, 2] * p.z,
    )

    fun inverse(): Mat3 {
        val a = this
        val c00 = a[1, 1] * a[2, 2] - a[1, 2] * a[2, 1]
        val c01 = a[1, 2] * a[2, 0] - a[1, 0] * a[2, 2]
        val c02 = a[1, 0] * a[2, 1] - a[1, 1] * a[2, 0]
        val det = a[0, 0] * c00 + a[0, 1] * c01 + a[0, 2] * c02
        require(kotlin.math.abs(det) > 1e-12) { "Matriz singular" }
        return Mat3(
            doubleArrayOf(
                c00, a[0, 2] * a[2, 1] - a[0, 1] * a[2, 2], a[0, 1] * a[1, 2] - a[0, 2] * a[1, 1],
                c01, a[0, 0] * a[2, 2] - a[0, 2] * a[2, 0], a[0, 2] * a[1, 0] - a[0, 0] * a[1, 2],
                c02, a[0, 1] * a[2, 0] - a[0, 0] * a[2, 1], a[0, 0] * a[1, 1] - a[0, 1] * a[1, 0],
            ).map { it / det }.toDoubleArray(),
        )
    }

    /** Column-major floats, as glUniformMatrix3fv expects with transpose = false. */
    fun toGlFloats(): FloatArray = FloatArray(9) { i -> this[i % 3, i / 3].toFloat() }

    companion object {
        val IDENTITY = diagonal(Vec3(1.0, 1.0, 1.0))

        fun of(vararg values: Double) = Mat3(values.copyOf())

        fun diagonal(d: Vec3) = Mat3(doubleArrayOf(d.x, 0.0, 0.0, 0.0, d.y, 0.0, 0.0, 0.0, d.z))

        fun lerp(a: Mat3, b: Mat3, t: Double) = Mat3(DoubleArray(9) { i -> a.v[i] + (b.v[i] - a.v[i]) * t })
    }
}
