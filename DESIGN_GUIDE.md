# Работа с дизайном в Android Studio

## Как открыть Preview-окно

1. **Откройте файл с Composable-функцией** (например, `PhotoCaptureScreen.kt` или `Marshruti.kt`)

2. **Найдите кнопку "Preview"** справа в верхней части редактора кода (или слева от номеров строк)

3. **Кликните на кнопку Preview** - откроется новая вкладка с предпросмотром

## Что вы можете делать в Preview:

### 1. **Просмотр интерфейса**
- Увидеть, как выглядит экран на разных устройствах
- Проверить расположение элементов
- Проверить отступы и размеры

### 2. **Изменение устройства**
В Preview-окне сверху есть выпадающий список с выбором устройства:
- Phone (Pixel系列)
- Tablet
- Foldable
- custom (свой размер)

### 3. **Изменение темы**
В меню Preview есть переключатель темы:
- Light (светлая тема)
- Dark (темная тема)
- System (системная тема)

### 4. **Изменение языка**
- В Preview можно переключить язык интерфейса

### 5. **Поворот экрана**
- Кнопка поворота экрана (иконка 📱��)

### 6. **Изменение размера шрифта**
- Масштабирование текста (от 80% до 150%)

## Как добавить новый Preview:

### Вариант 1: Создать отдельную функцию Preview

```kotlin
@Composable
@Preview(showBackground = true, name = "Название превью")
@Composable
fun MyScreenPreview() {
    MyScreen()
}
```

### Вариант 2: Добавить параметры в существующий Preview

```kotlin
@Composable
@Preview(
    showBackground = true, 
    name = "Preview с фоном",
    backgroundColor = 0xffffff00
)
@Preview(
    showBackground = true, 
    name = "Preview без фона"
)
@Composable
fun MyScreenPreview() {
    MyScreen()
}
```

## Типичные настройки Preview:

```kotlin
@Preview(
    showBackground = true,              // Показывать фон
    name = "Название экрана",           // Имя в списке preview
    backgroundColor = 0xff000000,       // Цвет фона (hex)
    uiMode = Configuration.UI_MODE_NIGHT_YES,  // Темная тема
    locale = "ru"                       // Русский язык
)
```

## Советы по дизайну:

### 1. **Используйте Material Design 3**
- Следуйте официальным рекомендациям Material Design
- Используйте цветовую схему из `MaterialTheme.colorScheme`

### 2. **Отступы**
- Используйте `Padding` и `Spacer` для создания пространства
- Стандартные отступы: 8.dp, 16.dp, 24.dp

### 3. **Размеры текста**
```
DisplayLarge: 57.sp
DisplayMedium: 45.sp
DisplaySmall: 36.sp
HeadlineLarge: 32.sp
HeadlineMedium: 28.sp
HeadlineSmall: 24.sp
TitleLarge: 22.sp
TitleMedium: 16.sp
TitleSmall: 14.sp
BodyLarge: 16.sp
BodyMedium: 14.sp
BodySmall: 12.sp
LabelLarge: 14.sp
LabelMedium: 12.sp
LabelSmall: 11.sp
```

### 4. **Удобство касания**
- Кнопки должны быть не меньше 48x48 dp
- Между кнопками минимум 8.dp

### 5. **Адаптивный дизайн**
- Используйте `fillMaxWidth()` и `weight()` для адаптации под разные размеры
- Тестируйте на разных устройствах

## Важно: Preview с внешними зависимостями

Preview **НЕ РАБОТАЕТ** для Composable-функций, которые:
- Зависят от реального `Context` (например, `LocalContext.current`)
- Используют `SharedPrefsManager`, `AlarmScheduler` и другие классы с внешними зависимостями
- Требуют инициализации через `remember` с реальными объектами

**Примеры экранов, где Preview работает:**
- `MarshrutiScreen` - использует `remember` для создания менеджеров

**Примеры экранов, где Preview НЕ работает:**
- `PhotoCaptureScreen` - использует `LocalContext.current` и реальный `SharedPrefsManager`
- `OhrannikCabinetScreen` - использует реальный контекст и менеджеры
- Любые экраны с камерой, CameraX и реальными системными сервисами

## Рабочий процесс:

1. **Измените код** в Composable-функции
2. **Сохраните файл** (Ctrl+S)
3. **Проверьте Preview** - изменения отобразятся автоматически
4. **ЕслиPreview не обновился** - нажмите "Refresh" в Preview-окне
5. **Протестируйте на устройстве** - соберите и установите приложение

## Распространенные ошибки:

1. **Preview не отображается** - проверьте, что функция помечена `@Composable`
2. **Preview не обновляется** - нажмите "Refresh" или перезапустите Android Studio
3. **Ошибка компиляции в Preview** - проверьте, что все зависимости переданы правильно

## Пример улучшенного Preview:

```kotlin
@Composable
@Preview(
    showBackground = true,
    name = "Photo Capture - Camera Mode",
    uiMode = Configuration.UI_MODE_NIGHT_NO
)
@Preview(
    showBackground = true,
    name = "Photo Capture - Preview Mode",
    uiMode = Configuration.UI_MODE_NIGHT_NO
)
@Composable
fun PhotoCapturePreview() {
    val context = LocalContext.current
    val prefsManager = remember { SharedPrefsManager(context) }
    
    PhotoCaptureScreen(
        checkpointName = "Электросчетчик",
        onPhotoTaken = {},
        onBack = {},
        prefsManager = prefsManager,
        employeeName = "Иванов Иван"
    )
}
```

## 6. Новая функциональность: Замена картинки прибора

### В пункте 4 "Картинки приборов для фото" теперь доступна замена:

1. **Нажмите на иконку "+"** рядом с привязанной картинкой
2. **Откроется диалог** с двумя кнопками:
   - **Заменить** - выберите новую картинку из галереи
   - **Удалить** - удалить текущую картинку

### Как это работает:

```
Текущие привязки:
CP-001 [+] [🗑️]
CP-002 [+] [🗑️]
```

- Кнопка **[+]** (плюс) - открывает диалог замены/удаления
- Кнопка **[🗑️]** (корзина) - удаляет картинку сразу

### Диалог замены теперь показывает текущую картинку!

```
Управление картинкой

[Картинка прибора здесь]

Чекпоинт: CP-001

Картинка привязана. Нажмите 'Заменить', чтобы выбрать новую,
или 'Удалить', чтобы убрать привязку.

[Заменить] [Удалить]
```

При клике на **[+]** показывается диалог с:
- **Предпросмотром текущей картинки** - вы сразу видите, что привязано
- **Текстом описания** - понятно, что можно сделать
- **Кнопками действия** - Заменить или Удалить

## Дополнительные ресурсы:

- [Material Design 3](https://m3.material.io/)
- [Jetpack Compose Documentation](https://developer.android.com/jetpack/compose)
- [Compose API Reference](https://developer.android.com/reference/kotlin/androidx/compose/packages)
