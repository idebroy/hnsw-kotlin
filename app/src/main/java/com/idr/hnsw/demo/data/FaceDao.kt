package com.idr.hnsw.demo.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface FaceDao {
    @Insert
    suspend fun insert(face: FaceEntity): Long

    @Update
    suspend fun update(face: FaceEntity)

    @Delete
    suspend fun delete(face: FaceEntity)

    @Query("SELECT * FROM faces")
    suspend fun getAll(): List<FaceEntity>

    @Query("SELECT * FROM faces WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Int>): List<FaceEntity>
    
    @Query("SELECT * FROM faces WHERE id = :id")
    suspend fun getById(id: Int): FaceEntity?
}
