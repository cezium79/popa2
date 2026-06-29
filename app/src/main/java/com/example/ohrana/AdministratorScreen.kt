package com.example.ohrana

import android.app.Activity
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdministratorScreen(
    onNavigateToEmployeeList: () -> Unit,
    onNavigateToArchive: () -> Unit,
    onNavigateToRoutes: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefsManager = remember { SharedPrefsManager(context) }

    var isStrictSequence by remember { mutableStateOf(prefsManager.isStrictSequenceEnabled()) }
    
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showImportResultDialog by remember { mutableStateOf(false) }
    var importResultMessage by remember { mutableStateOf("") }
    
    // Переменные для хранения результатов запросов разрешений и выбора файлов
    val storagePermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                // Разрешение получено, экспортируем
                val result = prefsManager.exportSettingsToFile(context)
                if (result) {
                    android.widget.Toast.makeText(
                        context,
                        "Настройки экспортированы успешно! Файл в Download/Ohrana/",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                } else {
                    android.widget.Toast.makeText(
                        context,
                        "Ошибка экспорта! Проверь логи.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                // Разрешение отклонено
                android.widget.Toast.makeText(
                    context,
                    "Для экспорта необходимо разрешение на запись",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    )
    
    // Ланчер для выбора файла импорта
    val importFileLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                // Пытаемся прочитать файл
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val jsonString = inputStream.bufferedReader().use { it.readText() }
                        // Пытаемся импортировать как JSON, если не удалось - как текст
                        val result = if (jsonString.startsWith("{") || jsonString.startsWith("[")) {
                            prefsManager.importSettings(jsonString)
                        } else {
                            prefsManager.importSettingsFromText(jsonString)
                        }
                        importResultMessage = if (result) {
                            "Настройки импортированы из файла! Перезапустите приложение."
                        } else {
                            "Ошибка импорта из файла!"
                        }
                        showImportResultDialog = true
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AdministratorScreen", "Error reading import file: ${e.message}", e)
                    importResultMessage = "Ошибка при чтении файла: ${e.message}"
                    showImportResultDialog = true
                }
            }
        }
    )

    // Диалог подтверждения экспорта
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Экспорт настроек") },
            text = { Text("Настройки будут сохранены в файл. Вы уверены?") },
            confirmButton = {
                Button(onClick = {
                    showExportDialog = false
                    // Запрашиваем разрешение на запись
                    storagePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }) { Text("Да") }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("Отмена") }
            }
        )
    }

    // Диалог импорта
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Импорт настроек") },
            text = { Text("Выберите файл резервной копии для восстановления настроек.") },
            confirmButton = {
                Button(onClick = {
                    showImportDialog = false
                    // Открываем системный файловый менеджер для выбора файла
                    importFileLauncher.launch("*/*")
                }) { Text("Выбрать файл") }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("Отмена") }
            }
        )
    }

    // Диалог результата импорта
    if (showImportResultDialog) {
        AlertDialog(
            onDismissRequest = { showImportResultDialog = false },
            title = { Text("Результат импорта") },
            text = { Text(importResultMessage) },
            confirmButton = {
                Button(onClick = { showImportResultDialog = false }) { Text("OK") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Панель администратора") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Кнопки навигации
            Button(
                onClick = onNavigateToEmployeeList,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).height(56.dp)
            ) {
                Text("Редактировать список сотрудников", fontSize = 16.sp)
            }
            
            Button(
                onClick = onNavigateToRoutes,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).height(56.dp)
            ) {
                Text("Редактирование маршрутов", fontSize = 16.sp)
            }

            Button(
                onClick = onNavigateToArchive,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Архив отчетов", fontSize = 16.sp)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Настройки контроля последовательности
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Строгий контроль",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            text = "Охранник обязан сканировать точки строго по порядку",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = isStrictSequence,
                        onCheckedChange = { isChecked ->
                            isStrictSequence = isChecked
                            prefsManager.setStrictSequenceEnabled(isChecked)
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Кнопки экспорта/импорта
            Text(
                text = "Резервное копирование",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { showExportDialog = true },
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Экспорт", fontSize = 16.sp)
                }
                
                Button(
                    onClick = { showImportDialog = true },
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Импорт", fontSize = 16.sp)
                }
            }
        }
    }
}
