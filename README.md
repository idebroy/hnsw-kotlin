# HNSW Kotlin

A lightweight, zero-dependency Kotlin implementation of Hierarchical Navigable Small World (HNSW) graphs for approximate nearest neighbor search. Designed for Android and JVM applications.

## Features

*   **Pure Kotlin**: No native dependencies (NDK not required).
*   **Lightweight**: Minimal memory footprint, suitable for mobile devices.
*   **Modular Distance Metrics**: Includes Euclidean, Cosine, and Dot Product distances.
*   **Serialization**: Built-in support for saving and loading indices to/from streams.
*   **Thread-Safe**: (Note: Current implementation is single-threaded for simplicity, but structure allows for future parallelization).

## Installation

Currently, you can include this library by copying the `hnsw_lib` module into your project or building it locally.

## Usage

### 1. Initialize the Index

```kotlin
import com.idr.hnsw.HnswIndex
import com.idr.hnsw.EuclideanDistance

// m: Max number of connections per element per layer (default 16)
// efConstruction: Size of the dynamic list for the nearest neighbors (default 200)
val index = HnswIndex(m = 16, efConstruction = 200, distanceMetric = EuclideanDistance)
```

### 2. Insert Vectors

```kotlin
val vector = floatArrayOf(0.1f, 0.2f, 0.3f, ...)
val id = 1 // Unique Integer ID for the vector

index.insert(vector, id)
```

### 3. Search

```kotlin
val queryVector = floatArrayOf(0.1f, 0.2f, 0.3f, ...)
val k = 10 // Number of nearest neighbors to find

val results: List<Int> = index.search(queryVector, k)
println("Found neighbor IDs: $results")
```

### 4. Serialization

Save the index to a file or stream:

```kotlin
val outputStream = FileOutputStream("index.bin")
index.save(outputStream)
outputStream.close()
```

Load the index from a file or stream:

```kotlin
val inputStream = FileInputStream("index.bin")
val loadedIndex = HnswIndex.load(inputStream, distanceMetric = EuclideanDistance)
```

## Configuration

| Parameter | Description | Default |
| :--- | :--- | :--- |
| `m` | The number of bi-directional links created for every new element during construction. Higher `m` works better on high dimensional data and/or high recall, but consumes more memory. | 16 |
| `efConstruction` | The size of the dynamic list for the nearest neighbors (used during the search). Higher `efConstruction` leads to more accurate but slower construction. | 200 |
| `distanceMetric` | The metric used to calculate distance between vectors (`EuclideanDistance`, `CosineDistance`, `DotProductDistance`). | `EuclideanDistance` |

## License

This project is open source.
