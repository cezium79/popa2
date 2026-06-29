package com.example.ohrana

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarshrutiScreen(
    onBack: () -> Unit,
    onNavigateToCheckpointEditor: (String?) -> Unit,
    onNavigateToSchedule: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefsManager = remember { SharedPrefsManager(context) }

    // 3. Обязательные точки обхода (QR)
    var newPointInput by remember { mutableStateOf("") }
    val checkpointList = remember { 
        val savedPoints = sharedPrefsManager.loadRouteCheckpoints()
        androidx.compose.runtime.mutableStateListOf<String>().apply {
            if (savedPoints.isNotEmpty()) {
                addAll(savedPoints)
            } else {
                addAll(listOf("Точка_Вход", "Точка_Склад_1", "Точка_Забор"))
            }
        }
    }

    // 4. Список чекпоинтов - загружаем и обновляем при изменении
    val allCheckpoints = remember { 
        androidx.compose.runtime.mutableStateListOf<Checkpoint>() 
    }
    
    // Синхронизируем список чекпоинтов при изменении
    LaunchedEffect(sharedPrefsManager.loadCheckpoints()) {
        allCheckpoints.clear()
        allCheckpoints.addAll(sharedPrefsManager.loadCheckpoints())
    }

    // Сохраняем точки обхода при изменении
    LaunchedEffect(checkpointList) {
        // Сохраняем только список чекпоинтов
        // Остальные настройки (roundsCount, times, tolerance) сохраняются в ScheduleScreen
        // и не должны перезаписываться здесь
        sharedPrefsManager.saveRouteCheckpoints(checkpointList.toList())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройка маршрутов и смен") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Кнопка для перехода к расписанию обходов
            Button(
                onClick = onNavigateToSchedule,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Расписание обходов", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // --- РАЗДЕЛ 1: ОБЯЗАТЕЛЬНЫЕ ТОЧКИ ОБХОДА ---
            Text("1. Обязательные контрольные точки (QR)", fontSize = 16.sp, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newPointInput,
                    onValueChange = { newPointInput = it },
                    label = { Text("ID точки или текст QR-кода") },
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        if (newPointInput.isNotBlank()) {
                            checkpointList.add(newPointInput.trim())
                            newPointInput = ""
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }

            // --- РАЗДЕЛ 2: СПИСОК ЧЕКПОИНТОВ С КНОПКАМИ РЕДАКТИРОВАНИЯ ---
            Text("2. Список чекпоинтов (${allCheckpoints.size} шт.)", fontSize = 16.sp, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            // Кнопка создания чекпоинта - сразу после оглавления
            Button(
                onClick = { onNavigateToCheckpointEditor(null) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("+ Создать чекпоинт")
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            if (allCheckpoints.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Чекпоинты еще не созданы",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Нажмите кнопку выше, чтобы создать новый чекпоинт",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                // Отображаем список чекпоинтов
                allCheckpoints.forEach { checkpoint ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = checkpoint.id,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = checkpoint.name,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Тип: ${checkpoint.action.name.lowercase()}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = { onNavigateToCheckpointEditor(checkpoint.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Редактировать",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            sharedPrefsManager.removeCheckpoint(checkpoint.id)
                                            // Перезагружаем список чекпоинтов
                                            allCheckpoints.clear()
                                            allCheckpoints.addAll(sharedPrefsManager.loadCheckpoints())
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Удалить",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Кнопка сохранения настроек
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    sharedPrefsManager.saveRouteAlarms(sharedPrefsManager.loadRouteAlarms())
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Сохранить настройки маршрута", fontSize = 16.sp)
            }

        }
    }
}

@Composable
@Preview(showBackground = true, name = "Marshruti Screen Preview")
fun MarshrutiScreenPreview() {
    MarshrutiScreen(onBack = {}, onNavigateToCheckpointEditor = {}, onNavigateToSchedule = {})
}
