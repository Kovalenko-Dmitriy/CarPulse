plugins {
    // Плагин Android Gradle (AGP) для сборки приложения
    id("com.android.application") version "9.3.3" apply false

    // Обязательный плагин для работы Jetpack Compose (начиная с Kotlin 2.0)
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false

    // KSP — нужен для Room (генерирует код базы данных)
    id("com.google.devtools.ksp") version "2.3.11" apply false
}