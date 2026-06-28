package com.example.ohrana

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdministratorScreen(
    onNavigateToEmployeeList: () -> Unit,
    onNavigateToArchive: () -> Unit, // Новый обработчик для перехода в архив
    onNavigateToRoutes: () -> Unit,
    onBack: () -> Unit
) {
    // Получаем контекст для инициализации менеджера настроек
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefsManager = remember { SharedPrefsManager(context) }

    // Состояние чекбокса, считанное из SharedPreferences
    var isStrictSequence by remember { mutableStateOf(prefsManager.isStrictSequenceEnabled()) }

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
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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

            // Новая кнопка "Архив"
            Button(
                onClick = onNavigateToArchive,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Архив отчетов", fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Новый блок настройки контроля последовательности сканирования
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
                            prefsManager.setStrictSequenceEnabled(isChecked) // Сохраняем на диск

                            // КОНТРОЛЬ: Читаем значение из памяти СРАЗУ после сохранения и выводим на экран
                            val checkFromDisk = prefsManager.isStrictSequenceEnabled()
                            android.widget.Toast.makeText(
                                context,
                                "Записано на диск: $checkFromDisk",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }

        }
    }
}
