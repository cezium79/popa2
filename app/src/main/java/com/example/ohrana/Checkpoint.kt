package com.example.ohrana

// Типы действий чекпоинта
enum class CheckpointAction {
    CHECKPOINT,  // Простой чекпоинт
    QUESTION,    // Чекпоинт с вопросом
    INPUT,       // Чекпоинт с вводом данных
    PHOTO        // Чекпоинт с фото
}

// Модель чекпоинта - хранится в SharedPreferences
data class Checkpoint(
    val id: String,
    val name: String,
    val action: CheckpointAction,
    val questionText: String? = null,
    val answers: List<String> = emptyList(),
    val inputTitle: String? = null,
    val imageUri: String? = null,
    val nfcId: String? = null  // ID NFC-тега (для чекпоинтов без QR-кода)
) {
    // Метод для JSON-сериализации
    fun toJson(): String {
        val answersJson = answers.joinToString(separator = ",") { "\"$it\"" }
        return """
        {
            "id": "$id",
            "name": "$name",
            "type": "checkpoint",
            "action": "${action.name.lowercase()}",
            ${if (questionText != null) """"text": "$questionText",""" else ""}
            ${if (answers.isNotEmpty()) """"answers": [$answersJson],""" else ""}
            ${if (inputTitle != null) """"title": "$inputTitle",""" else ""}
            ${if (imageUri != null) """"image": "$imageUri",""" else ""}
            ${if (nfcId != null) """"nfcId": "$nfcId",""" else ""}
            "timestamp": "${System.currentTimeMillis()}"
        }
        """.trimIndent()
    }
    
    companion object {
        // Метод для JSON-десериализации
        fun fromJson(json: String): Checkpoint? {
            return try {
                val JSONObject = org.json.JSONObject(json)
                val id = JSONObject.optString("id", "")
                val name = JSONObject.optString("name", "")
                val actionStr = JSONObject.optString("action", "checkpoint")
                val action = CheckpointAction.valueOf(actionStr.uppercase())
                
                val questionText = JSONObject.optString("text", null)
                val inputTitle = JSONObject.optString("title", null)
                val imageUri = JSONObject.optString("image", null)
                val nfcId = JSONObject.optString("nfcId", null)
                
                val answersList = if (JSONObject.has("answers")) {
                    val answersArray = JSONObject.getJSONArray("answers")
                    List(answersArray.length()) { answersArray.getString(it) }
                } else {
                    emptyList()
                }
                
                Checkpoint(
                    id = id,
                    name = name,
                    action = action,
                    questionText = questionText,
                    answers = answersList,
                    inputTitle = inputTitle,
                    imageUri = imageUri,
                    nfcId = nfcId
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
