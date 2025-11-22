package com.idr.hnsw.demo

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.idr.hnsw.HnswIndex
import com.idr.hnsw.demo.data.FaceEntity
import com.idr.hnsw.demo.repository.FaceRepository
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private lateinit var repository: FaceRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = FaceRepository(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HnswDemoApp()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        repository.saveIndex()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    @Composable
    fun HnswDemoApp() {
        val context = LocalContext.current
        var hasPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            )
        }

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { granted -> hasPermission = granted }
        )

        LaunchedEffect(Unit) {
            if (!hasPermission) {
                launcher.launch(Manifest.permission.CAMERA)
            }
        }

        if (hasPermission) {
            MainScreen()
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Camera permission required")
            }
        }
    }

    @Composable
    fun MainScreen() {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        
        var showAddDialog by remember { mutableStateOf(false) }
        var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var searchResults by remember { mutableStateOf<List<FaceEntity>>(emptyList()) }

        Box(modifier = Modifier.fillMaxSize()) {
            // Camera Preview
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build()
                        val selector = CameraSelector.DEFAULT_FRONT_CAMERA
                        
                        imageCapture = ImageCapture.Builder().build()

                        preview.setSurfaceProvider(previewView.surfaceProvider)

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                selector,
                                preview,
                                imageCapture
                            )
                        } catch (e: Exception) {
                            Log.e("CameraX", "Binding failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                // Search Results
                if (searchResults.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchResults) { face ->
                            FaceItem(face)
                        }
                    }
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = {
                        takePhoto { bitmap ->
                            capturedBitmap = bitmap
                            showAddDialog = true
                        }
                    }) {
                        Text("Add Selfie")
                    }

                    Button(onClick = {
                        takePhoto { bitmap ->
                            lifecycleScope.launch {
                                val results = repository.findSimilar(bitmap)
                                searchResults = results
                                Toast.makeText(context, "Found ${results.size} matches", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Text("Find Similar")
                    }
                }
            }

            // Add Face Dialog
            if (showAddDialog && capturedBitmap != null) {
                AddFaceDialog(
                    bitmap = capturedBitmap!!,
                    onDismiss = { 
                        showAddDialog = false 
                        capturedBitmap = null
                    },
                    onSave = { label ->
                        lifecycleScope.launch {
                            repository.addFace(capturedBitmap!!, label)
                            Toast.makeText(context, "Face added: $label", Toast.LENGTH_SHORT).show()
                            showAddDialog = false
                            capturedBitmap = null
                        }
                    }
                )
            }
        }
    }

    @Composable
    fun AddFaceDialog(
        bitmap: Bitmap,
        onDismiss: () -> Unit,
        onSave: (String) -> Unit
    ) {
        var label by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Add Face") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Captured Face",
                        modifier = Modifier
                            .size(150.dp)
                            .padding(bottom = 16.dp)
                    )
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Enter Name") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { 
                        if (label.isNotBlank()) onSave(label) 
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }

    @Composable
    fun FaceItem(face: FaceEntity) {
        val context = LocalContext.current
        val bitmap = remember(face.imagePath) {
            ImageUtils.loadBitmap(context, face.imagePath)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(100.dp)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = face.label,
                    modifier = Modifier.size(100.dp)
                )
            } else {
                Box(modifier = Modifier.size(100.dp).padding(4.dp)) {
                    Text("Image not found")
                }
            }
            Text(
                text = face.label,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
    }

    private fun takePhoto(onImageCaptured: (Bitmap) -> Unit) {
        val imageCapture = imageCapture ?: return

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("HnswDemo", "Photo capture failed: ${exc.message}", exc)
                }

                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = imageProxyToBitmap(image)
                    val rotatedBitmap = rotateBitmap(bitmap, image.imageInfo.rotationDegrees)
                    onImageCaptured(rotatedBitmap)
                    image.close()
                }
            }
        )
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
}
