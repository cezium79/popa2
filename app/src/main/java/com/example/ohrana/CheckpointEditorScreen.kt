package com.example.ohrana

import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckpointEditorScreen(
    checkpointId: String? = null,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    
    val prefsManager = remember { SharedPrefsManager(context) }
    
    val existingCheckpoint = checkpointId?.let { if (it.isEmpty()) null else prefsManager.getCheckpointById(it) }
    val isEditing = existingCheckpoint != null
    
    fun extractIdNumber(fullId: String): String {
        return if (fullId.startsWith("CP-")) {
            fullId.substring(3)
        } else {
            fullId
        }
    }
    
    var idNumberField by remember { mutableStateOf(existingCheckpoint?.let { extractIdNumber(it.id) } ?: "") }
    var idField by remember { mutableStateOf(existingCheckpoint?.id ?: "") }
    var nameField by remember { mutableStateOf(existingCheckpoint?.name ?: "") }
    
    var showSaveDialog by remember { mutableStateOf(false) }
    var lastSavedId by remember { mutableStateOf("") }
    var actionField by remember { mutableStateOf(existingCheckpoint?.action ?: CheckpointAction.CHECKPOINT) }
    var questionText by remember { mutableStateOf(existingCheckpoint?.questionText ?: "") }
    var inputTitle by remember { mutableStateOf(existingCheckpoint?.inputTitle ?: "") }
    var imageUri by remember { mutableStateOf(existingCheckpoint?.imageUri ?: "") }
    var nfcId by remember { mutableStateOf(existingCheckpoint?.nfcId ?: "") }
    var nfcScanningEnabled by remember { mutableStateOf(false) }
    var nfcScanResult by remember { mutableStateOf<String?>(null) }
    var answers by remember { mutableStateOf(existingCheckpoint?.answers ?: emptyList()) }
    
    val bitmapState = remember(imageUri) {
        androidx.compose.runtime.mutableStateOf<android.graphics.Bitmap?>(null)
    }
    
    LaunchedEffect(imageUri) {
        if (imageUri.isNotEmpty()) {
            val uri = android.net.Uri.parse(imageUri)
            bitmapState.value = runCatching {
                android.graphics.BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri))
            }.getOrNull()
        } else {
            bitmapState.value = null
        }
    }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                val uriString = uri.toString()
                android.widget.Toast.makeText(context, "Выбран URI: $uriString", android.widget.Toast.LENGTH_SHORT).show()
                imageUri = uriString
            } ?: run {
                android.widget.Toast.makeText(context, "URI не выбран", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    )
    
    fun saveCheckpoint() {
        if (idNumberField.isBlank()) {
            android.widget.Toast.makeText(context, "Номер ID не может быть пустым", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!idNumberField.matches(Regex("\\d+"))) {
            android.widget.Toast.makeText(context, "Номер ID должен содержать только цифры", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        val fullId = "CP-$idNumberField"
        
        if (nameField.isBlank()) {
            android.widget.Toast.makeText(context, "Имя не может быть пустым", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        val checkpoint = Checkpoint(
            id = fullId,
            name = nameField.trim(),
            action = actionField,
            questionText = if (actionField == CheckpointAction.QUESTION) questionText else null,
            answers = answers.filter { it.isNotBlank() }.toList(),
            inputTitle = if (actionField == CheckpointAction.INPUT) inputTitle else null,
            imageUri = if (imageUri.isNotEmpty()) imageUri else null,
            nfcId = if (nfcId.isNotEmpty()) nfcId else null
        )
        
        android.widget.Toast.makeText(context, "Сохранение: imageUri=${checkpoint.imageUri}", android.widget.Toast.LENGTH_LONG).show()
        
        prefsManager.addCheckpoint(checkpoint)
        lastSavedId = fullId
        showSaveDialog = true
    }
    
    // NFC scanning handler
    var nfcAdapter by remember { mutableStateOf<NfcAdapter?>(null) }
    
    LaunchedEffect(Unit) {
        try {
            nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        } catch (e: Exception) {
            // NFC не поддерживается
        }
    }
    
    LaunchedEffect(nfcScanningEnabled) {
        if (nfcScanningEnabled && nfcAdapter != null && activity != null) {
            try {
                nfcAdapter?.enableReaderMode(
                    activity,
                    { tag ->
                        val nfcId = tag.id.joinToString(":") { byte -> String.format("%02X", byte) }
                        nfcScanResult = nfcId
                    },
                    NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B,
                    null
                )
            } catch (e: Exception) {
                // Error handling
            }
        } else {
            try {
                nfcAdapter?.disableReaderMode(activity)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    // Check for NFC scan result
    LaunchedEffect(nfcScanResult) {
        if (nfcScanResult != null) {
            nfcId = nfcScanResult!!
            nfcScanningEnabled = false
            nfcScanResult = null
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            if (nfcScanningEnabled && nfcAdapter != null) {
                try {
                    nfcAdapter?.disableReaderMode(activity)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Редактировать чекпоинт" else "Создать чекпоинт") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    TextButton(onClick = { saveCheckpoint() }) {
                        Text("Сохранить")
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
            if (showSaveDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { },
                    title = { Text("Чекпоинт сохранен") },
                    text = {
                        Text("Чекпоинт $lastSavedId успешно сохранен.")
                    },
                    confirmButton = {
                        androidx.compose.material3.Button(
                            onClick = {
                                showSaveDialog = false
                                idNumberField = ""
                                nameField = ""
                                actionField = CheckpointAction.CHECKPOINT
                                questionText = ""
                                inputTitle = ""
                                imageUri = ""
                                nfcId = ""
                                answers = emptyList()
                                android.widget.Toast.makeText(context, "Форма очищена.", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Добавить еще один")
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                showSaveDialog = false
                                onBack()
                            }
                        ) {
                            Text("Выйти")
                        }
                    }
                )
            }
            
            if (imageUri.isNotEmpty()) {
                androidx.compose.material3.Text(
                    text = "Текущий URI: $imageUri",
                    fontSize = 12.sp,
                    color = androidx.compose.ui.graphics.Color.Green,
                    modifier = Modifier.padding(8.dp)
                )
            }
            
            OutlinedTextField(
                value = "CP-$idNumberField",
                onValueChange = { 
                    val input = it.replace("CP-", "")
                    val numericValue = input.filter { it.isDigit() }
                    idNumberField = numericValue
                },
                label = { Text("ID чекпоинта (CP-XXX)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            OutlinedTextField(
                value = nameField,
                onValueChange = { nameField = it },
                label = { Text("Имя чекпоинта") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Text("Тип действия:", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            
            var expanded by remember { mutableStateOf(false) }
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = actionField.name.lowercase().replace('_', ' '),
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Выберите тип") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                )
                
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CheckpointAction.values().forEach { action ->
                        DropdownMenuItem(
                            text = { Text(action.name.lowercase().replace('_', ' ')) },
                            onClick = {
                                actionField = action
                                expanded = false
                            }
                        )
                    }
                }
            }
            
            if (actionField == CheckpointAction.QUESTION) {
                OutlinedTextField(
                    value = questionText,
                    onValueChange = { questionText = it },
                    label = { Text("Текст вопроса") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text("Ответы:", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                answers.forEachIndexed { index, answer ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = answer,
                            onValueChange = { newAnswer ->
                                answers = answers.toMutableList().apply { this[index] = newAnswer }
                            },
                            label = { Text("Ответ ${index + 1}") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        if (answers.size > 1) {
                            IconButton(
                                onClick = { answers = answers.toMutableList().apply { removeAt(index) } }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить ответ")
                            }
                        }
                    }
                }
                
                IconButton(onClick = { answers = answers + "" }) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить ответ")
                }
            }
            
            if (actionField == CheckpointAction.INPUT) {
                OutlinedTextField(
                    value = inputTitle,
                    onValueChange = { inputTitle = it },
                    label = { Text("Текст запроса (название поля)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Text("Картинка прибора:", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (imageUri.isNotEmpty() && bitmapState.value != null) {
                    Image(
                        bitmap = bitmapState.value!!.asImageBitmap(),
                        contentDescription = "Предпросмотр картинки",
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .clip(RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Нет картинки",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Выбрать картинку")
                    }
                    if (imageUri.isNotEmpty()) {
                        TextButton(
                            onClick = { imageUri = "" },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Удалить картинку", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            
            // NFC-ID with scanner button
            OutlinedTextField(
                value = nfcId,
                onValueChange = { nfcId = it },
                label = { Text("NFC-ID (для чекпоинтов без QR)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Опционально: ID NFC-тега") }
            )
            
            if (nfcScanningEnabled) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Сканирование NFC...", fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Приложите NFC-тег к устройству", fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            } else {
                Button(
                    onClick = { nfcScanningEnabled = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Сканировать NFC-тег")
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
