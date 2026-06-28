package com.example.ohrana

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 1. Все возможные исходы сканирования (добавлены новые типы)
sealed class QrResult {
    data class CheckpointPassed(val checkpointId: String, val name: String, val timestamp: String) : QrResult()
    data class SequenceError(val expectedCheckpointId: String?, val message: String) : QrResult()
    data class QuestionFormat(val text: String, val answers: List<String>) : QrResult()
    data class InputFormat(val title: String) : QrResult()
    data class PhotoFormat(val title: String) : QrResult()
    object ShiftReportTrigger : QrResult()
    data class Error(val message: String) : QrResult()
}

data class CheckpointEntry(
    val type: String,
    val titleOrLocation: String,
    val userResult: String,
    val timestamp: String
)

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
  object QrHandler {
      // Единый формат даты для всего класса
      private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US)

      // Список логов завершенных или текущих смен
      private val shiftLogs = mutableListOf<CheckpointEntry>()

      // Активные маршруты обхода. Ключ - уникальное имя смены/маршрута.
// Модификатор 'private' скрывает карту от внешнего доступа,
// а внутренние функции QrHandler могут работать с ней через публичные методы.
      private val activeRounds = mutableMapOf<String, PatrolRoute>()

      // Ключ для маршрута по умолчанию (единственного в проекте)
      private const val DEFAULT_ROUND_KEY = "Маршрут_Смены_1"

      /**
       * Эталонная последовательность чекпоинтов для обхода.
       * В идеале, эти данные должны загружаться из настроек администратора (Marshruti.kt).
       */
      private val defaultCheckpointIds: List<String> by lazy {
          listOf("CP-001", "CP-002", "CP-003")
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

      /**
       * Основная функция парсинга QR-кода.
       * Здесь реализована логика автоматического старта обхода при первом сканировании.
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

              // --- ОБЪЕДИНЕННАЯ ЛОГИКА ---
              when {
                  // БЛОК 1: КОД СОДЕРЖИТ И ЧЕКПОИНТ, И ВОПРОС
                  json.has("type") && json.getString("type") == "checkpoint" &&
                          json.has("action") && json.getString("action") == "question" -> {

                      val checkpointId = json.optString("id", "")
                      val name = json.getString("name")
                      val currentTime = dateFormat.format(Date())

                      // Сохраняем статус контроля последовательности при первом сканировании
                      prefsManager.saveSequenceControlStatus(prefsManager.isStrictSequenceEnabled())

                      // НЕСТРОГИЙ РЕЖИМ: просто сохраняем факт прохода, без проверки последовательности
                      if (!prefsManager.isStrictSequenceEnabled()) {
                          saveCheckpointToLog(DEFAULT_ROUND_KEY, checkpointId, name, currentTime, prefsManager)
                          // Извлекаем данные вопроса и возвращаем результат
                          val questionText = json.getString("text")
                          val answersArray = json.getJSONArray("answers")
                          val answersList = List(answersArray.length()) { answersArray.getString(it) }
                          return QrResult.QuestionFormat(questionText, answersList)
                      }

                      // СТРОГИЙ РЕЖИМ: проверяем последовательность
                      val activeRoute = activeRounds[DEFAULT_ROUND_KEY]
                      
                      // Если маршрута нет - создаем и сразу проверяем первую точку
                      val (isValid, expectedId) = if (activeRoute == null) {
                          startNewRound(DEFAULT_ROUND_KEY, defaultCheckpointIds)
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

                      // Извлекаем данные вопроса и возвращаем результат
                      val questionText = json.getString("text")
                      val answersArray = json.getJSONArray("answers")
                      val answersList = List(answersArray.length()) { answersArray.getString(it) }

                      QrResult.QuestionFormat(questionText, answersList)
                  }

                  // БЛОК 1.5: КОД СОДЕРЖИТ И ЧЕКПОИНТ, И ВВОД ДАННЫХ
                  json.has("type") && json.getString("type") == "checkpoint" &&
                          json.has("action") && json.getString("action") == "input" -> {

                      val checkpointId = json.optString("id", "")
                      val name = json.getString("name")
                      val currentTime = dateFormat.format(Date())
                      val title = json.optString("text", "Показания")

                      // Сохраняем статус контроля последовательности при первом сканировании
                      prefsManager.saveSequenceControlStatus(prefsManager.isStrictSequenceEnabled())

                      // НЕСТРОГИЙ РЕЖИМ: просто сохраняем факт прохода, без проверки последовательности
                      if (!prefsManager.isStrictSequenceEnabled()) {
                          saveCheckpointToLog(DEFAULT_ROUND_KEY, checkpointId, name, currentTime, prefsManager)
                          return QrResult.InputFormat(title)
                      }

                      // СТРОГИЙ РЕЖИМ: проверяем последовательность
                      val activeRoute = activeRounds[DEFAULT_ROUND_KEY]
                      
                      // Если маршрута нет - создаем и сразу проверяем первую точку
                      val (isValid, expectedId) = if (activeRoute == null) {
                          startNewRound(DEFAULT_ROUND_KEY, defaultCheckpointIds)
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

                      return QrResult.InputFormat(title)
                  }

                  // БЛОК 2: ОБЫЧНЫЙ ЧЕКПОИНТ (без доп. действий)
                  json.has("type") && json.getString("type") == "checkpoint" -> {
                      val checkpointId = json.optString("id", "")
                      val name = json.getString("name")
                      val currentTime = dateFormat.format(Date())

                      // Сохраняем статус контроля последовательности при первом сканировании
                      prefsManager.saveSequenceControlStatus(prefsManager.isStrictSequenceEnabled())

                      // НЕСТРОГИЙ РЕЖИМ: просто сохраняем факт прохода, без проверки последовательности
                      if (!prefsManager.isStrictSequenceEnabled()) {
                          saveCheckpointToLog(DEFAULT_ROUND_KEY, checkpointId, name, currentTime, prefsManager)
                          return QrResult.CheckpointPassed(checkpointId, name, currentTime)
                      }

                      // СТРОГИЙ РЕЖИМ: проверяем последовательность
                      val activeRoute = activeRounds[DEFAULT_ROUND_KEY]
                      
                      // Если маршрута нет - создаем и сразу проверяем первую точку
                      val (isValid, expectedId) = if (activeRoute == null) {
                          startNewRound(DEFAULT_ROUND_KEY, defaultCheckpointIds)
                          activeRounds[DEFAULT_ROUND_KEY]!!.validateAndAdvance(checkpointId)
                      } else {
                          activeRoute.validateAndAdvance(checkpointId)
                      }
                      
                      if (isValid) {
                          saveCheckpointToLog(
                              DEFAULT_ROUND_KEY,
                              checkpointId,
                              name,
                              currentTime,
                              prefsManager
                          )
                          
                          // Если маршрут завершен, сбрасываем его
                          if (expectedId == null) {
                              endRoundIfActive()
                          }
                          
                          QrResult.CheckpointPassed(checkpointId, name, currentTime)
                      } else {
                          QrResult.SequenceError(
                              expectedId ?: "",
                              "Нарушена последовательность обхода."
                          )
                      }
                  }

                  // БЛОК 3: ПРОСТОЙ ВОПРОС
                  json.has("action") && json.getString("action") == "question" -> {
                      val text = json.getString("text")
                      val answersList = List(json.getJSONArray("answers").length()) { i ->
                          json.getJSONArray("answers").getString(i)
                      }
                      return QrResult.QuestionFormat(text, answersList)
                  }

                  // БЛОК 4: ВВОД ДАННЫХ (если type: "input" без type: "checkpoint")
                  json.has("type") && json.getString("type") == "input" -> {
                      val title = json.optString("title", "Показания")
                      return QrResult.InputFormat(title)
                  }

                  // БЛОК 5: ФОТОПРИЕМНИК (если action: "photo")
                  json.has("action") && json.getString("action") == "photo" -> {
                      val checkpointId = json.optString("id", "")
                      val name = json.getString("name")
                      val currentTime = dateFormat.format(Date())

                      // Сохраняем статус контроля последовательности при первом сканировании
                      prefsManager.saveSequenceControlStatus(prefsManager.isStrictSequenceEnabled())

                      // НЕСТРОГИЙ РЕЖИМ: просто сохраняем факт прохода, без проверки последовательности
                      if (!prefsManager.isStrictSequenceEnabled()) {
                          saveCheckpointToLog(DEFAULT_ROUND_KEY, checkpointId, name, currentTime, prefsManager)
                          return QrResult.PhotoFormat(name)
                      }

                      // СТРОГИЙ РЕЖИМ: проверяем последовательность
                      val activeRoute = activeRounds[DEFAULT_ROUND_KEY]
                      
                      // Если маршрута нет - создаем и сразу проверяем первую точку
                      val (isValid, expectedId) = if (activeRoute == null) {
                          startNewRound(DEFAULT_ROUND_KEY, defaultCheckpointIds)
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

                      return QrResult.PhotoFormat(name)
                  }

                  // ФИНАЛЬНЫЙ ELSE для любых других случаев
                  else -> {
                      return QrResult.Error("Игнорируется: неизвестный тип данных")
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
  }


