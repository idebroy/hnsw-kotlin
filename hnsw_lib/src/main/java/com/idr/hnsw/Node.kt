package com.idr.hnsw

class Node(
    val id: Int,
    val vector: Vector,
    val level: Int,
    maxLevel: Int
) {
    // connections[i] holds neighbors at level i
    val connections: Array<MutableList<Node>> = Array(maxLevel + 1) { ArrayList() }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Node

        return id == other.id
    }

    override fun hashCode(): Int {
        return id
    }

    override fun toString(): String {
        return "Node(id=$id, level=$level)"
    }
}

data class Candidate(val node: Node, val distance: Float) : Comparable<Candidate> {
    override fun compareTo(other: Candidate): Int = distance.compareTo(other.distance)
}
