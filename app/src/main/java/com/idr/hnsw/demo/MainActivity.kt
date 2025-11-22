package com.idr.hnsw.demo

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.idr.hnsw.HnswIndex
import com.idr.hnsw.demo.databinding.ActivityMainBinding
import com.idr.hnsw.demo.databinding.ItemResultBinding
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    
    // HNSW Index
    // Dimension 64*64 = 4096
    private val hnswIndex = HnswIndex(m = 16, efConstruction = 200)
    private var currentId = 0
    private val storedImages = mutableMapOf<Int, String>() // ID -> Filename

    private val resultAdapter = ResultAdapter()

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(baseContext, "Permission request denied", Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        binding.btnAddSelfie.setOnClickListener { takePhoto(isSearch = false) }
        binding.btnFindSimilar.setOnClickListener { takePhoto(isSearch = true) }
        
        binding.rvResults.adapter = resultAdapter

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun takePhoto(isSearch: Boolean) {
        val imageCapture = imageCapture ?: return

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onCaptureSuccess(image: ImageProxy) {
                    processImage(image, isSearch)
                    image.close()
                }
            }
        )
    }

    private fun processImage(image: ImageProxy, isSearch: Boolean) {
        val bitmap = imageProxyToBitmap(image)
        val rotatedBitmap = rotateBitmap(bitmap, image.imageInfo.rotationDegrees)
        
        val vector = ImageUtils.bitmapToVector(rotatedBitmap)

        if (isSearch) {
            // Search
            val results = hnswIndex.search(vector, 3)
            val resultBitmaps = results.mapNotNull { id ->
                val filename = storedImages[id]
                filename?.let { ImageUtils.loadBitmap(this, it) }
            }
            resultAdapter.submitList(resultBitmaps)
            Toast.makeText(this, "Found ${results.size} matches", Toast.LENGTH_SHORT).show()
        } else {
            // Add
            val id = currentId++
            hnswIndex.insert(vector, id)
            
            // Save original image for display
            val filename = "img_$id.jpg"
            ImageUtils.saveBitmap(this, rotatedBitmap, filename)
            storedImages[id] = filename
            
            Toast.makeText(this, "Image added to Index (ID: $id)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
    
    private fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "HnswDemo"
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA
            ).apply {
            }.toTypedArray()
    }
}

class ResultAdapter : RecyclerView.Adapter<ResultAdapter.ViewHolder>() {

    private var images: List<Bitmap> = emptyList()

    fun submitList(newImages: List<Bitmap>) {
        images = newImages
        notifyDataSetChanged()
    }

    class ViewHolder(val binding: ItemResultBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.imageView.setImageBitmap(images[position])
    }

    override fun getItemCount() = images.size
}
