package com.idr.hnsw

import kotlin.math.sqrt

typealias Vector = FloatArray

interface DistanceMetric {
    fun distance(v1: Vector, v2: Vector): Float
}

object EuclideanDistance : DistanceMetric {
    override fun distance(v1: Vector, v2: Vector): Float {
        var sum = 0.0f
        for (i in v1.indices) {
            val diff = v1[i] - v2[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }
}

object CosineDistance : DistanceMetric {
    override fun distance(v1: Vector, v2: Vector): Float {
        var dot = 0.0f
        var normA = 0.0f
        var normB = 0.0f
        for (i in v1.indices) {
            dot += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }
        
        if (normA == 0.0f || normB == 0.0f) return 1.0f
        
        return 1.0f - (dot / (sqrt(normA) * sqrt(normB)))
    }
}

object DotProductDistance : DistanceMetric {
    override fun distance(v1: Vector, v2: Vector): Float {
        var dot = 0.0f
        for (i in v1.indices) {
            dot += v1[i] * v2[i]
        }
        // HNSW requires non-negative distances usually, so we might return 1 - dot if normalized, 
        // or just -dot. For general dot product where larger is better, we return -dot.
        return -dot
    }
}
