package com.example.ohrana

import com.example.ohrana.QrHandler
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OhrannikCabinetScreen(
    employeeName: String,
    onLogout: () -> Unit,
    onNavigateToReports: () -> Unit
) {
    val context = LocalContext.current
    val manager = remember(context) { SharedPrefsManager(context) }

    val lifecycleOwner = LocalLifecycleOwner.current

    // ИСПРАВЛЕНО: Берем точное сохраненное время старта смены из памяти устройства
    val shiftStartTime = remember { manager.getShiftStartTime() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Главный триггер захвата кадра при нажатии кнопки "Сканировать"
    var isScanRequested by remember { mutableStateOf(false) }

    // --- СОСТОЯНИЯ ДЛЯ ПОКАЗА ДИАЛОГОВЫХ ОКОН ---
    var showCheckpointPassedDialog by remember { mutableStateOf<QrResult.CheckpointPassed?>(null) }

    var showQuestionDialog by remember { mutableStateOf<QrResult.QuestionFormat?>(null) }
    var showInputDialog by remember { mutableStateOf<QrResult.InputFormat?>(null) }
    var showPhotoDialog by remember { mutableStateOf<QrResult.PhotoFormat?>(null) }
    var showErrorDialog by remember { mutableStateOf<String?>(null) }
    var inputTextValue by remember { mutableStateOf("") } // Текст внутри поля ввода показаний

    // Флаг для переключения камеры: сканирование или фото
    var isPhotoMode by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    LaunchedEffect(key1 = true) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    // Переключение режима камеры при открытии/закрытии диалога фото
    LaunchedEffect(showPhotoDialog) {
        if (showPhotoDialog != null) {
            // Открываем диалог фото — переключаем в режим фото
            isPhotoMode = true
            isScanRequested = false // Останавливаем сканирование
        } else {
            // Закрываем диалог фото — возвращаем сканирование
            isPhotoMode = false
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(employeeName) },
                navigationIcon = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Выход")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Смена открыта в: $shiftStartTime",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (hasCameraPermission) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 16.dp)
                ) {
                    // Контейнер для вывода картинки с камеры смартфона
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()

                                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                try {
                                    cameraProvider.unbindAll()
                                    
                                    // В режиме фото подключаем только preview, без imageAnalysis
                                    if (!isPhotoMode) {
                                        val options = com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
                                            .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE)
                                            .build()
                                        val scanner = com.google.mlkit.vision.barcode.BarcodeScanning.getClient(options)

                                        imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                            if (isScanRequested) {
                                                @Suppress("UnsafeOptInUsageError")
                                                val mediaImage = imageProxy.image
                                                if (mediaImage != null) {
                                                    val rotation = imageProxy.imageInfo.rotationDegrees
                                                    val image = com.google.mlkit.vision.common.InputImage.fromMediaImage(mediaImage, rotation)

                                                    // Получаем физические размеры кадра матрицы камеры
                                                    val imgWidth = imageProxy.width
                                                    val imgHeight = imageProxy.height

                                                    scanner.process(image)
                                                        .addOnSuccessListener { barcodes ->
                                                            for (barcode in barcodes) {
                                                                val rawValue = barcode.rawValue
                                                                val bounds = barcode.boundingBox

                                                                if (rawValue != null && bounds != null) {

                                                                    // 1. Вычисляем координаты центра QR-кода на матрице
                                                                    val qrCenterX = bounds.centerX().toFloat()
                                                                    val qrCenterY = bounds.centerY().toFloat()

                                                                    // 2. Определяем границы рамки прицела (центральные 30% от кадра)
                                                                    // Если камера повернута вертикально (90 или 270 град), меняем оси местами для корректности
                                                                    val isRotated = rotation == 90 || rotation == 270
                                                                    val frameWidth = if (isRotated) imgHeight else imgWidth
                                                                    val frameHeight = if (isRotated) imgWidth else imgHeight

                                                                    // Рассчитываем допустимый квадрат по центру кадра
                                                                    val minX = frameWidth * 0.35f
                                                                    val maxX = frameWidth * 0.65f
                                                                    val minY = frameHeight * 0.35f
                                                                    val maxY = frameHeight * 0.65f

                                                                    // 3. Проверка: попадает ли центр QR-кода в рассчитанную центральную область
                                                                    if (qrCenterX in minX..maxX && qrCenterY in minY..maxY) {

                                                                        // ТОЛЬКО ЕСЛИ КОД ВНУТРИ ПРИЦЕЛА — выполняем ваш оригинальный код:
                                                                        isScanRequested = false

                                                                        // Отправляем строку на разбор парсеру QrHandler
                                                                        val qrResult = QrHandler.parseQrCode(barcode.rawValue ?: "", manager)


                                                                        // --- НОВЫЙ БЛОК ОБРАБОТКИ РЕЗУЛЬТАТА ---
                                                                        when (qrResult) {
                                                                            is QrResult.CheckpointPassed -> {
                                                                                // Ваша логика при успешном проходе точки
                                                                                showCheckpointPassedDialog = qrResult
                                                                            }
                                                                            is QrResult.SequenceError -> {
                                                                                // Логика при ошибке последовательности
                                                                                android.widget.Toast.makeText(context, qrResult.message, android.widget.Toast.LENGTH_LONG).show()
                                                                            }
                                                                            is QrResult.QuestionFormat -> {
                                                                                // Показать диалог с вопросом
                                                                                showQuestionDialog = qrResult
                                                                            }
                                                                            is QrResult.InputFormat -> {
                                                                                // Показать диалог для ввода данных
                                                                                showInputDialog = qrResult
                                                                            }
                                                                            is QrResult.PhotoFormat -> {
                                                                                // Показать диалог для съемки фото
                                                                                isPhotoMode = true // Переключаем камеру в режим фото
                                                                                showPhotoDialog = qrResult
                                                                            }
                                                                            is QrResult.ShiftReportTrigger -> {
                                                                                onNavigateToReports()
                                                                            }
                                                                            is QrResult.Error -> {
                                                                                showErrorDialog = qrResult.message
                                                                            }
                                                                        }
                                                                        // -------------------------------------
                                                                        break // Прерываем цикл, так как нужный код найден и обработан
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        .addOnCompleteListener {
                                                            // Обязательно закрываем imageProxy, чтобы камера не «застывала»
                                                            imageProxy.close()
                                                        }
                                                } else {
                                                    imageProxy.close()
                                                }
                                            } else {
                                                imageProxy.close()
                                            }
                                        }

                                        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
                                    } else {
                                        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
                                    }
                                } catch (e: Exception) { 
                                    e.printStackTrace() 
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Рамка зеленого прицела по центру экрана
                    Box(
                        modifier = Modifier
                            .size(280.dp, 280.dp)
                            .align(Alignment.Center)
                            .border(BorderStroke(3.dp, Color.Green), RoundedCornerShape(16.dp))
                    )

                    // Кнопка под рамкой
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = { isScanRequested = true },
                            modifier = Modifier.width(200.dp).height(56.dp),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Text(text = "Сканировать", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
    // ================= РЕНДЕРИНГ ДИАЛОГОВЫХ ОКОН =================

    // 1. Диалог для обычной метки локации
    // --- РЕНДЕРИНГ ДИАЛОГА ОБ УСПЕШНОМ ПРОХОЖДЕНИИ ---
    showCheckpointPassedDialog?.let { result ->
        AlertDialog(
            onDismissRequest = { showCheckpointPassedDialog = null }, // Закрываем при клике вне окна или назад
            title = { Text("Точка зафиксирована") },
            text = {
                Column {
                    Text("Локация: ${result.name}")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("ID: ${result.checkpointId}")
                    Text("Время: ${result.timestamp}")
                }
            },
            confirmButton = {
                Button(onClick = { showCheckpointPassedDialog = null }) { Text("ОК") }
            }
        )
    }

    // 2. Диалог с ВОПРОСОМ и кнопками вариантов ответов
    showQuestionDialog?.let { result ->
        AlertDialog(
            onDismissRequest = { showQuestionDialog = null },
            title = { Text("Проверка чек-листа") },
            text = {
                Column {
                    Text(
                        text = result.text,
                        modifier = Modifier.padding(bottom = 12.dp),
                        fontWeight = FontWeight.Medium
                    )
                    result.answers.forEach { answer ->
                        Button(
                            onClick = {
                                val logText = "Чек-лист: ${result.text} -> Ответ: $answer"
                                manager.saveScanResult(employeeName = employeeName, qrContent = logText)
                                showQuestionDialog = null
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) { Text(answer) }
                    }
                }
            },
            confirmButton = {} // Кнопки подтверждения не нужны, клик по варианту сам закроет окно
        )
    }

    // 3. Диалог для ВВОДА ДАННЫХ (Показания счетчиков и т.д.)
    showInputDialog?.let { result ->
        AlertDialog(
            onDismissRequest = { showInputDialog = null },
            title = { Text("Ввод данных") },
            text = {
                Column {
                    Text(text = result.title, modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(
                        value = inputTextValue,
                        onValueChange = { inputTextValue = it },
                        label = { Text("Введите показания") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputTextValue.isNotBlank()) {
                            val logText = "Показания: ${result.title} -> Введено: $inputTextValue"
                            manager.saveScanResult(employeeName = employeeName, qrContent = logText)

                            inputTextValue = "" // ОЧИСТКА ПОЛЯ: Сбрасываем текст, чтобы поле было пустым в следующий раз
                            showInputDialog = null
                        }
                    }
                ) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = {
                    inputTextValue = "" // Очищаем поле при отмене
                    showInputDialog = null
                }) { Text("Отмена") }
            }
        )
    }

    // 4. Диалог для ФОТО (съемка прибора)
    showPhotoDialog?.let { result ->
        PhotoCaptureDialog(
            checkpointName = result.title,
            onPhotoTaken = { photoPath ->
                // Сохраняем путь к фото и метки в SharedPreferences
                val logText = "Фото прибора: ${result.title} -> Файл: $photoPath"
                manager.saveScanResult(employeeName = employeeName, qrContent = logText)
                showPhotoDialog = null
            },
            onDismiss = { showPhotoDialog = null }
        )
    }

    // 5. Диалог ошибки парсинга/сканирования
    showErrorDialog?.let { message ->
        AlertDialog(
            onDismissRequest = { showErrorDialog = null },
            title = { Text("Ошибка сканирования") },
            text = { Text(message) },
            confirmButton = { Button(onClick = { showErrorDialog = null }) { Text("ОК") } }
        )
    }
}

// Диалог для съемки фото прибора
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoCaptureDialog(
    checkpointName: String,
    onPhotoTaken: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Логер для отладки
    LaunchedEffect(Unit) {
        android.widget.Toast.makeText(context, "Фото прибора: $checkpointName", android.widget.Toast.LENGTH_SHORT).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Съемка прибора") },
        text = {
            Column {
                Text("Чекпоинт: $checkpointName")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Сделайте фото прибора и нажмите Сохранить", color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                
                // Камера через CameraX
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
                                cameraProvider.bindToLifecycle(ctx as androidx.lifecycle.LifecycleOwner, cameraSelector, preview)
                            } catch (e: Exception) { 
                                e.printStackTrace()
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Генерируем имя файла с именем чекпоинта и временем
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val fileName = "${checkpointName.replace(" ", "_")}_${timestamp}.jpg"
                    
                    onPhotoTaken(fileName)
                }
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

// Вспомогательная заглушка, чтобы не ломать вызовы в других частях проекта
@Composable
fun CameraScannerScreen(
    onScanRecognized: (String) -> Unit,
    modifier: Modifier = Modifier,
    onPreviewViewCreated: (PreviewView) -> Unit
) {
    Box(modifier = modifier.fillMaxSize())
}
