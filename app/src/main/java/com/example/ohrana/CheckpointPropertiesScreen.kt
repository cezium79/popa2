package com.example.ohrana

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckpointPropertiesScreen(
    checkpointList: List<String>,
    onBack: () -> Unit,
    onPropertiesChanged: (Map<String, String?>) -> Unit
) {
    val context = LocalContext.current
    val prefsManager = remember { SharedPrefsManager(context) }
    
    // Выбранный чекпоинт для привязки картинки
    var selectedCheckpointIdForImage by remember { mutableStateOf<String?>(null) }
    
    // Кэшируем текущие свойства чекпоинтов
    val checkpointProperties = remember(checkpointList) {
        checkpointList.associateWith { checkpointId ->
            prefsManager.getCheckpointImageUri(checkpointId)
        }
    }
    
    // Локальный кэш свойств для редактирования
    val localProperties = remember { 
        mutableStateMapOf<String, String?>().apply {
            putAll(checkpointProperties)
        }
    }
    
    // Launcher для выбора изображения
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                val checkpointId = selectedCheckpointIdForImage ?: return@let
                localProperties[checkpointId] = uri.toString()
                prefsManager.saveCheckpointImageUri(checkpointId, uri.toString())
                android.widget.Toast.makeText(context, "Картинка привязана к чекпоинту '$checkpointId'", android.widget.Toast.LENGTH_SHORT).show()
                selectedCheckpointIdForImage = null
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройка чекпоинтов") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Свойства чекпоинтов",
                                fontSize = 18.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Нажмите на чекпоинт, чтобы редактировать его свойства",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                items(checkpointList) { checkpointId ->
                    val currentImageUri = localProperties[checkpointId]
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clickable(onClick = {
                                selectedCheckpointIdForImage = checkpointId
                                imagePickerLauncher.launch("image/*")
                            }),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Чекпоинт: $checkpointId",
                                    fontSize = 16.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                )
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Удалить картинку",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            if (currentImageUri != null) {
                                val uri = android.net.Uri.parse(currentImageUri)
                                // Используем LaunchedEffect для загрузки bitmap при изменении URI
                                val bitmapState = remember(uri) {
                                    androidx.compose.runtime.mutableStateOf<android.graphics.Bitmap?>(null)
                                }
                                LaunchedEffect(uri) {
                                    bitmapState.value = runCatching {
                                        android.graphics.BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri))
                                    }.getOrNull()
                                }
                                
                                if (bitmapState.value != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = bitmapState.value!!.asImageBitmap(),
                                        contentDescription = "Картинка прибора",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(100.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Картинка привязана",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Text(
                                        text = "[Загрузка картинки...]",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Text(
                                    text = "Нет привязанной картинки. Нажмите, чтобы добавить.",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            // Сохраняем все изменения
                            val allUris = localProperties.filter { it.value != null }.toMap()
                            onPropertiesChanged(allUris)
                            onBack()
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text("Сохранить изменения")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
