package com.example.ohrana

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 1. Все возможные исходы сканирования (добавлены новые типы)
sealed class QrResult {
    data class CheckpointPassed(val checkpointId: String, val name: String, val timestamp: String) : QrResult()
    data class SequenceError(val expectedCheckpointId: String?, val message: String) : QrResult()
    data class QuestionFormat(val checkpointId: String, val checkpointName: String, val text: String, val answers: List<String>) : QrResult()
    data class InputFormat(val checkpointId: String, val checkpointName: String, val title: String) : QrResult()
    data class PhotoFormat(val checkpointId: String, val checkpointName: String, val imageUri: String? = null) : QrResult()
    object ShiftReportTrigger : QrResult()
    data class Error(val message: String) : QrResult()
}

// Extension properties для обратной совместимости
val QrResult.QuestionFormat.questionText: String get() = text
val QrResult.QuestionFormat.answersList: List<String> get() = answers
val QrResult.InputFormat.titleText: String get() = title
val QrResult.PhotoFormat.photoCheckpointId: String get() = checkpointId
val QrResult.PhotoFormat.photoCheckpointName: String get() = checkpointName

// Extension property для NFC-чекпоинтов
val QrResult.CheckpointPassed.nfcCheckpointId: String get() = checkpointId


/**
 * Внутренний класс, представляющий маршрут обхода.
 */
private class PatrolRoute(val routeName: String, private val checkpoints: List<String>) {
    var currentIndex = 0
        private set

    /**
     * Проверяет ID чекпоинта и, если он верный, продвигает обход к следующей точке.
     * Возвращает Pair(валидность, следующий ожидаемый ID или null если маршрут завершен)
     */
    fun validateAndAdvance(checkpointId: String): Pair<Boolean, String?> {
        // Проверяем, совпадает ли отсканированный ID с ожидаемым
        if (currentIndex < checkpoints.size && checkpoints[currentIndex] == checkpointId) {
            currentIndex++

            // Если достигли конца маршрута, возвращаем success с null (маршрут завершен)
            if (currentIndex >= checkpoints.size) {
                return Pair(true, null)
            }

            // Возвращаем следующий ожидаемый ID
            val nextExpectedId = checkpoints[currentIndex]
            return Pair(true, nextExpectedId)

        } else {
            // В случае ошибки возвращаем тот ID, который ожидался
            val expectedId =
                if (currentIndex < checkpoints.size) checkpoints[currentIndex] else null
            return Pair(false, expectedId)
        }
    }
}

// Модель для записи в лог
data class CheckpointEntry(
    val type: String,
    val titleOrLocation: String,
    val userResult: String,
    val timestamp: String
)

object QrHandler {
    // Единый формат даты для всего класса
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US)

    // Список логов завершенных или текущих смен
    private val shiftLogs = mutableListOf<CheckpointEntry>()

    /**
     * Очищает shiftLogs - вызывается при открытии/закрытии смены
     */
    fun clearShiftLogs() {
        shiftLogs.clear()
    }

    // Активные маршруты обхода. Ключ - уникальное имя смены/маршрута.
// Модификатор 'private' скрывает карту от внешнего доступа,
// а внутренние функции QrHandler могут работать с ней через публичные методы.
    private val activeRounds = mutableMapOf<String, PatrolRoute>()

    // Ключ для маршрута по умолчанию (единственного в проекте)
    private const val DEFAULT_ROUND_KEY = "Маршрут_Смены_1"

    /**
     * Основная функция парсинга QR-кода.
     * Логика изменена: сначала берется только ID из QR-кода,
     * затем вся информация загружается из базы данных чекпоинтов.
     * QR-коды без ID в базе данных игнорируются.
     */
    fun parseQrCode(rawText: String, prefsManager: SharedPrefsManager): QrResult {

        val trimmed = rawText.trim()

        if (trimmed.equals("отчет о смене", ignoreCase = true)) {
            return QrResult.ShiftReportTrigger
        }

        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return QrResult.Error("Игнорируется: это не рабочий QR-код системы")
        }

        return try {
            val json = JSONObject(trimmed)

            // --- НОВАЯ ЛОГИКА ---
            // 1. Сначала пробуем получить только ID из QR-кода
            val checkpointId = json.optString("id", "").trim()
            
            if (checkpointId.isEmpty()) {
                return QrResult.Error("Неизвестный QR-код")
            }
            
            // 2. Ищем чекпоинт в базе данных по этому ID
            val checkpointFromDatabase = prefsManager.getCheckpointById(checkpointId)
            
            if (checkpointFromDatabase == null) {
                // Чекпоинт не найден в базе данных
                return QrResult.Error("Неизвестный QR-код")
            }
            
            // 3. Если чекпоинт найден, берем все данные из базы данных
            val name = checkpointFromDatabase.name
            val currentTime = dateFormat.format(Date())
            val action = checkpointFromDatabase.action
            
            // Сохраняем статус контроля последовательности при первом сканировании
            prefsManager.saveSequenceControlStatus(prefsManager.isStrictSequenceEnabled())
            
            // НЕСТРОГИЙ РЕЖИМ: просто сохраняем факт прохода, без проверки последовательности
            if (!prefsManager.isStrictSequenceEnabled()) {
                saveCheckpointToLog(DEFAULT_ROUND_KEY, checkpointId, name, currentTime, prefsManager)
                
                // Действия на основе типа чекпоинта
                return when (action) {
                    CheckpointAction.QUESTION -> {
                        val questionText = checkpointFromDatabase.questionText ?: ""
                        val answersList = checkpointFromDatabase.answers.ifEmpty { emptyList() }
                        QrResult.QuestionFormat(checkpointId, name, questionText, answersList)
                    }
                    CheckpointAction.INPUT -> {
                        val title = checkpointFromDatabase.inputTitle ?: "Показания"
                        QrResult.InputFormat(checkpointId, name, title)
                    }
                    CheckpointAction.PHOTO -> {
                        val imageUri = checkpointFromDatabase.imageUri
                        QrResult.PhotoFormat(checkpointId, name, imageUri)
                    }
                    else -> {
                        QrResult.CheckpointPassed(checkpointId, name, currentTime)
                    }
                }
            }
            
            // СТРОГИЙ РЕЖИМ: проверяем последовательность
            val activeRoute = activeRounds[DEFAULT_ROUND_KEY]
            
            // Если маршрута нет - создаем и сразу проверяем первую точку
            val (isValid, expectedId) = if (activeRoute == null) {
                // Загружаем список чекпоинтов из настроек маршрута
                val routeCheckpoints = prefsManager.loadRouteCheckpoints()
                startNewRound(DEFAULT_ROUND_KEY, routeCheckpoints)
                activeRounds[DEFAULT_ROUND_KEY]!!.validateAndAdvance(checkpointId)
            } else {
                activeRoute.validateAndAdvance(checkpointId)
            }
            
            if (!isValid) {
                return QrResult.SequenceError(
                    expectedId ?: "",
                    "Нарушена последовательность обхода."
                )
            }

            // Если проверка пройдена, сохраняем факт
            saveCheckpointToLog(DEFAULT_ROUND_KEY, checkpointId, name, currentTime, prefsManager)

            // Если маршрут завершен, сбрасываем его
            if (expectedId == null) {
                endRoundIfActive()
            }

            // Действия на основе типа чекпоинта
            when (action) {
                CheckpointAction.QUESTION -> {
                    val questionText = checkpointFromDatabase.questionText ?: ""
                    val answersList = checkpointFromDatabase.answers.ifEmpty { emptyList() }
                    QrResult.QuestionFormat(checkpointId, name, questionText, answersList)
                }
                CheckpointAction.INPUT -> {
                    val title = checkpointFromDatabase.inputTitle ?: "Показания"
                    QrResult.InputFormat(checkpointId, name, title)
                }
                CheckpointAction.PHOTO -> {
                    val imageUri = checkpointFromDatabase.imageUri
                    QrResult.PhotoFormat(checkpointId, name, imageUri)
                }
                else -> {
                    QrResult.CheckpointPassed(checkpointId, name, currentTime)
                }
            }
            
        } catch (e: Exception) {
            return QrResult.Error("Игнорируется: невалидный JSON-формат")
        }
    }

    fun startNewRound(roundName: String, checkpointIds: List<String>) {
        activeRounds[roundName] = PatrolRoute(roundName, checkpointIds)
    }

    private fun saveCheckpointToLog(
        routeName: String,
        id: String,
        name: String,
        timestamp: String,
        prefsManager: SharedPrefsManager
    ) {
        shiftLogs.add(
            CheckpointEntry(
                type = "Маршрут: $routeName",
                titleOrLocation = "$name (ID: $id)",
                userResult = "Отметка пройдена",
                timestamp = timestamp
            )
        )
        
        // Сохраняем лог в SharedPreferences для Excel-отчета
        val logText = "Чекпоинт: $name (ID: $id)"
        prefsManager.saveScanResult("Маршрут", logText)
    }
    
    /**
     * Возвращает ключ активного обхода, если он существует.
     */
    fun getActiveRouteKey(): String? {
        return if (activeRounds.containsKey(DEFAULT_ROUND_KEY)) {
            DEFAULT_ROUND_KEY
        } else {
            null
        }
    }

    /**
     * Добавляет запись в shiftLogs
     */
    fun addCheckpointToLog(
        type: String,
        titleOrLocation: String,
        userResult: String,
        timestamp: String
    ) {
        shiftLogs.add(
            CheckpointEntry(
                type = type,
                titleOrLocation = titleOrLocation,
                userResult = userResult,
                timestamp = timestamp
            )
        )
    }
    
    /**
     * Завершает активный обход, если он был начат.
     * Эту функцию можно вызывать безопасно в любой момент.
     */
    fun endRoundIfActive() {
        val activeKey = getActiveRouteKey()

        if (activeKey != null) {
            activeRounds.remove(activeKey)?.let { completedRound ->
                println("Обход '${completedRound.routeName}' завершен.")
            }
        } else {
            println("Завершение обхода: обход не был активен.")
        }
    }

    fun generateFullReport(): String {
        if (shiftLogs.isEmpty()) {
            return "Отчет пуст. За смену не было зафиксировано ни одного сканирования."
        }
        val builder = StringBuilder()
        builder.append("=== ОТЧЕТ О СМЕНЕ ===\n")
        builder.append("Сгенерирован: ${dateFormat.format(Date())}\n")
        builder.append("----------------------------------------\n")
        shiftLogs.forEachIndexed { index, entry ->
            builder.append("${index + 1}. [${entry.timestamp}]\n")
            builder.append(" Тип: ${entry.type}\n")
            builder.append(" Объект: ${entry.titleOrLocation}\n")
            builder.append(" Результат: ${entry.userResult}\n")
            builder.append("----------------------------------------\n")
        }
        return builder.toString()
    }
    
    /**
     * Парсит NFC-данные (rawText - это NFC-ID тега)
     * Ищет чекпоинт по NFC-ID в базе данных
     */
    fun parseNfcData(nfcId: String, prefsManager: SharedPrefsManager): QrResult {
        val trimmed = nfcId.trim()
        
        if (trimmed.isEmpty()) {
            return QrResult.Error("Пустой NFC-ID")
        }
        
        // Ищем чекпоинт по NFC-ID
        val checkpoints = prefsManager.loadCheckpoints()
        val checkpointFromDatabase = checkpoints.find { it.nfcId == trimmed }
        
        if (checkpointFromDatabase == null) {
            return QrResult.Error("Неизвестный NFC-тег")
        }
        
        val checkpointId = checkpointFromDatabase.id
        val name = checkpointFromDatabase.name
        val currentTime = dateFormat.format(Date())
        val action = checkpointFromDatabase.action
        
        // Сохраняем статус контроля последовательности при первом сканировании
        prefsManager.saveSequenceControlStatus(prefsManager.isStrictSequenceEnabled())
        
        // НЕСТРОГИЙ РЕЖИМ: просто сохраняем факт прохода, без проверки последовательности
        if (!prefsManager.isStrictSequenceEnabled()) {
            saveCheckpointToLog(DEFAULT_ROUND_KEY, checkpointId, name, currentTime, prefsManager)
            
            // Действия на основе типа чекпоинта
            return when (action) {
                CheckpointAction.QUESTION -> {
                    val questionText = checkpointFromDatabase.questionText ?: ""
                    val answersList = checkpointFromDatabase.answers.ifEmpty { emptyList() }
                    QrResult.QuestionFormat(checkpointId, name, questionText, answersList)
                }
                CheckpointAction.INPUT -> {
                    val title = checkpointFromDatabase.inputTitle ?: "Показания"
                    QrResult.InputFormat(checkpointId, name, title)
                }
                CheckpointAction.PHOTO -> {
                    val imageUri = checkpointFromDatabase.imageUri
                    QrResult.PhotoFormat(checkpointId, name, imageUri)
                }
                else -> {
                    QrResult.CheckpointPassed(checkpointId, name, currentTime)
                }
            }
        }
        
        // СТРОГИЙ РЕЖИМ: проверяем последовательность
        val activeRoute = activeRounds[DEFAULT_ROUND_KEY]
        
        // Если маршрута нет - создаем и сразу проверяем первую точку
        val (isValid, expectedId) = if (activeRoute == null) {
            // Загружаем список чекпоинтов из настроек маршрута
            val routeCheckpoints = prefsManager.loadRouteCheckpoints()
            startNewRound(DEFAULT_ROUND_KEY, routeCheckpoints)
            activeRounds[DEFAULT_ROUND_KEY]!!.validateAndAdvance(checkpointId)
        } else {
            activeRoute.validateAndAdvance(checkpointId)
        }
        
        if (!isValid) {
            return QrResult.SequenceError(
                expectedId ?: "",
                "Нарушена последовательность обхода."
            )
        }

        // Если проверка пройдена, сохраняем факт
        saveCheckpointToLog(DEFAULT_ROUND_KEY, checkpointId, name, currentTime, prefsManager)

        // Если маршрут завершен, сбрасываем его
        if (expectedId == null) {
            endRoundIfActive()
        }

        // Действия на основе типа чекпоинта
        when (action) {
            CheckpointAction.QUESTION -> {
                val questionText = checkpointFromDatabase.questionText ?: ""
                val answersList = checkpointFromDatabase.answers.ifEmpty { emptyList() }
                QrResult.QuestionFormat(checkpointId, name, questionText, answersList)
            }
            CheckpointAction.INPUT -> {
                val title = checkpointFromDatabase.inputTitle ?: "Показания"
                QrResult.InputFormat(checkpointId, name, title)
            }
            CheckpointAction.PHOTO -> {
                val imageUri = checkpointFromDatabase.imageUri
                QrResult.PhotoFormat(checkpointId, name, imageUri)
            }
            else -> {
                QrResult.CheckpointPassed(checkpointId, name, currentTime)
            }
        }
        
        return QrResult.Error("Неизвестная ошибка")
    }
}
