package com.idr.hnsw

import kotlin.random.Random
import kotlin.system.measureTimeMillis

fun main() {
    val dim = 64
    val numVectors = 1000
    val numQueries = 10
    val k = 10

    println("Generating $numVectors vectors of dimension $dim...")
    val vectors = Array(numVectors) {
        FloatArray(dim) { Random.nextFloat() }
    }

    val index = HnswIndex(m = 16, efConstruction = 200)

    println("Building index...")
    val buildTime = measureTimeMillis {
        for (i in vectors.indices) {
            index.insert(vectors[i], i)
        }
    }
    println("Index built in $buildTime ms")

    println("Running $numQueries queries...")
    var totalRecall = 0.0f

    for (i in 0 until numQueries) {
        val query = FloatArray(dim) { Random.nextFloat() }
        
        // Brute force search
        val bruteForceResults = vectors.mapIndexed { id, vec -> 
            id to EuclideanDistance.distance(query, vec)
        }.sortedBy { it.second }.take(k).map { it.first }.toSet()

        // HNSW search
        val hnswResults = index.search(query, k).toSet()

        val intersection = bruteForceResults.intersect(hnswResults).size
        val recall = intersection.toFloat() / k
        totalRecall += recall
        
        // println("Query $i: Recall = $recall")
    }

    println("Average Recall: ${totalRecall / numQueries}")
}
