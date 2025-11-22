package com.idr.hnsw.demo.repository

import android.content.Context
import android.graphics.Bitmap
import com.idr.hnsw.HnswIndex
import com.idr.hnsw.demo.ImageUtils
import com.idr.hnsw.demo.data.AppDatabase
import com.idr.hnsw.demo.data.FaceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FaceRepository(private val context: Context) {

    private val database = AppDatabase.getDatabase(context)
    private val faceDao = database.faceDao()
    
    // HNSW Index
    private var hnswIndex = HnswIndex(m = 16, efConstruction = 200)
    private val indexFile = File(context.filesDir, "hnsw_index.bin")

    init {
        loadIndex()
    }

    private fun loadIndex() {
        if (indexFile.exists()) {
            try {
                indexFile.inputStream().use {
                    hnswIndex = HnswIndex.load(it)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveIndex() {
        try {
            indexFile.outputStream().use {
                hnswIndex.save(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun addFace(bitmap: Bitmap, label: String) = withContext(Dispatchers.IO) {
        // 1. Save Image to Disk
        val timestamp = System.currentTimeMillis()
        val tempFilename = "temp_${timestamp}.jpg" // Temporary name
        
        // 2. Insert into DB to get ID
        val face = FaceEntity(label = label, timestamp = timestamp, imagePath = tempFilename)
        val id = faceDao.insert(face).toInt()
        
        // 3. Rename file to include ID (optional, but good for organization)
        val finalFilename = "img_$id.jpg"
        // We need to save the bitmap now
        ImageUtils.saveBitmap(context, bitmap, finalFilename)
        
        // Update DB with correct path if we changed it
        faceDao.update(face.copy(id = id, imagePath = finalFilename))

        // 4. Insert into HNSW Index
        val vector = ImageUtils.bitmapToVector(bitmap)
        hnswIndex.insert(vector, id)
        
        saveIndex() // Auto-save index after modification
    }

    suspend fun findSimilar(bitmap: Bitmap, k: Int = 3): List<FaceEntity> = withContext(Dispatchers.IO) {
        val vector = ImageUtils.bitmapToVector(bitmap)
        val resultIds = hnswIndex.search(vector, k)
        
        if (resultIds.isEmpty()) return@withContext emptyList()
        
        // Fetch from DB. Note: This might not preserve order.
        val faces = faceDao.getByIds(resultIds)
        
        // Re-order based on resultIds to maintain similarity ranking
        val faceMap = faces.associateBy { it.id }
        resultIds.mapNotNull { faceMap[it] }
    }
    
    suspend fun getAllFaces(): List<FaceEntity> = withContext(Dispatchers.IO) {
        faceDao.getAll()
    }
    
    suspend fun deleteFace(face: FaceEntity) = withContext(Dispatchers.IO) {
        faceDao.delete(face)
        // Note: HNSW does not support deletion. The node remains but won't be returned
        // by findSimilar because we filter by DB existence (getByIds only returns what's in DB).
        
        // Delete image file
        val file = File(context.filesDir, face.imagePath)
        if (file.exists()) {
            file.delete()
        }
    }
    
    suspend fun updateLabel(face: FaceEntity, newLabel: String) = withContext(Dispatchers.IO) {
        faceDao.update(face.copy(label = newLabel))
    }
}
