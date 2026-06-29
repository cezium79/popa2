package com.example.ohrana

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
import androidx.compose.runtime.LaunchedEffect
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefsManager = remember { SharedPrefsManager(context) }
    val alarmScheduler = remember { AlarmScheduler(context) }

    // 1. Количество обходов
    // Загружаем данные из SharedPreferences при каждом открытии экрана
    val initialRoundsCount = sharedPrefsManager.loadRouteRoundsCount()
    
    // 2. Список объектов будильников
    val savedAlarms: List<RouteAlarm> = sharedPrefsManager.loadRouteAlarms()
    Log.d("ScheduleScreen", "Loaded ${savedAlarms.size} alarms from SharedPreferences: $savedAlarms")
    
    // Загружаем время и допуск из SharedPreferences при каждом открытии экрана
    val initialTimeTolerance = sharedPrefsManager.loadRouteTolerance()
    val initialMaxDuration = sharedPrefsManager.loadMaxRoundDuration()

    // Загружаем чекпоинты для сохранения
    val routeCheckpoints = remember {
        sharedPrefsManager.loadRouteCheckpoints()
    }
    
    // Определяем начальное количество раундов на основе загруженных будильников
    // Это предотвращает синхронизацию с устаревшим значением из SharedPreferences
    val initialRoundsFromAlarms = remember { savedAlarms.size }
    
    // Объявляем переменные с начальными значениями
    // При первом запуске используем количество будильников как количество раундов
    var roundsCount by remember { mutableStateOf(initialRoundsFromAlarms) }
    var routeAlarms by remember { mutableStateOf(savedAlarms.toMutableList()) }
    var timeToleranceMinutes by remember { mutableStateOf(initialTimeTolerance) }
    var maxRoundDurationMinutes by remember { mutableStateOf(initialMaxDuration) }
    
    // Флаг для предотвращения сохранения при первом запуске
    var isFirstLaunch by remember { mutableStateOf(true) }
    
    // Логирование для отладки
    androidx.compose.runtime.SideEffect {
        if (isFirstLaunch) {
            Log.d("ScheduleScreen", "=== FIRST LAUNCH: initializing from SharedPreferences ===")
            Log.d("ScheduleScreen", "  initialRoundsCount=$initialRoundsCount (из SharedPreferences)")
            Log.d("ScheduleScreen", "  initialRoundsFromAlarms=$initialRoundsFromAlarms (из будильников)")
            Log.d("ScheduleScreen", "  initialTimeTolerance=$initialTimeTolerance")
            Log.d("ScheduleScreen", "  initialMaxDuration=$initialMaxDuration")
            Log.d("ScheduleScreen", "  savedAlarms=${savedAlarms.size} alarms")
        }
        Log.d("ScheduleScreen", "=== routeAlarms updated: ${routeAlarms.size} alarms ===")
        routeAlarms.forEach { alarm ->
            Log.d("ScheduleScreen", "  - Alarm ${alarm.id}: time='${alarm.time}', enabled=${alarm.isEnabled}")
        }
    }

    // Синхронизация количества полей времени со счетчиком обходов
    // Не сохраняет в SharedPreferences, только обновляет UI
    LaunchedEffect(roundsCount) {
        routeAlarms = routeAlarms.toMutableList().apply {
            if (size < roundsCount) {
                while (size < roundsCount) {
                    val nextId = size + 1
                    add(RouteAlarm(id = nextId, time = "", isEnabled = true))
                }
            } else if (size > roundsCount) {
                while (size > roundsCount) {
                    removeAt(lastIndex)
                }
            }
        }
    }

    // Сохраняем настройки при изменении
    LaunchedEffect(roundsCount, routeAlarms, timeToleranceMinutes, maxRoundDurationMinutes) {
        // Пропускаем сохранение при первом запуске (только при изменении значений)
        if (isFirstLaunch) {
            Log.d("ScheduleScreen", "First launch - skipping save")
            isFirstLaunch = false
            return@LaunchedEffect
        }
        
        Log.d("ScheduleScreen", "Saving settings: rounds=$roundsCount, alarms=${routeAlarms.size}, tolerance=$timeToleranceMinutes, maxDuration=$maxRoundDurationMinutes")
        
        sharedPrefsManager.saveRouteSettings(
            roundsCount = roundsCount,
            times = routeAlarms.map { it.time },
            tolerance = timeToleranceMinutes,
            checkpoints = sharedPrefsManager.loadRouteCheckpoints()
        )
        
        // Сохраняем максимальную длительность обхода в тот же файл
        sharedPrefsManager.saveMaxRoundDuration(maxRoundDurationMinutes)
        
        // Сохраняем будильники
        sharedPrefsManager.saveRouteAlarms(routeAlarms)
        Log.d("ScheduleScreen", "Alarms saved to SharedPrefs")
        
        // Обновляем будильники
        alarmScheduler.updateAlarms(routeAlarms)
        Log.d("ScheduleScreen", "Alarms updated in scheduler")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Расписание обходов") },
                navigationIcon = {
                    IconButton(onClick = {
                        Log.d("ScheduleScreen", "Back button clicked")
                        onBack()
                    }) {
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

            // --- РАЗДЕЛ 1: КОЛИЧЕСТВО ОБХОДОВ ---
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

            // --- РАЗДЕЛ 2: ДИНАМИЧЕСКОЕ РАСПИСАНИЕ, ПЕРЕКЛЮЧАТЕЛИ И ДОПУСКИ ---
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
                            // Поле ввода времени
                            OutlinedTextField(
                                value = alarm.time,
                                onValueChange = { newValue ->
                                    routeAlarms = routeAlarms.toMutableList().apply {
                                        this[index] = alarm.copy(time = newValue)
                                    }
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
                                        routeAlarms = routeAlarms.toMutableList().apply {
                                            this[index] = alarm.copy(isEnabled = isChecked)
                                        }
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

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = maxRoundDurationMinutes,
                        onValueChange = { maxRoundDurationMinutes = it },
                        label = { Text("Максимальное время на обход (в минутах)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                }
            }

            // Кнопка сохранения
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    Log.d("ScheduleScreen", "Save button clicked!")
                    // Сохраняем настройки маршрута
                    sharedPrefsManager.saveRouteSettings(
                        roundsCount = roundsCount,
                        times = routeAlarms.map { it.time },
                        tolerance = timeToleranceMinutes,
                        checkpoints = sharedPrefsManager.loadRouteCheckpoints()
                    )
                    
                    // Сохраняем максимальное время на обход
                    sharedPrefsManager.saveMaxRoundDuration(maxRoundDurationMinutes)
                    Log.d("ScheduleScreen", "Max duration saved: $maxRoundDurationMinutes")
                    
                    // Сохраняем будильники
                    Log.d("ScheduleScreen", "Calling saveRouteAlarms with ${routeAlarms.size} alarms")
                    sharedPrefsManager.saveRouteAlarms(routeAlarms)
                    Log.d("ScheduleScreen", "saveRouteAlarms completed")
                    
                    // Обновляем будильники
                    alarmScheduler.updateAlarms(routeAlarms)
                    
                    Log.d("ScheduleScreen", "All settings saved successfully!")
                    
                    Log.d("ScheduleScreen", "Closing screen...")
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Сохранить расписание", fontSize = 16.sp)
            }

        }
    }
}
