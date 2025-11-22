package com.idr.hnsw.demo.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "faces")
data class FaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String,
    val timestamp: Long,
    val imagePath: String
)
