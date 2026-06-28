package com.example.ohrana

import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.asImageBitmap
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.MediaScannerConnection
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import android.graphics.BitmapFactory
import android.graphics.YuvImage
import java.io.ByteArrayOutputStream
import android.provider.MediaStore
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.content.Context
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoCaptureScreen(
    checkpointName: String,
    onPhotoTaken: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Состояния: false = камера, true = предпросмотр
    var isPreviewMode by remember { mutableStateOf(false) }
    // Храним путь к последнему снятоому фото
    var lastPhotoPath by remember { mutableStateOf("") }
    // Храним Bitmap для отображения в предпросмотре
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Launcher для системной камеры
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success) {
                // Фото успешно сохранено, читаем его
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = "${checkpointName.replace(" ", "_")}_${timestamp}.jpg"
                
                // Получаем файл из папки files
                val filesDir = context.filesDir
                val imageFile = File(filesDir, fileName)
                
                if (imageFile.exists()) {
                    val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                    if (bitmap != null) {
                        lastPhotoPath = imageFile.absolutePath
                        capturedBitmap = bitmap
                        isPreviewMode = true
                    }
                }
            }
        }
    )

    LaunchedEffect(Unit) {
        android.widget.Toast.makeText(context, "Съемка: $checkpointName", android.widget.Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (isPreviewMode) {
                        Text("Предпросмотр")
                    } else {
                        Text("Съемка прибора: $checkpointName")
                    }
                },
                navigationIcon = {
                    if (isPreviewMode) {
                        IconButton(onClick = { isPreviewMode = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад к камере")
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Выход")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Камера
            if (!isPreviewMode) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Предпросмотр
                if (capturedBitmap != null) {
                    Image(
                        bitmap = capturedBitmap!!.asImageBitmap(),
                        contentDescription = "Предпросмотр фото",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.LightGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Нет изображения")
                    }
                }
            }

            // Кнопки управления
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isPreviewMode) {
                    // В режиме предпросмотра: кнопки "Сохранить" и "Сделать заново"
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Button(
                            onClick = {
                                // Сделать заново - возвращаемся в режим камеры
                                isPreviewMode = false
                                capturedBitmap = null
                            },
                            modifier = Modifier.width(140.dp).height(56.dp),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Text(text = "Сделать заново", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        
                        Button(
                            onClick = {
                                // Сохраняем фото
                                onPhotoTaken(lastPhotoPath)
                            },
                            modifier = Modifier.width(140.dp).height(56.dp),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Text(text = "Сохранить", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                } else {
                    // В режиме камеры: кнопка "Сделать фото"
                    Button(
                        onClick = {
                            // Создаем файл для сохранения
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                            val fileName = "${checkpointName.replace(" ", "_")}_${timestamp}.jpg"
                            val filesDir = context.filesDir
                            val imageFile = File(filesDir, fileName)
                            
                            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                FileProvider.getUriForFile(context, context.packageName + ".fileprovider", imageFile)
                            } else {
                                Uri.fromFile(imageFile)
                            }
                            
                            // Запускаем системную камеру
                            cameraLauncher.launch(uri)
                        },
                        modifier = Modifier.width(200.dp).height(56.dp),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Text(text = "Сделать фото", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
