package com.example.ohrana
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

// Модель свойств чекпоинта
data class CheckpointProperties(
    val id: String,
    val imageUri: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarshrutiScreen(
    onBack: () -> Unit,
    onNavigateToCheckpointProperties: (List<String>) -> Unit
) {
    var maxRoundDurationMinutes by remember { mutableStateOf("30") }
    val context = LocalContext.current

    // 🛡️ Создаем менеджер памяти (Имя переменной теперь строго согласовано с кодом ниже)
    val sharedPrefsManager = remember { SharedPrefsManager(context) }

    // ⏰ Инициализируем менеджер системных будильников
    val alarmScheduler = remember { AlarmScheduler(context) }

    // 📷 FilePicker для выбора картинки прибора
    // Текущий выбранный чекпоинт для привязки картинки
    var selectedCheckpointIdForImage by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                // Сохраняем URI для конкретного чекпоинта
                sharedPrefsManager.saveCheckpointImageUri(selectedCheckpointIdForImage, uri.toString())
                android.widget.Toast.makeText(context, "Картинка привязана к чекпоинту '$selectedCheckpointIdForImage'", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    )

    // 1. Количество обходов

    // 1. Количество обходов (Явно указываем <Int>, чтобы компилятор не путался)
    var roundsCount by remember { mutableStateOf<Int>(3) }

    // 2. Список объектов будильников (Все переменные теперь видят sharedPrefsManager)
    val routeAlarms = remember {
        val savedAlarms: List<RouteAlarm> = sharedPrefsManager.loadRouteAlarms()
        val initialList: List<RouteAlarm> = if (savedAlarms.isNotEmpty()) {
            savedAlarms
        } else {
            listOf(
                RouteAlarm(id = 1, time = "08:00", isEnabled = true),
                RouteAlarm(id = 2, time = "14:00", isEnabled = true),
                RouteAlarm(id = 3, time = "20:00", isEnabled = true)
            )
        }
        val stateList = androidx.compose.runtime.mutableStateListOf<RouteAlarm>()
        stateList.addAll(initialList)
        stateList
    }
    LaunchedEffect(Unit) {
        val localPrefs = context.getSharedPreferences("OhranaPrefs", android.content.Context.MODE_PRIVATE)
        maxRoundDurationMinutes = localPrefs.getString("max_round_duration_key", "30") ?: "30"
    }
    // Синхронизация количества полей времени со счетчиком обходов
    LaunchedEffect(roundsCount) {
        if (routeAlarms.size < roundsCount) {
            while (routeAlarms.size < roundsCount) {
                val nextId = routeAlarms.size + 1
                routeAlarms.add(RouteAlarm(id = nextId, time = "", isEnabled = true))
            }
        } else if (routeAlarms.size > roundsCount) {
            while (routeAlarms.size > roundsCount) {
                routeAlarms.removeAt(routeAlarms.lastIndex)
            }
        }
    }

    // 🔥 АВТОКОРРЕКТОР: Преобразуем список в стабильную строку-ключ, чтобы не ломать типы Kotlin
    val alarmsTriggerKey: String = routeAlarms.joinToString(separator = ",") { "${it.time}-${it.isEnabled}" }

    LaunchedEffect(alarmsTriggerKey) {
        val currentAlarmsList: List<RouteAlarm> = routeAlarms.toList()
        alarmScheduler.updateAlarms(currentAlarmsList)
    }


    // Допуск к началу обхода (+- минут)
    var timeToleranceMinutes by remember { mutableStateOf("15") }

    // 3. Обязательные точки обхода (QR)
    var newPointInput by remember { mutableStateOf("") }
    val checkpointList = remember { mutableStateListOf("Точка_Вход", "Точка_Склад_1", "Точка_Забор") }

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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // --- РАЗДЕЛ 1: КОЛИЧЕСТВО ОБХОДОВ ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("1. Обязательное количество обходов", fontSize = 16.sp, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Button(onClick = { if (roundsCount > 1) roundsCount-- }) {
                                Text("-", fontSize = 20.sp)
                            }
                            Text("$roundsCount обходов за смену", fontSize = 18.sp)
                            Button(onClick = { roundsCount++ }) {
                                Text("+", fontSize = 20.sp)
                            }
                        }
                    }
                }
            }

            // --- РАЗДЕЛ 2: ДИНАМИЧЕСКОЕ РАСПИСАНИЕ, ПЕРЕКЛЮЧАТЕЛИ И ДОПУСКИ ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("2. Расписание и временные рамки", fontSize = 16.sp, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(12.dp))

                        routeAlarms.forEachIndexed { index, alarm ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Поле ввода времени (Занимает основную ширину)
                                OutlinedTextField(
                                    value = alarm.time,
                                    onValueChange = { newValue ->
                                        routeAlarms[index] = alarm.copy(time = newValue)
                                    },
                                    label = { Text("Время обхода №${alarm.id} (например, 08:00)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )

                                // 🔘 Переключатель подтверждения / игнорирования будильника
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = if (alarm.isEnabled) "Вкл" else "Игнор",
                                        fontSize = 12.sp,
                                        color = if (alarm.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    )
                                    Switch(
                                        checked = alarm.isEnabled,
                                        onCheckedChange = { isChecked ->
                                            routeAlarms[index] = alarm.copy(isEnabled = isChecked)
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = timeToleranceMinutes,
                            onValueChange = { timeToleranceMinutes = it },
                            label = { Text("Допуск к началу обхода (+- минут)") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // 🔥 НОВОЕ ПОЛЕ: Максимальное время на сам обход
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = maxRoundDurationMinutes,
                            onValueChange = { maxRoundDurationMinutes = it },
                            label = { Text("Максимальное время на обход (в минутах)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            )
                        )

                    }
                }
            }

            // --- РАЗДЕЛ 3: ОБЯЗАТЕЛЬНЫЕ ТОЧКИ ОБХОДА ---
            item {
                Text("3. Обязательные контрольные точки (QR)", fontSize = 16.sp, style = MaterialTheme.typography.titleMedium)
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
                                // Сохраняем список чекпоинтов в SharedPreferences
                                sharedPrefsManager.saveRouteSettings(
                                    roundsCount = roundsCount,
                                    times = routeAlarms.map { it.time },
                                    tolerance = timeToleranceMinutes,
                                    checkpoints = checkpointList.toList()
                                )
                                newPointInput = ""
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Добавить", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }

            items(checkpointList) { checkpoint ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = checkpoint,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Кнопка привязки картинки к чекпоинту
                        IconButton(
                            onClick = {
                                selectedCheckpointIdForImage = checkpoint
                                imagePickerLauncher.launch("image/*")
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Привязать картинку",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                checkpointList.remove(checkpoint)
                                // Сохраняем список чекпоинтов в SharedPreferences при удалении
                                sharedPrefsManager.saveRouteSettings(
                                    roundsCount = roundsCount,
                                    times = routeAlarms.map { it.time },
                                    tolerance = timeToleranceMinutes,
                                    checkpoints = checkpointList.toList()
                                )
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

            // --- РАЗДЕЛ 4: РЕДАКТИРОВАНИЕ СВОЙСТВ ЧЕКПОИНТОВ ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("4. Настройка свойств чекпоинтов", fontSize = 16.sp, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Управление привязанными картинками для всех чекпоинтов",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { onNavigateToCheckpointProperties(checkpointList) },
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Text("Редактировать свойства чекпоинтов")
                        }
                    }
                }
            }

            // Кнопка сохранения настроек
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        // Сохраняем в память телефона перед выходом
                        sharedPrefsManager.saveRouteAlarms(routeAlarms.toList())
                        // 🔥 Записываем продолжительность обхода в память устройства
                        val localPrefs = context.getSharedPreferences("OhranaPrefs", android.content.Context.MODE_PRIVATE)
                        localPrefs.edit().putString("max_round_duration_key", maxRoundDurationMinutes).apply()

                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Сохранить настройки маршрута", fontSize = 16.sp)
                }

            }
        }
    }
}

@Composable
@Preview(showBackground = true, name = "Marshruti Screen Preview")
fun MarshrutiScreenPreview() {
    MarshrutiScreen(onBack = {}, onNavigateToCheckpointProperties = {})
}