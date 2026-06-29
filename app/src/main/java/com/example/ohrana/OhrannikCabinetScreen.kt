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
import android.content.Context
import android.os.Build
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import android.graphics.BitmapFactory
import android.provider.MediaStore
import android.nfc.NfcAdapter
import androidx.activity.ComponentActivity


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OhrannikCabinetScreen(
    employeeName: String,
    onLogout: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToPhoto: (SharedPrefsManager, String) -> Unit
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
    
    var hasStoragePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Главный триггер захвата кадра при нажатии кнопки "Сканировать"
    var isScanRequested by remember { mutableStateOf(false) }

    // --- СОСТОЯНИЯ ДЛЯ ПОКАЗА ДИАЛОГОВЫХ ОКОН ---
    var showCheckpointPassedDialog by remember { mutableStateOf<QrResult.CheckpointPassed?>(null) }

    var showQuestionDialog by remember { mutableStateOf<QrResult.QuestionFormat?>(null) }
    var showInputDialog by remember { mutableStateOf<QrResult.InputFormat?>(null) }
    var showErrorDialog by remember { mutableStateOf<String?>(null) }
    var inputTextValue by remember { mutableStateOf("") } // Текст внутри поля ввода показаний
    
    // Для съемки фото
    var photoCheckpointId by remember { mutableStateOf("") }
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )
    
    val storageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasStoragePermission = granted }
    )
    
    // Запуск экрана фото при изменении photoCheckpointId
    LaunchedEffect(photoCheckpointId) {
        if (photoCheckpointId.isNotEmpty()) {
            // Переход на экран фото с передачей manager и ID чекпоинта
            onNavigateToPhoto(manager, photoCheckpointId)
            // Очищаем после использования
            photoCheckpointId = ""
        }
    }
    
    // NFC сканер включён постоянно для автоматического считывания NFC-тегов
    val activity = context as? ComponentActivity
    var nfcAdapter by remember { mutableStateOf<NfcAdapter?>(null) }
    var nfcScanResult by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(Unit) {
        try {
            nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        } catch (e: Exception) {
            // NFC не поддерживается
        }
    }
    
    // Всегда включаем NFC reader mode для фонового сканирования
    LaunchedEffect(activity) {
        if (nfcAdapter != null && activity != null) {
            try {
                nfcAdapter?.enableReaderMode(
                    activity,
                    { tag ->
                        val nfcId = tag.id.joinToString(":") { byte -> String.format("%02X", byte) }
                        nfcScanResult = nfcId
                    },
                    NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B,
                    null
                )
            } catch (e: Exception) {
                // Error handling
            }
        }
    }
    
    // Обработка NFC сканирования - ищем чекпоинт по NFC-ID
    LaunchedEffect(nfcScanResult) {
        if (nfcScanResult != null) {
            val scannedNfcId = nfcScanResult!!
            val checkpoint = manager.getCheckpointByNfcId(scannedNfcId)
            
            if (checkpoint != null) {
                // NFC-ID найден в базе - обрабатываем в зависимости от типа
                when (checkpoint.action) {
                    CheckpointAction.CHECKPOINT -> {
                        // Сохраняем в логи SharedPreferences
                        val logText = "NFC-чекпоинт: ${checkpoint.name} -> ID: ${checkpoint.id}"
                        manager.saveScanResult(employeeName = employeeName, qrContent = logText)
                        
                        // Добавляем в shiftLogs для отображения в журнале текущих обходов
                        val currentTime = java.text.SimpleDateFormat("HH:mm:ss dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date())
                        QrHandler.addCheckpointToLog(
                            type = "NFC-чекпоинт",
                            titleOrLocation = "${checkpoint.name} (ID: ${checkpoint.id})",
                            userResult = "Отметка пройдена",
                            timestamp = currentTime
                        )
                        
                        showCheckpointPassedDialog = QrResult.CheckpointPassed(
                            checkpointId = checkpoint.id,
                            name = checkpoint.name,
                            timestamp = currentTime
                        )
                    }
                    CheckpointAction.QUESTION -> {
                        // Диалог с вопросом сохранит результат при выборе ответа
                        showQuestionDialog = QrResult.QuestionFormat(
                            checkpointId = checkpoint.id,
                            checkpointName = checkpoint.name,
                            text = checkpoint.questionText ?: "",
                            answers = checkpoint.answers
                        )
                    }
                    CheckpointAction.INPUT -> {
                        // Диалог с вводом сохранит результат при нажатии "Сохранить"
                        showInputDialog = QrResult.InputFormat(
                            checkpointId = checkpoint.id,
                            checkpointName = checkpoint.name,
                            title = checkpoint.inputTitle ?: ""
                        )
                    }
                    CheckpointAction.PHOTO -> {
                        // PhotoFormat не сохраняется в логи автоматически - только при отправке фото
                        photoCheckpointId = checkpoint.id
                    }
                }
            } else {
                // NFC-ID не найден в базе
                showErrorDialog = "NFC-тег не найден в базе чекпоинтов"
            }
            
            nfcScanResult = null
        }
    }
    


    LaunchedEffect(key1 = true) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
        if (!hasStoragePermission) {
            storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
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
                                                                        // Запоминаем ID чекпоинта и переходим на экран фото
                                                                        photoCheckpointId = qrResult.checkpointId
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


                                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                try {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
                                } catch (e: Exception) { e.printStackTrace() }
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

                    // Кнопка под рамкой: QR
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
                            Text(text = "Сканировать QR", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                        text = result.questionText,
                        modifier = Modifier.padding(bottom = 12.dp),
                        fontWeight = FontWeight.Medium
                    )
                    result.answers.forEach { answer ->
                        Button(
                            onClick = {
                                val logText = "Чек-лист: ${result.questionText} -> Ответ: $answer"
                                manager.saveScanResult(employeeName = employeeName, qrContent = logText)
                                
                                // Добавляем в shiftLogs для отображения в журнале текущих обходов
                                val currentTime = java.text.SimpleDateFormat("HH:mm:ss dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date())
                                QrHandler.addCheckpointToLog(
                                    type = "NFC-чекпоинт",
                                    titleOrLocation = "${result.checkpointName} (ID: ${result.checkpointId})",
                                    userResult = "Отметка пройдена",
                                    timestamp = currentTime
                                )
                                
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
                    Text(text = result.titleText, modifier = Modifier.padding(bottom = 8.dp))
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
                            val logText = "Показания: ${result.titleText} -> Введено: $inputTextValue"
                            manager.saveScanResult(employeeName = employeeName, qrContent = logText)
                            
                            // Добавляем в shiftLogs для отображения в журнале текущих обходов
                            val currentTime = java.text.SimpleDateFormat("HH:mm:ss dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date())
                            QrHandler.addCheckpointToLog(
                                type = "NFC-чекпоинт",
                                titleOrLocation = "${result.checkpointName} (ID: ${result.checkpointId})",
                                userResult = "Отметка пройдена",
                                timestamp = currentTime
                            )

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

    // 4. Диалог ошибки парсинга/сканирования
    showErrorDialog?.let { message ->
        AlertDialog(
            onDismissRequest = { showErrorDialog = null },
            title = { Text("Ошибка сканирования") },
            text = { Text(message) },
            confirmButton = { Button(onClick = { showErrorDialog = null }) { Text("ОК") } }
        )
    }
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
