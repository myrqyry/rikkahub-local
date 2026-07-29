package me.rerere.rikkahub.data.rag

object VectorMath {
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || b.isEmpty() || a.size != b.size) return 0f
        val dot = dotProduct(a, b)
        val magA = magnitude(a)
        val magB = magnitude(b)
        return if (magA == 0f || magB == 0f) 0f else dot / (magA * magB)
    }

    fun normalize(a: FloatArray): FloatArray {
        val mag = magnitude(a)
        if (mag == 0f) return a
        return FloatArray(a.size) { a[it] / mag }
    }

    fun dotProduct(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || b.isEmpty() || a.size != b.size) return 0f
        var sum = 0f
        for (i in a.indices) {
            sum += a[i] * b[i]
        }
        return sum
    }

    fun magnitude(a: FloatArray): Float {
        if (a.isEmpty()) return 0f
        var sum = 0f
        for (v in a) {
            sum += v * v
        }
        return kotlin.math.sqrt(sum)
    }
}