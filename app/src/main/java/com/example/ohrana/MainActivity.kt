package com.example.ohrana

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppNavigation()
        }
    }
}
@Composable
fun AdminPasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    isError: Boolean
) {
    var passwordInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Доступ ограничен") },
        text = {
            Column {
                Text("Введите пароль администратора:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("Пароль") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = isError,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isError) {
                    Text(
                        text = "Неверный пароль. Попробуйте еще раз.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(passwordInput) }) { Text("Вход") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}


@Composable
fun AppNavigation() {


    var previousScreenWasAdmin by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf("privet") }
    var selectedEmployeeName by remember { mutableStateOf("") }
    var selectedCheckpointName by remember { mutableStateOf("") }

    val context = LocalContext.current
    val prefsManager = remember { SharedPrefsManager(context) }

    // Храним список чекпоинтов для навигации в CheckpointPropertiesScreen
    var checkpointListForProps by remember { mutableStateOf<List<String>>(emptyList()) }

    val employeeList = remember {
        val savedList = prefsManager.loadEmployees()
        val initialList = if (savedList.isEmpty()) {
            listOf(
                Employee(name = "Иванов Иван", role = "Старший смены"),
                Employee(name = "Петров Петр", role = "Охранник")
            )
        } else {
            savedList
        }
        mutableStateListOf<Employee>().apply { addAll(initialList) }
    }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var isPasswordError by remember { mutableStateOf(false) }


    // 🔔 ВСПЛЫВАЮЩЕЕ ДИАЛОГОВОЕ ОКНО
    if (showPasswordDialog) {
        AdminPasswordDialog(
            onDismiss = { showPasswordDialog = false; isPasswordError = false },
            onConfirm = { enteredPassword ->
                if (enteredPassword == "1234")// Пароль для входа в окно настроек
                {
                    showPasswordDialog = false
                    isPasswordError = false
                    currentScreen = "admin"
                } else {
                    isPasswordError = true
                }
            },
            isError = isPasswordError
        )
    }

    when (currentScreen) {
        "privet" -> PrivetScreen(
            onNavigateToOhrannik = {
                // Читаем имя и состояние флага напрямую из хранилища
                val activeGuardName = prefsManager.getActiveShiftEmployeeName()
                val isShiftRunning = prefsManager.isShiftActive() // Используем чистый флаг!

                // Если флаг равен true И имя действительно записано
                if (isShiftRunning && activeGuardName.isNotEmpty() && activeGuardName != "-") {
                    selectedEmployeeName = activeGuardName
                    currentScreen = "shift_control"
                } else {
                    // Если смена закрыта — гарантированно очищаем оперативку и открываем список
                    selectedEmployeeName = ""
                    currentScreen = "ohrannik"
                }
            },
            onNavigateToAdministrator = { showPasswordDialog = true }
        )




        "ohrannik" -> OhrannikScreen(
            employees = employeeList,
            onSelectEmployee = { employee ->
                selectedEmployeeName = employee.name

                // УМНЫЙ ПЕРЕХВАТ: Если смена у этого сотрудника уже активна — сразу шлем в камеру
                if (prefsManager.isShiftActiveFor(employee.name)) {
                    currentScreen = "ohrannik_cabinet"
                } else {
                    // Если смены нет — отправляем на экран открытия смены (Старт/Стоп)
                    currentScreen = "shift_control"
                }
            },
            onBack = {
                currentScreen = "privet"
                //  стираем имя из оперативной памяти приложения если сразу выходим не выбирая фамилии
                selectedEmployeeName = "" }
        )



        "shift_control" -> OhrannikShiftControlScreen(
            employeeName = selectedEmployeeName,
            selectedEmployeeName = selectedEmployeeName,
            onStartShiftSuccess = {
                prefsManager.startNewShift(selectedEmployeeName, prefsManager.isStrictSequenceEnabled())
                currentScreen = "ohrannik_cabinet"
            },
            onContinueShift = {
                currentScreen = "ohrannik_cabinet"
            },
            onShiftClosedSuccess = {
                // ИСПРАВЛЕНО: Убрали дублирующий вызов closeCurrentShift(),
                // так как кнопка СТОП уже сделала это перед созданием отчета!

                selectedEmployeeName = "" // Просто стираем имя из оперативной памяти
                previousScreenWasAdmin = false
                currentScreen = "privet" // Возвращаемся в начало
            },
            onBack = {
                currentScreen = "privet"
            }
        )

        "ohrannik_cabinet" -> OhrannikCabinetScreen(
            // ИСПРАВЛЕНО: параметр внутри функции называется employeeName!
            employeeName = selectedEmployeeName,
            onLogout = {
                currentScreen = "privet"
            },
            onNavigateToReports = {
                previousScreenWasAdmin = false
                currentScreen = "spisok_otchetov"
            },
            onNavigateToPhoto = { manager, checkpointName ->
                selectedCheckpointName = checkpointName
                currentScreen = "photo_capture"
            }
        )

        // Экран захвата фото
        "photo_capture" -> PhotoCaptureScreen(
            checkpointName = selectedCheckpointName,
            onPhotoTaken = { fileName ->
                // Сохраняем путь к фото в SharedPreferences
                val logText = "Фото прибора: $selectedCheckpointName -> Файл: $fileName"
                prefsManager.saveScanResult(employeeName = selectedEmployeeName, qrContent = logText)
            },
            onBack = {
                // При возврате очищаем ID чекпоинта и возвращаемся в cabinet
                selectedCheckpointName = ""
                currentScreen = "ohrannik_cabinet"
            },
            prefsManager = prefsManager,
            employeeName = selectedEmployeeName
        )



        // ТО ЧЕГО НЕ ХВАТАЛО: Экран Администратора
        "admin" -> AdministratorScreen(
            onNavigateToEmployeeList = { currentScreen = "employee_list" },
            onNavigateToArchive = {
                previousScreenWasAdmin = true
                currentScreen = "spisok_otchetov"
            },
            onNavigateToRoutes = { currentScreen = "routes" }, // <-- ДОБАВЛЕНО
            onBack = { currentScreen = "privet" }
        )

        // Экран управления маршрутами (Новый блок)
        "routes" -> MarshrutiScreen(
            onNavigateToCheckpointProperties = { checkpoints -> 
                checkpointListForProps = checkpoints
                currentScreen = "checkpoint_properties" 
            },
            onBack = { currentScreen = "admin" }
        )

        // Экран редактирования свойств чекпоинтов
        "checkpoint_properties" -> CheckpointPropertiesScreen(
            checkpointList = checkpointListForProps,
            onBack = { currentScreen = "routes" },
            onPropertiesChanged = { updatedProperties ->
                // Обновляем свойства в памяти
            }
        )

        // Дополнительный экран: Список охранников
        "employee_list" -> EmployeeListScreen(
            employees = employeeList,
            onAddEmployee = { name, position ->
                // Создаем и добавляем нового сотрудника прямо в список
                val newEmployee = Employee(name = name, role = position)
                employeeList.add(newEmployee)
                // Сохраняем обновленный список в SharedPreferences
                prefsManager.saveEmployees(employeeList.toList())
            },
            onDeleteEmployee = { employee ->
                // Удаляем сотрудника из списка и обновляем хранилище
                employeeList.remove(employee)
                prefsManager.saveEmployees(employeeList.toList())
            },
            onEditEmployee = { employee, newName, newPosition ->
                // Находим редактируемого сотрудника и обновляем его поля
                val index = employeeList.indexOf(employee)
                if (index != -1) {
                    employeeList[index] = employee.copy(name = newName, role = newPosition)
                    prefsManager.saveEmployees(employeeList.toList())
                }
            },
            onBack = { currentScreen = "admin" }
        )

        // Дополнительный экран: Список отчетов
        "spisok_otchetov" -> SpisokOtchetovScreen(
            onBack = {
                currentScreen = "privet"
            },
            onBackToAdmin = {
                currentScreen = "admin"
            },
            previousScreenWasAdmin = previousScreenWasAdmin
        )



    }


}
