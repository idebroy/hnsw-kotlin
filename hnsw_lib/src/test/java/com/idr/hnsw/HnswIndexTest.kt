package com.idr.hnsw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HnswIndexTest {

    @Test
    fun testInsertAndSearch() {
        val index = HnswIndex(m = 16, efConstruction = 100)
        
        val v1 = floatArrayOf(1.0f, 0.0f)
        val v2 = floatArrayOf(0.0f, 1.0f)
        val v3 = floatArrayOf(1.1f, 0.0f) // Close to v1
        
        index.insert(v1, 1)
        index.insert(v2, 2)
        index.insert(v3, 3)
        
        val query = floatArrayOf(1.0f, 0.0f)
        val results = index.search(query, 2)
        
        // Should find 1 and 3 as closest
        assertEquals(2, results.size)
        assertTrue(results.contains(1))
        assertTrue(results.contains(3))
        
        // 1 should be first as distance is 0
        assertEquals(1, results[0])
    }
    
    @Test
    fun testEmptyIndex() {
        val index = HnswIndex()
        val results = index.search(floatArrayOf(1.0f), 5)
        assertTrue(results.isEmpty())
    }

    @Test
    fun testSerialization() {
        val index = HnswIndex(m = 16, efConstruction = 100)
        val v1 = floatArrayOf(1.0f, 2.0f)
        index.insert(v1, 1)
        
        val baos = java.io.ByteArrayOutputStream()
        index.save(baos)
        
        val bais = java.io.ByteArrayInputStream(baos.toByteArray())
        val loadedIndex = HnswIndex.load(bais)
        
        val results = loadedIndex.search(floatArrayOf(1.0f, 2.0f), 1)
        assertEquals(1, results.size)
        assertEquals(1, results[0])
    }
}
