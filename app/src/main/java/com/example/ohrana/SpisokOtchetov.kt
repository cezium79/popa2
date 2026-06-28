package com.example.ohrana

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable

fun SpisokOtchetovScreen(
    onBack: () -> Unit,
    onBackToAdmin: () -> Unit,
    previousScreenWasAdmin: Boolean
) {
    val context = LocalContext.current
    val prefsManager = remember { SharedPrefsManager(context) }
    val reportsList = remember { prefsManager.getReportsList() }
    val arrowBackIcon = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Архив отчетов (Excel/CSV)") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (previousScreenWasAdmin) {
                            onBackToAdmin()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(arrowBackIcon, contentDescription = "Назад")
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
            if (reportsList.isEmpty()) {
                Text(
                    text = "Архив пуст. Сгенерированных отчетов не найдено.",
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(reportsList) { file ->
                        // 🔥 Парсим имя файла "Фамилия_Имя_Дата_Время.csv"
                        val displayName = remember(file.name) {
                            try {
                                val nameWithoutExt = file.nameWithoutExtension
                                val parts = nameWithoutExt.split("_")

                                if (parts.size >= 4) {
                                    val lastName = parts[0]
                                    val firstName = parts[1]
                                    val datePart = parts[2]
                                    val timePart = parts[3].replace("-", ":") // Заменяем дефис на двоеточие во времени

                                    "$lastName $firstName | $datePart в $timePart"
                                } else {
                                    // Если файл старого формата или имя повреждено, показываем как есть
                                    file.name
                                }
                            } catch (e: Exception) {
                                file.name
                            }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { openFileInSystem(context, file) }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    // Выводим красивое распарсенное имя вместо file.name
                                    Text(text = displayName, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        text = "${file.length() / 1024} Кб",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text("Открыть", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}


// Функция системного открытия CSV файла внешними табличными редакторами (Excel, Гугл Таблицы)
private fun openFileInSystem(context: Context, file: File) {
    try {
        // Укажите ваш applicationId + ".fileprovider" в соответствии с Манифестом
        val authority = "${context.packageName}.fileprovider"
        val uri: Uri = FileProvider.getUriForFile(context, authority, file)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/csv")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(Intent.createChooser(intent, "Открыть отчет через:"))
    } catch (e: Exception) {
        Toast.makeText(context, "Нет приложения для открытия CSV/Excel файлов", Toast.LENGTH_SHORT).show()
        e.printStackTrace()
    }
}


