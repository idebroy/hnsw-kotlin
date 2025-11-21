package com.idr.hnsw

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

class DistanceMetricTest {

    @Test
    fun testEuclideanDistance() {
        val v1 = floatArrayOf(1.0f, 2.0f, 3.0f)
        val v2 = floatArrayOf(4.0f, 5.0f, 6.0f)
        // diff: 3, 3, 3 -> sq: 9, 9, 9 -> sum: 27 -> sqrt(27) approx 5.196
        val expected = sqrt(27.0f)
        assertEquals(expected, EuclideanDistance.distance(v1, v2), 0.0001f)
    }

    @Test
    fun testCosineDistance() {
        val v1 = floatArrayOf(1.0f, 0.0f, 0.0f)
        val v2 = floatArrayOf(0.0f, 1.0f, 0.0f)
        // Orthogonal -> distance 1.0
        assertEquals(1.0f, CosineDistance.distance(v1, v2), 0.0001f)

        val v3 = floatArrayOf(1.0f, 1.0f, 0.0f)
        val v4 = floatArrayOf(1.0f, 1.0f, 0.0f)
        // Same direction -> distance 0.0
        assertEquals(0.0f, CosineDistance.distance(v3, v4), 0.0001f)
        
        val v5 = floatArrayOf(1.0f, 0.0f)
        val v6 = floatArrayOf(-1.0f, 0.0f)
        // Opposite -> distance 2.0 (1 - (-1))
        assertEquals(2.0f, CosineDistance.distance(v5, v6), 0.0001f)
    }

    @Test
    fun testDotProductDistance() {
        val v1 = floatArrayOf(1.0f, 2.0f)
        val v2 = floatArrayOf(3.0f, 4.0f)
        // dot: 3 + 8 = 11. Returns -11
        assertEquals(-11.0f, DotProductDistance.distance(v1, v2), 0.0001f)
    }
}
