package com.example.ohrana

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageCapture.OutputFileOptions
import androidx.camera.core.Preview as CameraPreview
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.LaunchedEffect
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
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoCaptureScreen(
    checkpointId: String,
    onPhotoTaken: (String) -> Unit,
    onBack: () -> Unit,
    prefsManager: SharedPrefsManager,
    employeeName: String
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Получаем URI картинки прибора
    // Сначала пытаемся загрузить из данных чекпоинта, затем из SharedPreferences
    val checkpointFromDatabase = prefsManager.getCheckpointById(checkpointId)
    var deviceImageUri by remember { 
        mutableStateOf(
            checkpointFromDatabase?.imageUri ?: prefsManager.getCheckpointImageUri(checkpointId)
        ) 
    }
    
    LaunchedEffect(prefsManager, checkpointId) {
        val checkpoint = prefsManager.getCheckpointById(checkpointId)
        deviceImageUri = checkpoint?.imageUri ?: prefsManager.getCheckpointImageUri(checkpointId)
    }

    // Состояния: false = камера, true = предпросмотр
    var isPreviewMode by remember { mutableStateOf(false) }
    // Храним путь к последнему снятоому фото
    var lastPhotoPath by remember { mutableStateOf("") }
    // Храним Bitmap для отображения в предпросмотре
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    // ImageCapture для захвата фото
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    // Сначала сохраняем в приватную папку
    val filesDir = context.filesDir
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val fileName = "${checkpointId.replace(" ", "_")}_${timestamp}.jpg"
    val imageFile = File(filesDir, fileName)
    
    // Лог для диагностики
    android.widget.Toast.makeText(context, "Файл будет сохранен в: ${imageFile.absolutePath}", android.widget.Toast.LENGTH_SHORT).show()

    // Функция захвата фото через CameraX
    fun capturePhoto() {
        val capture = imageCapture ?: run {
            android.widget.Toast.makeText(
                context,
                "Камера не инициализирована",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        try {
            // Создаем OutputFileOptions для сохранения в files/ папку
            val outputFileOptions = ImageCapture.OutputFileOptions.Builder(imageFile).build()
            
            capture.takePicture(
                outputFileOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        android.widget.Toast.makeText(context, "Фото успешно сохранено!", android.widget.Toast.LENGTH_SHORT).show()
                        
                        lastPhotoPath = imageFile.absolutePath
                        
                        // Проверяем, что файл действительно создан
                        if (imageFile.exists()) {
                            android.widget.Toast.makeText(context, "Файл существует: ${imageFile.length()} байт", android.widget.Toast.LENGTH_SHORT).show()
                            
                            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                            if (bitmap != null) {
                                capturedBitmap = bitmap
                                isPreviewMode = true
                                
                                // Сохраняем лог в SharedPreferences
                                val logText = "Фото прибора: $checkpointId -> Файл: $fileName"
                                prefsManager.saveScanResult(employeeName = employeeName, qrContent = logText)
                            } else {
                                android.widget.Toast.makeText(context, "Ошибка: не удалось декодировать bitmap!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            android.widget.Toast.makeText(context, "Ошибка: файл не создан!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        exception.printStackTrace()
                        android.widget.Toast.makeText(
                            context,
                            "Ошибка съемки: ${exception.message}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(
                context,
                "Ошибка: ${e.message}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Функция инициализации камеры
    fun initCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = CameraPreview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val capture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    capture
                )
                // Сохраняем ImageCapture для последующего использования
                imageCapture = capture
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Разрешение на запись во внешнее хранилище
    var hasStoragePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val storageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasStoragePermission = granted }
    )
    
    // Запрашиваем разрешение при старте
    LaunchedEffect(Unit) {
        if (!hasStoragePermission) {
            storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    LaunchedEffect(Unit) {
        android.widget.Toast.makeText(context, "Съемка: $checkpointId", android.widget.Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (isPreviewMode) {
                        Text("Предпросмотр")
                    } else {
                        Text("Съемка прибора: $checkpointId")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
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
                // Делим экран на две части: сверху картинка прибора (40%), снизу камера (60%)
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Верхняя часть - картинка прибора (пример)
                    Box(
                        modifier = Modifier
                            .weight(0.4f)
                            .fillMaxWidth()
                            .background(Color(0xFF333333))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Пример прибора:",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            if (deviceImageUri != null) {
                                // Отображаем выбранную картинку прибора
                                val uri = android.net.Uri.parse(deviceImageUri)
                                // Используем LaunchedEffect для загрузки bitmap при изменении URI
                                val bitmapState = remember(uri) {
                                    androidx.compose.runtime.mutableStateOf<android.graphics.Bitmap?>(null)
                                }
                                LaunchedEffect(uri) {
                                    bitmapState.value = runCatching {
                                        android.graphics.BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri))
                                    }.getOrNull()
                                }
                                if (bitmapState.value != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = bitmapState.value!!.asImageBitmap(),
                                        contentDescription = "Картинка прибора",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    // Заглушка, если картинка не выбрана
                                    Text(
                                        text = "[Картинка прибора]\n(Настройка в меню маршрутов)",
                                        fontSize = 14.sp,
                                        color = Color.LightGray,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    // Нижняя часть - камера
                    Box(
                        modifier = Modifier
                            .weight(0.6f)
                            .fillMaxWidth()
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                initCamera(previewView)
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // Надпись "Сделайте фото" + имя чекпоинта поверх камеры
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.height(32.dp))
                            Text(
                                text = "Сделайте фото",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(16.dp)
                            )
                            Text(
                                text = checkpointId,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        
                        // Кнопка "Сделать фото"
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Button(
                                onClick = {
                                    capturePhoto()
                                },
                                modifier = Modifier.width(200.dp).height(56.dp),
                                shape = RoundedCornerShape(28.dp)
                            ) {
                                Text(text = "Сделать фото", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }
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
                
                // Кнопки управления
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Button(
                            onClick = {
                                // Сделать заново - возвращаемся к камере
                                isPreviewMode = false
                                capturedBitmap = null
                                lastPhotoPath = ""
                                // Удаляем старый файл
                                if (imageFile.exists()) {
                                    imageFile.delete()
                                }
                            },
                            modifier = Modifier.width(140.dp).height(56.dp),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Text(text = "Сделать заново", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        
                        Button(
                            onClick = {
                                // Сохраняем фото и возвращаемся в cabinet
                                // Сохраняем фото в галерею
                                val savedFileName = savePhotoToGallery(imageFile, checkpointId, context)
                                savedFileName?.let { onPhotoTaken(it) }
                                onBack()
                            },
                            modifier = Modifier.width(140.dp).height(56.dp),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Text(text = "Сохранить", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

// Функция для сохранения фото в галерею
fun savePhotoToGallery(sourceFile: File, checkpointId: String, context: Context): String? {
    try {
        android.widget.Toast.makeText(context, "Сохранение в галерею...", android.widget.Toast.LENGTH_SHORT).show()
        
        if (!sourceFile.exists()) {
            android.widget.Toast.makeText(context, "Исходный файл не найден!", android.widget.Toast.LENGTH_SHORT).show()
            return null
        }
        
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val destFileName = "${checkpointId.replace(" ", "_")}_${timestamp}.jpg"
        
        // Создаем папку в галерее
        val galleryDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val ohranaDir = File(galleryDir, "Ohrana")
        if (!ohranaDir.exists()) {
            ohranaDir.mkdirs()
            android.widget.Toast.makeText(context, "Создана папка: ${ohranaDir.absolutePath}", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        val destFile = File(ohranaDir, destFileName)
        
        // Копируем файл
        sourceFile.inputStream().use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        // Проверяем, что файл скопирован
        if (destFile.exists()) {
            android.widget.Toast.makeText(context, "Файл скопирован: ${destFile.absolutePath}", android.widget.Toast.LENGTH_SHORT).show()
            
            // Обновляем галерею через MediaScannerConnection
            MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                arrayOf("image/jpeg"),
                null
            )
            
            android.widget.Toast.makeText(context, "Галерея обновлена!", android.widget.Toast.LENGTH_SHORT).show()
            
            // Удаляем временный файл из private папки
            sourceFile.delete()
            android.widget.Toast.makeText(context, "Фото сохранено в галерее!", android.widget.Toast.LENGTH_SHORT).show()
            
            return destFileName
        } else {
            android.widget.Toast.makeText(context, "Ошибка: файл не создан!", android.widget.Toast.LENGTH_SHORT).show()
            return null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        android.widget.Toast.makeText(context, "Ошибка: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        return null
    }
}
