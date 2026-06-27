package com.example.ohrana

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Модель для сохранения сканирований
data class QrScanRecord(val employeeName: String, val time: String, val qrContent: String)

class SharedPrefsManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("ohrana_prefs", Context.MODE_PRIVATE)

    // ОБНОВЛЕНО: Новый, понятный формат даты для логов и смен
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US)
    // --- МЕТОДЫ УПРАВЛЕНИЯ СМЕНОЙ ОХРАННИКА ---
// Включен ли строгий контроль последовательности сканирования (true/false)
    fun isStrictSequenceEnabled(): Boolean {
        return prefs.getBoolean("strict_sequence_enabled", false)
    }

    fun setStrictSequenceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("strict_sequence_enabled", enabled).apply()
    }

    // Сохранить текущий рабочий маршрут (список названий точек через запятую)
    fun saveCurrentRouteCheckpoints(points: List<String>) {
        prefs.edit().putString("active_route_points", points.joinToString(",")).apply()
    }

    // Получить список точек текущего активного маршрута
    fun getActiveRouteCheckpoints(): List<String> {
        val saved = prefs.getString("active_route_points", "") ?: ""
        if (saved.isEmpty()) {
            // Дефолтный тестовый маршрут, если админ ничего не настроил в "routes"
            return listOf("Точка 1", "Точка 2", "Точка 3")
        }
        return saved.split(",")
    }

    // Получить индекс чекпоинта, который мы ждем от сканера (начиная с 0)
    fun getCurrentCheckpointIndex(): Int {
        return prefs.getInt("active_route_current_index", 0)
    }

    fun updateCurrentCheckpointIndex(index: Int) {
        prefs.edit().putInt("active_route_current_index", index).apply()
    }

    fun resetRouteProgress() {
        prefs.edit().putInt("active_route_current_index", 0).apply()
    }

    // Начать новую смену (сохраняем имя и время старта)
    fun startNewShift(employeeName: String) {
        val currentTime = dateFormat.format(Date()) // Генерирует строку вида "27.06.2026 15:30:00"
        prefs.edit().apply {
            putString("active_shift_employee", employeeName)
            putString("active_shift_start_time", currentTime)
            putBoolean("active_shift_is_running", true)
            apply()
        }
    }
    // Просто возвращает true, если смена запущена на устройстве, и false, если закрыта
    fun isShiftActive(): Boolean {
        return prefs.getBoolean("active_shift_is_running", false)
    }


    // Проверить, идет ли сейчас активная смена у конкретного сотрудника
    fun isShiftActiveFor(employeeName: String): Boolean {
        val savedName = prefs.getString("active_shift_employee", "") ?: ""
        val isRunning = prefs.getBoolean("active_shift_is_running", false)
        return isRunning && savedName == employeeName
    }

    // Получить время начала текущей смены
    fun getShiftStartTime(): String = prefs.getString("active_shift_start_time", "-") ?: "-"





    // Дополнительный метод (пригодится для вывода на экран отчетов)
    fun getShiftEndTime(): String = prefs.getString("active_shift_end_time", "-") ?: "-"

    // Получить имя сотрудника, у которого сейчас открыта смена (чтобы сделать автопереход)
    fun getActiveShiftEmployeeName(): String {
        val isRunning = prefs.getBoolean("active_shift_is_running", false)
        return if (isRunning) {
            prefs.getString("active_shift_employee", "") ?: ""
        } else {
            ""
        }
    }
    // Закрыть смену (с фиксацией времени закрытия)
    fun closeCurrentShift() {
        val endTime = dateFormat.format(Date()) // Генерирует время закрытия, например "27.06.2026 10:15:00"

        prefs.edit().apply {
            putBoolean("active_shift_is_running", false)
            putString("active_shift_end_time", endTime) // ФИКСАЦИЯ: Сохраняем время закрытия

            // Удаляем данные сотрудника, так как смена завершена
            remove("active_shift_employee")
            remove("active_shift_start_time")
            apply()
        }
    }


// --- УПРАВЛЕНИЕ АКТИВНЫМ ОБХОДОМ ---

    // Запустить конкретный обход по его номеру (индексу)
    fun startRound(roundIndex: Int, startTime: String) {
        prefs.edit().apply {
            putInt("active_shift_current_round_index", roundIndex)
            putString("round_${roundIndex}_start_time", startTime)
            putString("round_${roundIndex}_scanned_points", "") // Очищаем отсканированные точки для этого обхода
            apply()
        }
    }

    // Получить индекс обхода, который выполняется прямо сейчас (-1 означает, что никакой обход не запущен)
    fun getActiveRoundIndex(): Int = prefs.getInt("active_shift_current_round_index", -1)

    // Получить время начала конкретного обхода
    fun getRoundStartTime(roundIndex: Int): String = prefs.getString("round_${roundIndex}_start_time", "-") ?: "-"


    // (Ваши старые методы loadEmployees и saveEmployees остаются здесь без изменений)
    fun loadEmployees(): List<Employee> {
        // Здесь ваша оригинальная логика загрузки сотрудников
        return emptyList()
    }
    // --- СОХРАНЕНИЕ НАСТРОЕК МАРШРУТА И РАСПИСАНИЯ ---
    fun saveRouteSettings(roundsCount: Int, times: List<String>, tolerance: String, checkpoints: List<String>) {
        prefs.edit().apply {
            putInt("route_rounds_count", roundsCount)
            putString("route_times", times.joinToString(","))
            putString("route_tolerance", tolerance)
            putString("route_checkpoints", checkpoints.joinToString(","))
            apply()
        }
    }

    fun loadRouteRoundsCount(): Int = prefs.getInt("route_rounds_count", 3)

    fun loadRouteTimes(): List<String> {
        val raw = prefs.getString("route_times", "") ?: ""
        if (raw.isEmpty()) return listOf("08:00", "14:00", "20:00") // Базовые значения
        return raw.split(",")
    }

    fun loadRouteTolerance(): String = prefs.getString("route_tolerance", "15") ?: "15"

    fun loadRouteCheckpoints(): List<String> {
        val raw = prefs.getString("route_checkpoints", "") ?: ""
        if (raw.isEmpty()) return listOf("Точка_Вход", "Точка_Склад_1", "Точка_Забор") // Базовые значения
        return raw.split(",")
    }


    fun saveEmployees(list: List<Employee>) {
        // Здесь ваша оригинальная логика сохранения сотрудников
    }

    // Сохранение логов сканирования в SharedPrefs (в виде простой строки для упрощения)
    fun saveScanResult(employeeName: String, qrContent: String) {
        val currentTime = dateFormat.format(Date())
        val currentLogs = prefs.getString("qr_logs", "") ?: ""

        // Очищаем контент от точек с запятой и переносов, чтобы не ломать структуру CSV таблицы
        val safeContent = qrContent.replace(";", " ").replace("\n", " ")
        val logLine = "$employeeName;$currentTime;$safeContent"

        val newLogs = if (currentLogs.isEmpty()) {
            logLine
        } else {
            "$currentLogs\n$logLine"
        }
        prefs.edit().putString("qr_logs", newLogs).apply()
    }

    // Получение всех логов
    fun getScanLogs(): List<QrScanRecord> {
        val logsString = prefs.getString("qr_logs", "") ?: ""
        if (logsString.isEmpty()) return emptyList()

        return logsString.split("\n").mapNotNull { line ->
            val parts = line.split(";")
            if (parts.size == 3) QrScanRecord(parts[0], parts[1], parts[2]) else null
        }
    }

    // Очистка логов после генерации отчета
    fun clearScanLogs() {
        prefs.edit().remove("qr_logs").apply()
    }

    // Генерация CSV отчета для Excel
    fun generateExcelReport(employeeName: String): File? {
        val logs = getScanLogs()

        // 🗓️ Понятный формат: день.месяц.yy_HH-mm (например, 25.06.26_14-30)
        val fileTimestamp = SimpleDateFormat("dd.MM.yy_HH-mm", Locale.US).format(Date())

        // Шаблон имени файла: Фамилия_Имя_ДАТА_ВРЕМЯ.csv
        val fileName = "${employeeName.replace(" ", "_")}_$fileTimestamp.csv"

        val reportsDir = File(context.filesDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()

        val file = File(reportsDir, fileName)

        try {
            // Открываем поток записи строго в кодировке UTF-8
            file.bufferedWriter(charset = Charsets.UTF_8).use { writer ->

                // Записываем правильный UTF-8 BOM маркер, чтобы Excel сразу понял русский язык
                writer.write("\uFEFF")

                // Заголовки столбцов
                writer.write("Сотрудник;Дата и Время;Данные QR-кода\n")

                // Запись накопленных данных
                for (log in logs) {
                    writer.write("${log.employeeName};${log.time};${log.qrContent}\n")
                }
            }
            // После успешной записи отчета очищаем текущие логи
            clearScanLogs()
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    // Получить список всех файлов отчетов
    fun getReportsList(): List<File> {
        val reportsDir = File(context.filesDir, "reports")
        return reportsDir.listFiles()?.filter { it.isFile && it.extension == "csv" }?.toList() ?: emptyList()
    }

    // ==================================================
    // ⏰ МЕТОДЫ ДЛЯ РАБОТЫ С БУДИЛЬНИКАМИ (RouteAlarm)
    // ==================================================

    /**
     * Сохраняет список настроенных будильников в память устройства
     */
    fun saveRouteAlarms(alarms: List<RouteAlarm>) {
        val localPrefs = context.getSharedPreferences("OhranaPrefs", Context.MODE_PRIVATE)
        val stringSet = alarms.map { "${it.id}|${it.time}|${it.isEnabled}" }.toSet()
        localPrefs.edit().putStringSet("route_alarms_key", stringSet).apply()
    }

    /**
     * Загружает сохраненный список будильников. Если данных нет — вернет пустой список
     */
    fun loadRouteAlarms(): List<RouteAlarm> {
        val localPrefs = context.getSharedPreferences("OhranaPrefs", Context.MODE_PRIVATE)
        val stringSet = localPrefs.getStringSet("route_alarms_key", null) ?: return emptyList()
        return stringSet.mapNotNull { savedString ->
            val parts = savedString.split("|")
            if (parts.size == 3) {
                val id = parts[0].toIntOrNull() ?: return@mapNotNull null
                val time = parts[1]
                val isEnabled = parts[2].toBooleanStrictOrNull() ?: true
                RouteAlarm(id = id, time = time, isEnabled = isEnabled)
            } else {
                null
            }
        }.sortedBy { it.id }
    }
}

