package com.idr.hnsw

import java.util.BitSet
import java.util.PriorityQueue
import kotlin.math.ln
import kotlin.math.min
import kotlin.random.Random

class HnswIndex(
    private val m: Int = 16,
    private val efConstruction: Int = 200,
    private val mMax: Int = m,
    private val mMax0: Int = m * 2,
    private val ml: Float = 1.0f / ln(m.toFloat()),
    private val distanceMetric: DistanceMetric = EuclideanDistance
) {

    private var entryPoint: Node? = null
    private var maxLevel = -1
    private val nodes = mutableMapOf<Int, Node>()

    private fun randomLevel(): Int {
        val r = Random.nextFloat()
        return (-ln(r) * ml).toInt()
    }

    fun insert(vector: Vector, id: Int) {
        val level = randomLevel()
        // maxLevel in Node constructor is used to init connections array size
        // We should pass the node's specific level, but the array needs to be up to that level.
        // Actually Node constructor takes 'level' and 'maxLevel'. 
        // In my Node.kt I defined: class Node(..., val level: Int, maxLevel: Int)
        // and connections = Array(maxLevel + 1).
        // So here we pass 'level' as both? No, the node has a specific level, and connections go up to that level.
        // So we pass 'level' as maxLevel for that node.
        val newNode = Node(id, vector, level, level)
        nodes[id] = newNode

        var currObj = entryPoint
        var currDist = if (currObj != null) distanceMetric.distance(vector, currObj.vector) else Float.MAX_VALUE

        if (currObj != null) {
            // 1. Search from top to level+1
            for (l in maxLevel downTo level + 1) {
                var changed = true
                while (changed) {
                    changed = false
                    val connections = currObj!!.connections[l]
                    for (neighbor in connections) {
                        val d = distanceMetric.distance(vector, neighbor.vector)
                        if (d < currDist) {
                            currDist = d
                            currObj = neighbor
                            changed = true
                        }
                    }
                }
            }
        }

        // 2. Search and connect from level down to 0
        val topLevel = min(level, maxLevel)
        for (l in topLevel downTo 0) {
            val candidates = searchLayer(currObj, vector, efConstruction, l)
            val neighbors = selectNeighbors(candidates, m, l, extendCandidates = true, keepPrunedConnections = true)
            
            // Add bidirectional connections
            for (neighbor in neighbors) {
                newNode.connections[l].add(neighbor.node)
                neighbor.node.connections[l].add(newNode)
                
                // Prune connections of neighbor if needed
                val maxM = if (l == 0) mMax0 else mMax
                if (neighbor.node.connections[l].size > maxM) {
                    val neighborConnections = neighbor.node.connections[l]
                    // We need to prune 'neighborConnections' to keep only best 'maxM'
                    // We can reuse selectNeighbors logic or just sort and pick.
                    // selectNeighbors expects PriorityQueue<Candidate>.
                    val candidatesForPruning = PriorityQueue<Candidate>()
                    for (n in neighborConnections) {
                        candidatesForPruning.add(Candidate(n, distanceMetric.distance(neighbor.node.vector, n.vector)))
                    }
                    // We need a MaxHeap for selectNeighbors? No, selectNeighbors takes the result of searchLayer which is a MaxHeap (W).
                    // But selectNeighbors implementation I wrote sorts them.
                    // Let's just do it manually here for clarity and efficiency.
                    
                    val sorted = neighborConnections.map { 
                        Candidate(it, distanceMetric.distance(neighbor.node.vector, it.vector)) 
                    }.sortedBy { it.distance }
                    
                    neighbor.node.connections[l].clear()
                    neighbor.node.connections[l].addAll(sorted.take(maxM).map { it.node })
                }
            }
            // Update entry point for next layer (greedy step within the found candidates)
            if (candidates.isNotEmpty()) {
                // searchLayer returns a max-heap (W), so we need to find the closest element
                // to be the entry point for the next layer.
                currObj = candidates.minByOrNull { it.distance }?.node ?: currObj
            }
        }

        if (entryPoint == null || level > maxLevel) {
            maxLevel = level
            entryPoint = newNode
        }
    }

    fun search(query: Vector, k: Int): List<Int> {
        if (entryPoint == null) return emptyList()

        var currObj = entryPoint!!
        var currDist = distanceMetric.distance(query, currObj.vector)

        for (l in maxLevel downTo 1) {
            var changed = true
            while (changed) {
                changed = false
                for (neighbor in currObj.connections[l]) {
                    val d = distanceMetric.distance(query, neighbor.vector)
                    if (d < currDist) {
                        currDist = d
                        currObj = neighbor
                        changed = true
                    }
                }
            }
        }

        val candidates = searchLayer(currObj, query, maxOf(efConstruction, k), 0)
        
        return candidates.sortedBy { it.distance }.take(k).map { it.node.id }
    }

    private fun searchLayer(entryPoint: Node?, query: Vector, ef: Int, layer: Int): PriorityQueue<Candidate> {
        val v = BitSet() // Visited set
        val C = PriorityQueue<Candidate>() // Min-heap for candidates to visit
        val W = PriorityQueue<Candidate> { a, b -> b.distance.compareTo(a.distance) } // Max-heap for keeping top ef

        if (entryPoint != null) {
            val d = distanceMetric.distance(query, entryPoint.vector)
            val c = Candidate(entryPoint, d)
            C.add(c)
            W.add(c)
            v.set(entryPoint.id)
        }

        while (C.isNotEmpty()) {
            val c = C.poll() // Closest candidate
            val f = W.peek() // Furthest in current result set

            if (c.distance > f.distance) break // All remaining candidates are worse than the worst in W

            for (neighbor in c.node.connections[layer]) {
                if (!v.get(neighbor.id)) {
                    v.set(neighbor.id)
                    val d = distanceMetric.distance(query, neighbor.vector)
                    if (W.size < ef || d < W.peek().distance) {
                        val e = Candidate(neighbor, d)
                        C.add(e)
                        W.add(e)
                        if (W.size > ef) {
                            W.poll() // Remove furthest
                        }
                    }
                }
            }
        }
        return W
    }

    private fun selectNeighbors(
        candidates: PriorityQueue<Candidate>, // This is W from searchLayer (Max Heap)
        M: Int,
        layer: Int,
        extendCandidates: Boolean,
        keepPrunedConnections: Boolean
    ): List<Candidate> {
        // Simple heuristic: just take the M closest.
        val sorted = candidates.sortedBy { it.distance }
        return sorted.take(M)
    }

    fun save(out: java.io.OutputStream) {
        val dos = java.io.DataOutputStream(out)
        dos.writeInt(m)
        dos.writeInt(efConstruction)
        dos.writeInt(mMax)
        dos.writeInt(mMax0)
        dos.writeFloat(ml)
        
        dos.writeInt(maxLevel)
        dos.writeInt(entryPoint?.id ?: -1)
        
        dos.writeInt(nodes.size)
        for ((id, node) in nodes) {
            dos.writeInt(id)
            dos.writeInt(node.level)
            // Vector
            dos.writeInt(node.vector.size)
            for (v in node.vector) {
                dos.writeFloat(v)
            }
            // Connections
            for (l in 0..node.level) {
                val neighbors = node.connections[l]
                dos.writeInt(neighbors.size)
                for (n in neighbors) {
                    dos.writeInt(n.id)
                }
            }
        }
        dos.flush()
    }

    companion object {
        fun load(input: java.io.InputStream, distanceMetric: DistanceMetric = EuclideanDistance): HnswIndex {
            val dis = java.io.DataInputStream(input)
            val m = dis.readInt()
            val ef = dis.readInt()
            val mMax = dis.readInt()
            val mMax0 = dis.readInt()
            val ml = dis.readFloat()
            
            val index = HnswIndex(m, ef, mMax, mMax0, ml, distanceMetric)
            
            val maxLevel = dis.readInt()
            index.maxLevel = maxLevel
            val entryPointId = dis.readInt()
            
            val numNodes = dis.readInt()
            val tempConnections = mutableMapOf<Int, List<List<Int>>>()
            
            for (i in 0 until numNodes) {
                val id = dis.readInt()
                val level = dis.readInt()
                val vecSize = dis.readInt()
                val vector = FloatArray(vecSize)
                for (j in 0 until vecSize) {
                    vector[j] = dis.readFloat()
                }
                
                val node = Node(id, vector, level, level)
                index.nodes[id] = node
                
                val nodeConns = mutableListOf<List<Int>>()
                for (l in 0..level) {
                    val numNeighbors = dis.readInt()
                    val neighborIds = mutableListOf<Int>()
                    for (k in 0 until numNeighbors) {
                        neighborIds.add(dis.readInt())
                    }
                    nodeConns.add(neighborIds)
                }
                tempConnections[id] = nodeConns
            }
            
            // Link connections
            for ((id, conns) in tempConnections) {
                val node = index.nodes[id]!!
                for (l in conns.indices) {
                    val neighborIds = conns[l]
                    for (nid in neighborIds) {
                        val neighbor = index.nodes[nid]
                        if (neighbor != null) {
                            node.connections[l].add(neighbor)
                        }
                    }
                }
            }
            
            if (entryPointId != -1) {
                index.entryPoint = index.nodes[entryPointId]
            }
            
            return index
        }
    }
}
